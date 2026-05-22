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

final class WifiChangeMonitor {
    private static final long DEBOUNCE_MILLIS = 1800L;
    private static final long CAPABILITY_CHECK_MIN_INTERVAL_MILLIS = 60_000L;

    private final Context context;
    private final String owner;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final AtomicBoolean checkQueued = new AtomicBoolean(false);
    private final AtomicBoolean forceDisconnected = new AtomicBoolean(false);
    private final AtomicLong lastCapabilityCheckAt = new AtomicLong(0L);
    private final AtomicReference<String> queuedReason = new AtomicReference<>();

    private ExecutorService executor;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback defaultCallback;
    private ConnectivityManager.NetworkCallback wifiCallback;

    WifiChangeMonitor(Context context, String owner) {
        this.context = context.getApplicationContext();
        this.owner = owner;
        this.connectivityManager = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    void start() {
        if (!Config.get(context).isTrackingEnabled() || !Config.get(context).postOnWifiChange()) {
            stop();
            return;
        }
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        if (connectivityManager == null) {
            registered.set(false);
            LogStore.append(context, "network", owner + " monitor unavailable; ConnectivityManager missing");
            return;
        }

        executor = Executors.newSingleThreadExecutor();
        NetworkStateStore.seedIfMissing(context, WifiInfoReader.read(context), owner + "-monitor-start");
        defaultCallback = createCallback("default");
        wifiCallback = createCallback("wifi");

        try {
            connectivityManager.registerDefaultNetworkCallback(defaultCallback);
            NetworkRequest wifiRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback);
            LogStore.append(context, "network", owner + " network callbacks registered");
        } catch (RuntimeException e) {
            registered.set(false);
            shutdownExecutor();
            LogStore.append(context, "network", owner + " network callback registration failed: "
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
        forceDisconnected.set(false);
        lastCapabilityCheckAt.set(0L);
        queuedReason.set(null);
        shutdownExecutor();
    }

    private ConnectivityManager.NetworkCallback createCallback(final String source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                @Override
                public void onAvailable(Network network) {
                    queueCheck(source + "-available", false);
                }

                @Override
                public void onLost(Network network) {
                    queueCheck(source + "-lost", "wifi".equals(source));
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    queueCapabilityCheck(source + "-capabilities");
                }
            };
        }
        return new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                queueCheck(source + "-available", false);
            }

            @Override
            public void onLost(Network network) {
                queueCheck(source + "-lost", "wifi".equals(source));
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
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
        queueCheck(reason, false);
    }

    private void queueCheck(final String reason, boolean disconnected) {
        queuedReason.set(reason);
        forceDisconnected.set(disconnected);
        if (!checkQueued.compareAndSet(false, true)) {
            return;
        }
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null || currentExecutor.isShutdown()) {
            checkQueued.set(false);
            return;
        }
        currentExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SystemClock.sleep(DEBOUNCE_MILLIS);
                    String finalReason = queuedReason.get();
                    boolean finalForceDisconnected = forceDisconnected.getAndSet(false);
                    handleCheck(finalReason == null ? reason : finalReason, finalForceDisconnected);
                } finally {
                    checkQueued.set(false);
                }
            }
        });
    }

    private void handleCheck(String reason, boolean forcedDisconnected) {
        if (!Config.get(context).isTrackingEnabled() || !Config.get(context).postOnWifiChange()) {
            return;
        }
        if (forcedDisconnected && (reason == null || !reason.contains("wifi-lost"))) {
            reason = "wifi-lost:" + reason;
        }
        WifiSnapshot wifi = forcedDisconnected
                ? WifiSnapshot.disconnected("forced disconnected state for " + reason)
                : WifiInfoReader.read(context);
        if (!NetworkStateStore.updateAndCheckChanged(context, wifi, owner + ":" + reason)) {
            return;
        }
        LogStore.append(context, "network", "Wi-Fi changed; requesting GPS post owner=" + owner
                + " reason=" + reason + " ssid=" + wifi.displaySsid + " bssid=" + wifi.displayBssid);
        try {
            SystemTaskService.startTask(context, TaskIds.GPS_POST, "wifi-change:" + owner + ":" + reason, false);
        } catch (RuntimeException e) {
            LogStore.append(context, "network", "Wi-Fi change post start failed: " + e.getMessage());
            AlarmScheduler.scheduleGpsPostAfter(context, 5000L, "wifi-change-fallback");
        }
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
