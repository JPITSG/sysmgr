package com.jpitsg.sysman;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Watches connection state for {@link VncService} and decides whether the
 * configured availability conditions are satisfied.
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
    private static final int WATCH_TRANSPORTS = 1;
    private static final int WATCH_VPN = 1 << 1;

    interface Listener {
        /**
         * @param transitioned true when the network actually moved, as opposed
         *                     to a callback that resolved to the same state.
         */
        void onConnectionChanged(String reason, boolean transitioned, NetworkSnapshot snapshot);
    }

    /** What the conditions are evaluated against. Read off the main thread only. */
    static final class NetworkSnapshot {
        final WifiSnapshot wifi;
        final boolean onWifi;
        final boolean onCellularOnly;
        final boolean vpnConnected;
        final String signature;

        NetworkSnapshot(WifiSnapshot wifi, boolean onWifi, boolean onCellularOnly,
                        boolean vpnConnected) {
            this.wifi = wifi;
            this.onWifi = onWifi;
            this.onCellularOnly = onCellularOnly;
            this.vpnConnected = vpnConnected;
            this.signature = (onWifi ? "wifi:" : onCellularOnly ? "cellular:" : "other:")
                    + (wifi.ssid.isEmpty()
                    ? (wifi.ssidRedacted ? "<redacted>" : "<none>") : wifi.ssid);
        }
    }

    private static final class TransportSnapshot {
        final boolean wifi;
        final boolean cellular;
        final boolean ethernet;

        TransportSnapshot(boolean wifi, boolean cellular, boolean ethernet) {
            this.wifi = wifi;
            this.cellular = cellular;
            this.ethernet = ethernet;
        }
    }

    /** The conditions' answer, with a reason fit for both the log and the status block. */
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
    private final AtomicBoolean queuedTransition = new AtomicBoolean(false);
    private final AtomicLong lastCapabilityCheckAt = new AtomicLong(0L);
    private final AtomicLong registrationGeneration = new AtomicLong(0L);
    private final AtomicReference<String> queuedReason = new AtomicReference<>();
    private final AtomicReference<String> lastSignature = new AtomicReference<>();
    private final AtomicReference<Boolean> lastVpnConnected = new AtomicReference<>();

    private ExecutorService executor;
    private final ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback defaultCallback;
    private ConnectivityManager.NetworkCallback transportCallback;
    private BroadcastReceiver vpnStateReceiver;
    private volatile int registeredMask;

    VncNetworkWatcher(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.connectivityManager =
                (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    // ---- Condition evaluation ----------------------------------------------

    /** True when at least one availability condition is selected. */
    static boolean rulesNeedState(Context context) {
        Config config = Config.get(context);
        return config.vncEnabledOnMatchingWifi()
                || config.vncEnabledWhenVpnConnected()
                || config.vncEnabledOnCellularOnly();
    }

    /**
     * Reads only the state needed by the selected conditions. Resolving an SSID
     * can block for over a second inside {@link WifiInfoReader}, so it is skipped
     * for VPN-only and cellular-only policies and this method must still remain
     * off the main thread.
     */
    static NetworkSnapshot readNetwork(Context context) {
        Config config = Config.get(context);
        boolean needsWifiIdentity = config.vncEnabledOnMatchingWifi();
        boolean needsTransports = needsWifiIdentity || config.vncEnabledOnCellularOnly();
        WifiSnapshot wifi = needsWifiIdentity
                ? WifiInfoReader.read(context)
                : WifiSnapshot.disconnected("Wi-Fi identity not needed");
        TransportSnapshot transports = needsTransports
                ? readUnderlyingTransports(context)
                : new TransportSnapshot(false, false, false);
        boolean onWifi = wifi.connected || transports.wifi;
        boolean onCellularOnly = transports.cellular && !onWifi && !transports.ethernet;
        return new NetworkSnapshot(wifi, onWifi, onCellularOnly, isVpnConnected(context));
    }

    /**
     * Applies the availability conditions as an allow-list. With none selected,
     * VNC runs everywhere. With one or more selected, any matching condition is
     * enough to run it.
     */
    static Verdict evaluate(Context context, NetworkSnapshot snapshot) {
        Config config = Config.get(context);
        boolean matchingWifi = config.vncEnabledOnMatchingWifi();
        boolean connectedVpn = config.vncEnabledWhenVpnConnected();
        boolean cellularOnly = config.vncEnabledOnCellularOnly();

        if (!matchingWifi && !connectedVpn && !cellularOnly) {
            return new Verdict(true, "No availability conditions");
        }
        if (snapshot == null) {
            return new Verdict(false, "Connection state unavailable");
        }

        String wifiFailure = null;
        if (matchingWifi) {
            String pattern = config.vncMatchingWifiSsid();
            if (pattern.isEmpty()) {
                wifiFailure = "No Wi-Fi SSID pattern set";
            } else if (!snapshot.onWifi) {
                wifiFailure = "Not on Wi-Fi";
            } else if (snapshot.wifi.ssid.isEmpty()) {
                // Fail closed rather than guess: an unreadable SSID cannot be
                // matched, and starting anyway would ignore the condition.
                wifiFailure = snapshot.wifi.ssidRedacted
                        ? "SSID not visible; check the location permission and that location is on"
                        : "SSID unavailable";
            } else if (PatternMatcher.simpleMatch(pattern, snapshot.wifi.ssid, false)) {
                return new Verdict(true,
                        "Wi-Fi SSID " + snapshot.wifi.ssid + " matches " + pattern);
            } else {
                wifiFailure = "Wi-Fi SSID " + snapshot.wifi.ssid
                        + " does not match " + pattern;
            }
        }

        if (connectedVpn && snapshot.vpnConnected) {
            return new Verdict(true, "VPN connected");
        }
        if (cellularOnly && snapshot.onCellularOnly) {
            return new Verdict(true, "Cellular-only connection active");
        }

        int selectedCount = (matchingWifi ? 1 : 0) + (connectedVpn ? 1 : 0)
                + (cellularOnly ? 1 : 0);
        if (selectedCount == 1) {
            if (matchingWifi) {
                return new Verdict(false, wifiFailure);
            }
            return new Verdict(false, connectedVpn
                    ? "VPN not connected" : "Cellular-only connection not active");
        }

        List<String> conditions = new ArrayList<>();
        if (matchingWifi) {
            conditions.add("matching Wi-Fi");
        }
        if (connectedVpn) {
            conditions.add("a connected VPN");
        }
        if (cellularOnly) {
            conditions.add("a cellular-only connection");
        }
        String reason = "Waiting for " + joinWithOr(conditions);
        if (matchingWifi && "No Wi-Fi SSID pattern set".equals(wifiFailure)) {
            reason += "; no Wi-Fi SSID pattern is set";
        }
        return new Verdict(false, reason);
    }

    /**
     * Reads physical transports without treating a VPN's inherited transport as
     * Wi-Fi or cellular. Validation is not required: a LAN without internet is
     * exactly where this server can still be useful.
     */
    @SuppressWarnings("deprecation")
    private static TransportSnapshot readUnderlyingTransports(Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return new TransportSnapshot(false, false, false);
        }
        boolean wifi = false;
        boolean cellular = false;
        boolean ethernet = false;
        try {
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    continue;
                }
                wifi |= caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                cellular |= caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                ethernet |= caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            }
        } catch (RuntimeException ignored) {
        }
        return new TransportSnapshot(wifi, cellular, ethernet);
    }

    private static boolean isVpnConnected(Context context) {
        return OpenVpnService.isActive()
                && OpenVpnStateStore.STATE_CONNECTED.equals(OpenVpnStateStore.state(context));
    }

    private static String joinWithOr(List<String> values) {
        if (values.size() == 1) {
            return values.get(0);
        }
        if (values.size() == 2) {
            return values.get(0) + " or " + values.get(1);
        }
        return values.get(0) + ", " + values.get(1) + ", or " + values.get(2);
    }

    // ---- Callback registration ----------------------------------------------

    synchronized void start() {
        int wantedMask = watchMask(context);
        if (wantedMask == 0) {
            stop();
            return;
        }
        if (registered.get() && registeredMask == wantedMask) {
            return;
        }
        stop();
        if ((wantedMask & WATCH_TRANSPORTS) != 0 && connectivityManager == null) {
            LogStore.append(context, "vnc",
                    "Availability watcher unavailable; ConnectivityManager missing");
            return;
        }

        executor = Executors.newSingleThreadExecutor();
        try {
            if ((wantedMask & WATCH_TRANSPORTS) != 0) {
                defaultCallback = createCallback("default");
                transportCallback = createCallback("transport");
                connectivityManager.registerDefaultNetworkCallback(defaultCallback);
                // The default may be a VPN, so also watch every physical
                // network. This catches Wi-Fi, cellular and Ethernet changes
                // underneath an otherwise-stable tunnel.
                NetworkRequest transportRequest = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .build();
                connectivityManager.registerNetworkCallback(transportRequest, transportCallback);
            }
            if ((wantedMask & WATCH_VPN) != 0) {
                registerVpnStateReceiver();
            }
            registeredMask = wantedMask;
            registered.set(true);
            LogStore.append(context, "vnc", "Availability watcher registered transports="
                    + ((wantedMask & WATCH_TRANSPORTS) != 0)
                    + " vpn=" + ((wantedMask & WATCH_VPN) != 0));
            // A dynamic receiver has no sticky initial event. Read once after
            // every registration so a VPN transition racing registration, or
            // a transport callback omitted by the platform, cannot strand VNC
            // in a stale state.
            queueCheck("watcher-start");
        } catch (RuntimeException e) {
            // Registration is not atomic. Remove every partial registration or
            // a later retry leaks callbacks and duplicates evaluations.
            stop();
            LogStore.append(context, "vnc", "Availability watcher registration failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    synchronized void stop() {
        // Invalidates callbacks and queued work from the prior registration,
        // including work that shutdownNow cannot interrupt immediately.
        registrationGeneration.incrementAndGet();
        if (connectivityManager != null) {
            unregister(defaultCallback);
            unregister(transportCallback);
        }
        unregisterVpnStateReceiver();
        defaultCallback = null;
        transportCallback = null;
        registered.set(false);
        registeredMask = 0;
        checkQueued.set(false);
        queuedTransition.set(false);
        lastCapabilityCheckAt.set(0L);
        queuedReason.set(null);
        lastSignature.set(null);
        lastVpnConnected.set(null);
        shutdownExecutor();
    }

    /** Seeds transition detection from the service's initial evaluation. */
    void rememberSnapshot(NetworkSnapshot snapshot) {
        if (snapshot != null && registered.get()) {
            lastSignature.compareAndSet(null, snapshot.signature);
        }
    }

    private static int watchMask(Context context) {
        Config config = Config.get(context);
        int mask = 0;
        if (config.vncEnabledOnMatchingWifi() || config.vncEnabledOnCellularOnly()) {
            mask |= WATCH_TRANSPORTS;
        }
        if (config.vncEnabledWhenVpnConnected()) {
            mask |= WATCH_VPN;
        }
        return mask;
    }

    private void registerVpnStateReceiver() {
        lastVpnConnected.set(isVpnConnected(context));
        vpnStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent intent) {
                boolean connected = isVpnConnected(context);
                Boolean previous = lastVpnConnected.getAndSet(connected);
                if (previous != null && previous.booleanValue() == connected) {
                    return;
                }
                queueCheck("vpn-state", previous != null);
            }
        };
        IntentFilter filter = new IntentFilter(OpenVpnStateStore.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(vpnStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(vpnStateReceiver, filter);
        }
    }

    private void unregisterVpnStateReceiver() {
        BroadcastReceiver receiver = vpnStateReceiver;
        vpnStateReceiver = null;
        if (receiver == null) {
            return;
        }
        try {
            context.unregisterReceiver(receiver);
        } catch (RuntimeException ignored) {
        }
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
        queueCheck(reason, false);
    }

    private void queueCheck(final String reason, boolean knownTransition) {
        if (knownTransition) {
            queuedTransition.set(true);
        }
        queuedReason.set(reason);
        if (!checkQueued.compareAndSet(false, true)) {
            return;
        }
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null || currentExecutor.isShutdown()) {
            checkQueued.set(false);
            return;
        }
        final long generation = registrationGeneration.get();
        try {
            currentExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        SystemClock.sleep(DEBOUNCE_MILLIS);
                        if (generation != registrationGeneration.get()) {
                            return;
                        }
                        String finalReason = queuedReason.get();
                        handleCheck(finalReason == null ? reason : finalReason, generation);
                    } finally {
                        if (generation == registrationGeneration.get()) {
                            checkQueued.set(false);
                        }
                    }
                }
            });
        } catch (RuntimeException e) {
            if (generation == registrationGeneration.get()) {
                checkQueued.set(false);
            }
        }
    }

    private void handleCheck(String reason, long generation) {
        if (!registered.get() || generation != registrationGeneration.get()) {
            return;
        }
        NetworkSnapshot snapshot = readNetwork(context);
        if (!registered.get() || generation != registrationGeneration.get()) {
            return;
        }
        String previous = lastSignature.getAndSet(snapshot.signature);
        boolean vpnTransitioned = false;
        if ((registeredMask & WATCH_VPN) != 0) {
            Boolean previousVpn = lastVpnConnected.getAndSet(snapshot.vpnConnected);
            vpnTransitioned = previousVpn != null
                    && previousVpn.booleanValue() != snapshot.vpnConnected;
        }
        boolean transitioned = queuedTransition.getAndSet(false)
                || vpnTransitioned
                || (previous != null && !previous.equals(snapshot.signature));
        listener.onConnectionChanged(reason, transitioned, snapshot);
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
