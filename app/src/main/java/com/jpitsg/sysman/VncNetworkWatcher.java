package com.jpitsg.sysman;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.SystemClock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Watches the network for {@link VncService} and decides whether the auto-enable
 * rules are satisfied.
 *
 * <p>Shaped like {@link WifiChangeMonitor} but deliberately not a reuse of it:
 * that one is hard-gated on the GPS tracking settings and owns the shared
 * {@link NetworkStateStore} signature that drives Wi-Fi-change location posts.
 * Writing to that store from here would quietly change when the GPS logger
 * fires, so this keeps its own signature and touches nothing the tracker owns.
 */
final class VncNetworkWatcher {
    /** Wi-Fi transitions arrive as bursts of callbacks; settle before deciding. */
    private static final long DEBOUNCE_MILLIS = 1800L;
    /** Signal-strength changes fire constantly on a stable network. */
    private static final long CAPABILITY_CHECK_MIN_INTERVAL_MILLIS = 60_000L;

    interface Listener {
        /**
         * @param transitioned true when the network actually moved, as opposed
         *                     to a callback that resolved to the same state.
         */
        void onNetworkChanged(String reason, boolean transitioned, NetworkSnapshot snapshot);
    }

    /** What the rules are evaluated against. Read off the main thread only. */
    static final class NetworkSnapshot {
        final WifiSnapshot wifi;
        final boolean onWifi;
        final String signature;

        NetworkSnapshot(WifiSnapshot wifi, boolean onWifi) {
            this.wifi = wifi;
            this.onWifi = onWifi;
            this.signature = (onWifi ? "wifi:" : "off:")
                    + (wifi.ssid.isEmpty() ? (wifi.ssidRedacted ? "<redacted>" : "<none>") : wifi.ssid);
        }
    }

    /** The rules' answer, with a reason fit for both the log and the status block. */
    static final class Verdict {
        final boolean shouldRun;
        final String reason;

        private Verdict(boolean shouldRun, String reason) {
            this.shouldRun = shouldRun;
            this.reason = reason;
        }
    }

    private final Context context;
    private final Listener listener;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final AtomicBoolean checkQueued = new AtomicBoolean(false);
    private final AtomicLong lastCapabilityCheckAt = new AtomicLong(0L);
    private final AtomicReference<String> queuedReason = new AtomicReference<>();
    private final AtomicReference<String> lastSignature = new AtomicReference<>();

    private ExecutorService executor;
    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback defaultCallback;
    private ConnectivityManager.NetworkCallback wifiCallback;

    VncNetworkWatcher(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.connectivityManager =
                (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    // ---- Rule evaluation ----------------------------------------------------

    /** True when any rule needs to know about the network at all. */
    static boolean rulesNeedNetwork(Context context) {
        Config config = Config.get(context);
        return config.vncAutoWifiEnabled() || config.vncStopOnCellular();
    }

    /**
     * Reads the current network. Can block for over a second inside {@link
     * WifiInfoReader}, so it must never be called on the main thread.
     */
    static NetworkSnapshot readNetwork(Context context) {
        WifiSnapshot wifi = WifiInfoReader.read(context);
        return new NetworkSnapshot(wifi, wifi.connected || hasWifiTransport(context));
    }

    /**
     * Applies the auto-enable rules. A null snapshot means no rule needed the
     * network, which is only the case when neither rule is switched on.
     */
    static Verdict evaluate(Context context, NetworkSnapshot snapshot) {
        Config config = Config.get(context);
        boolean autoWifi = config.vncAutoWifiEnabled();
        boolean stopOnCellular = config.vncStopOnCellular();

        if (!autoWifi && !stopOnCellular) {
            return new Verdict(true, "No network rules");
        }
        if (snapshot == null) {
            return new Verdict(false, "Network state unavailable");
        }
        if (!snapshot.onWifi) {
            // The cellular guard is unconditional by design: a connected VPN
            // does not keep the server alive off Wi-Fi.
            return new Verdict(false, "Not on Wi-Fi");
        }
        if (!autoWifi) {
            return new Verdict(true, "On Wi-Fi");
        }

        String pattern = config.vncAutoWifiSsid();
        if (pattern.isEmpty()) {
            return new Verdict(false, "No SSID pattern set");
        }
        if (snapshot.wifi.ssid.isEmpty()) {
            // Fail closed rather than guess: an unreadable SSID cannot be
            // matched, and starting anyway would ignore the rule entirely.
            return new Verdict(false, snapshot.wifi.ssidRedacted
                    ? "SSID not visible; check the location permission and that location is on"
                    : "SSID unavailable");
        }
        if (!PatternMatcher.simpleMatch(pattern, snapshot.wifi.ssid, false)) {
            return new Verdict(false, "SSID " + snapshot.wifi.ssid + " does not match " + pattern);
        }
        return new Verdict(true, "SSID " + snapshot.wifi.ssid + " matches " + pattern);
    }

    /**
     * True when a non-VPN Wi-Fi network exists. Deliberately not a look at the
     * default network: with the tunnel up the default is the VPN, and a VPN
     * inherits its underlying transports, so a tunnel over cellular would
     * otherwise read as Wi-Fi. Validation is not required either — a LAN with
     * no internet is exactly where this server is useful.
     */
    @SuppressWarnings("deprecation")
    private static boolean hasWifiTransport(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        try {
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    continue;
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    // ---- Callback registration ----------------------------------------------

    void start() {
        if (!rulesNeedNetwork(context)) {
            stop();
            return;
        }
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        if (connectivityManager == null) {
            registered.set(false);
            LogStore.append(context, "vnc", "Network watcher unavailable; ConnectivityManager missing");
            return;
        }

        executor = Executors.newSingleThreadExecutor();
        defaultCallback = createCallback("default");
        wifiCallback = createCallback("wifi");
        try {
            connectivityManager.registerDefaultNetworkCallback(defaultCallback);
            NetworkRequest wifiRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback);
            LogStore.append(context, "vnc", "Network watcher registered");
        } catch (RuntimeException e) {
            // Registration is not atomic: the default callback may already be
            // live when registering the Wi-Fi callback fails. Remove both or a
            // later retry leaks the first callback and duplicates evaluations.
            unregister(defaultCallback);
            unregister(wifiCallback);
            defaultCallback = null;
            wifiCallback = null;
            registered.set(false);
            shutdownExecutor();
            LogStore.append(context, "vnc", "Network watcher registration failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    void stop() {
        if (connectivityManager != null) {
            unregister(defaultCallback);
            unregister(wifiCallback);
        }
        defaultCallback = null;
        wifiCallback = null;
        registered.set(false);
        checkQueued.set(false);
        lastCapabilityCheckAt.set(0L);
        queuedReason.set(null);
        lastSignature.set(null);
        shutdownExecutor();
    }

    boolean isRunning() {
        return registered.get();
    }

    private ConnectivityManager.NetworkCallback createCallback(final String source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new ConnectivityManager.NetworkCallback(
                    ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                @Override
                public void onAvailable(Network network) {
                    queueCheck(source + "-available");
                }

                @Override
                public void onLost(Network network) {
                    queueCheck(source + "-lost");
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    queueCapabilityCheck(source + "-capabilities");
                }
            };
        }
        return new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                queueCheck(source + "-available");
            }

            @Override
            public void onLost(Network network) {
                queueCheck(source + "-lost");
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                queueCapabilityCheck(source + "-capabilities");
            }
        };
    }

    private void queueCapabilityCheck(String reason) {
        if (checkQueued.get()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long previous = lastCapabilityCheckAt.get();
        if (previous > 0L && now - previous < CAPABILITY_CHECK_MIN_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCapabilityCheckAt.compareAndSet(previous, now)) {
            return;
        }
        queueCheck(reason);
    }

    private void queueCheck(final String reason) {
        queuedReason.set(reason);
        if (!checkQueued.compareAndSet(false, true)) {
            return;
        }
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null || currentExecutor.isShutdown()) {
            checkQueued.set(false);
            return;
        }
        try {
            currentExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        SystemClock.sleep(DEBOUNCE_MILLIS);
                        String finalReason = queuedReason.get();
                        handleCheck(finalReason == null ? reason : finalReason);
                    } finally {
                        checkQueued.set(false);
                    }
                }
            });
        } catch (RuntimeException e) {
            checkQueued.set(false);
        }
    }

    private void handleCheck(String reason) {
        if (!registered.get()) {
            return;
        }
        NetworkSnapshot snapshot = readNetwork(context);
        String previous = lastSignature.getAndSet(snapshot.signature);
        boolean transitioned = previous != null && !previous.equals(snapshot.signature);
        listener.onNetworkChanged(reason, transitioned, snapshot);
    }

    private void unregister(ConnectivityManager.NetworkCallback callback) {
        if (callback == null || connectivityManager == null) {
            return;
        }
        try {
            connectivityManager.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
        }
    }

    private void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
