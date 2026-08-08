package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemoteLinkService extends Service {
    private static final String CHANNEL_ID = "system_manager_remote_link";
    private static final int NOTIFICATION_ID = 0x5303;
    private static final long GPS_ACK_TIMEOUT_MILLIS = 10_000L;
    private static final long LIVENESS_PROBE_TIMEOUT_MILLIS = 5_000L;
    // The server acks every heartbeat within ~1s, so silence this long after
    // sending one means the link is blackholed: reconnect without a further
    // probe (the heartbeat was the probe).
    private static final long HEARTBEAT_ACK_TIMEOUT_MILLIS = 8_000L;
    // On wifi a faster beat costs next to nothing and wifi is the link that
    // flaps at the edge of range, so the configured interval is capped there.
    // Cellular keeps the configured cadence: every send holds the radio out of
    // idle for ~10s, and the cellular link doesn't flap at the border anyway.
    private static final long WIFI_HEARTBEAT_MAX_INTERVAL_MILLIS = 15_000L;
    // Below this wifi RSSI the link is marginal enough to be worth verifying
    // before a message needs it; re-probe at most this often while lingering.
    private static final int WIFI_WEAK_RSSI_DBM = -75;
    private static final long WEAK_SIGNAL_PROBE_MIN_INTERVAL_MILLIS = 30_000L;
    // Connections that die younger than this count toward reconnect backoff;
    // longer-lived ones reset it (distinguishes churn from a healthy link dropping).
    private static final long STABLE_CONNECTION_MILLIS = 30_000L;
    // A backed-up notification that isn't acked within this window is resent.
    private static final long BACKUP_RETRY_MILLIS = 60_000L;
    // Cap how many notifications are outstanding (sent, awaiting ack) at once.
    private static final int BACKUP_MAX_IN_FLIGHT = 100;
    // If the platform ever times the foreground service out anyway, come back
    // on this delay rather than the watchdog's, but slowly enough that a
    // still-exhausted runtime budget cannot turn into a restart loop.
    private static final long FGS_TIMEOUT_RESTART_DELAY_MILLIS = 5L * 60_000L;
    private static final long[] BACKOFF_STEP_SECONDS = {1L, 2L, 5L, 10L, 30L};
    private static volatile RemoteLinkService activeService;

    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<String> pendingManualPingReasons = new ConcurrentLinkedQueue<>();
    private final Object gpsAckLock = new Object();
    private final java.util.Map<String, PendingGpsAck> pendingGpsAcks = new java.util.HashMap<>();
    private final AtomicBoolean livenessProbeRequested = new AtomicBoolean(false);
    private volatile String livenessProbeReason = "";
    private volatile boolean reconnectWakeRequested;
    private volatile Network connectedNetwork;
    private volatile boolean linkOnWifi;
    // Touched only on the ConnectivityManager callback thread.
    private long lastWeakSignalProbeAt;
    private volatile boolean defaultValidated = true;
    private volatile boolean stopRequested;
    private volatile RemoteWebSocketClient client;
    private volatile long heartbeatResetAtMillis;
    // Notification Backup: keys sent and awaiting a server ack -> the time they
    // were sent, so we can retry after BACKUP_RETRY_MILLIS. Touched only on the
    // worker thread (drain + ack handling both run in the connected loop).
    private final java.util.Map<String, Long> backupInFlight = new java.util.HashMap<>();
    private final AtomicBoolean backupProbeRequested = new AtomicBoolean(false);
    private volatile String backupProbeReason = "";
    private volatile boolean backupOutboxDirty = true;
    private Thread worker;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    /** Re-resolved before every foreground start, so the toggle applies at once. */
    private String channelId = CHANNEL_ID;

    @Override
    public void onCreate() {
        super.onCreate();
        activeService = this;
        RemoteLinkStateStore.setConnected(this, false);
        ensureNotificationChannel();
        registerNetworkCallback();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Config config = Config.get(this);
        String action = intent == null ? RemoteLinkManager.ACTION_SYNC : intent.getAction();
        String reason = intent == null ? "unknown" : intent.getStringExtra(RemoteLinkManager.EXTRA_REASON);
        if (reason == null) {
            reason = "unknown";
        }

        if (!config.remoteLinkEnabled()) {
            RemoteLinkStateStore.setConnected(this, false);
            LogStore.append(this, "remote", "Remote Link stopping; disabled reason=" + reason);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        // Always foreground, whether or not the notification is on show: the
        // link is a long-lived socket, and a background service would be
        // reclaimed. Hiding is done by the channel, not by giving up the
        // foreground status.
        try {
            startForegroundRemoteLink();
        } catch (RuntimeException e) {
            LogStore.append(this, "remote", "Remote Link foreground start failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (RemoteLinkManager.ACTION_RESTART.equals(action)) {
            LogStore.append(this, "remote", "Remote Link reconnect requested reason=" + reason);
            closeClient();
        }
        // Any sync/nudge (e.g. a freshly queued notification backup) should make
        // the worker re-check the outbox on its next pass.
        backupOutboxDirty = true;
        startWorkerIfNeeded(reason);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Android 15+ times out foreground services of a capped type. Handling this
     * is what keeps the timeout from becoming a crash: ignore it and the
     * platform throws ForegroundServiceDidNotStopInTimeException on the main
     * thread, killing the process and the socket with it. Stop cleanly instead
     * and let the alarm bring the link back.
     */
    @Override
    public void onTimeout(int startId, int fgsType) {
        LogStore.append(this, "remote", "Remote Link foreground service timed out type=" + fgsType
                + "; stopping and retrying in " + (FGS_TIMEOUT_RESTART_DELAY_MILLIS / 60_000L) + "min");
        AlarmScheduler.scheduleRemoteLinkWatchdogAfter(this, FGS_TIMEOUT_RESTART_DELAY_MILLIS, "fgs-timeout");
        stopRequested = true;
        closeClient();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopRequested = true;
        unregisterNetworkCallback();
        RemoteLinkStateStore.setConnected(this, false);
        closeClient();
        if (worker != null) {
            worker.interrupt();
        }
        removeForegroundNotification();
        if (activeService == this) {
            activeService = null;
        }
        super.onDestroy();
    }

    /** False after a process death took the service down without restarting it. */
    static boolean isRunning() {
        return activeService != null;
    }

    static boolean sendPingIfRunning(Context context, String reason) {
        RemoteLinkService service = activeService;
        if (service == null) {
            return false;
        }
        return service.queueManualPing(reason == null ? "unknown" : reason);
    }

    static boolean sendGpsIfRunning(Context context, JSONObject payload) {
        RemoteLinkService service = activeService;
        if (service == null) {
            LogStore.append(context, "remote", "Remote Link unavailable for GPS payload");
            return false;
        }
        return service.sendGpsPayload(payload);
    }

    static boolean sendBackupProbeIfRunning(Context context, String reason) {
        RemoteLinkService service = activeService;
        if (service == null) {
            return false;
        }
        service.backupProbeReason = reason == null ? "unknown" : reason;
        service.backupProbeRequested.set(true);
        service.backupOutboxDirty = true;
        return true;
    }

    private void startWorkerIfNeeded(final String reason) {
        if (!workerRunning.compareAndSet(false, true)) {
            return;
        }
        stopRequested = false;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runRemoteLinkLoop(reason);
            }
        }, "SystemManagerRemoteLink");
        worker.start();
    }

    private void runRemoteLinkLoop(String initialReason) {
        int consecutiveFailures = 0;
        LogStore.append(this, "remote", "Remote Link worker started reason=" + initialReason);
        try {
            while (!stopRequested && Config.get(this).remoteLinkEnabled()) {
                Config config = Config.get(this);
                RemoteWebSocketClient current = new RemoteWebSocketClient(
                        config.remoteLinkEndpoint(),
                        config.remoteLinkUsername(),
                        config.remoteLinkPassword(),
                        config.remoteLinkAcceptAnySslCert());
                client = current;
                boolean connected = false;
                long connectedAtMillis = 0L;
                try {
                    NotificationBackupStateStore.setChecking(this);
                    LogStore.append(this, "remote", "Connecting to " + config.remoteLinkEndpoint());
                    current.connect();
                    connected = true;
                    connectedAtMillis = SystemClock.elapsedRealtime();
                    connectedNetwork = currentDefaultNetwork();
                    linkOnWifi = isWifiNetwork(connectedNetwork);
                    RemoteLinkStateStore.setConnected(this, true);
                    LogStore.append(this, "remote", "Remote Link connected");
                    backupInFlight.clear();
                    backupOutboxDirty = true;
                    sendHello(current, config);
                    runConnectedLoop(current);
                    RemoteLinkStateStore.setConnected(this, false);
                    LogStore.append(this, "remote", "Remote Link disconnected");
                    consecutiveFailures = 0;
                } catch (Exception e) {
                    RemoteLinkStateStore.setConnected(this, false);
                    long delayMillis;
                    if (connected) {
                        long lifetimeMillis = SystemClock.elapsedRealtime() - connectedAtMillis;
                        consecutiveFailures = lifetimeMillis >= STABLE_CONNECTION_MILLIS
                                ? 0
                                : consecutiveFailures + 1;
                        delayMillis = backoffDelayMillis(consecutiveFailures);
                        LogStore.append(this, "remote", "Remote Link lost after "
                                + (lifetimeMillis / 1000L) + "s; retry in " + (delayMillis / 1000L) + "s: "
                                + e.getClass().getSimpleName() + ": " + e.getMessage());
                    } else {
                        consecutiveFailures++;
                        delayMillis = backoffDelayMillis(consecutiveFailures);
                        LogStore.append(this, "remote", "Remote Link connect failed; retry in "
                                + (delayMillis / 1000L) + "s: "
                                + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                    if (sleepUnlessWoken(delayMillis)) {
                        consecutiveFailures = 0;
                        LogStore.append(this, "remote", "Remote Link retry resumed early by network change");
                    }
                } finally {
                    current.close();
                    connectedNetwork = null;
                    linkOnWifi = false;
                    if (client == current) {
                        client = null;
                    }
                }
            }
        } finally {
            RemoteLinkStateStore.setConnected(this, false);
            workerRunning.set(false);
            if (!stopRequested && Config.get(this).remoteLinkEnabled()) {
                LogStore.append(this, "remote", "Remote Link worker exited unexpectedly; restarting service");
                RemoteLinkManager.sync(this, "worker-exit");
            } else {
                LogStore.append(this, "remote", "Remote Link worker stopped");
                stopSelf();
            }
        }
    }

    private void runConnectedLoop(RemoteWebSocketClient current) throws IOException {
        long nextHeartbeatAt = 0L;
        long lastInboundAt = current.lastInboundAtMillis();
        long heartbeatAckDeadlineAt = 0L;
        long lastHeartbeatResetAt = heartbeatResetAtMillis;
        long probeSentAt = 0L;
        long probeDeadlineAt = 0L;
        livenessProbeRequested.set(false);
        while (!stopRequested && Config.get(this).remoteLinkEnabled() && current.isOpen()) {
            Config config = Config.get(this);
            sendQueuedPings(current);
            sendBackupProbeIfRequested(current);
            String message = current.readTextFrame();
            long inboundAt = current.lastInboundAtMillis();
            if (inboundAt > lastInboundAt) {
                lastInboundAt = inboundAt;
                heartbeatAckDeadlineAt = 0L;
                nextHeartbeatAt = inboundAt + heartbeatDelayMillis(config);
                LogStore.append(this, "remote", "Heartbeat timer reset by inbound traffic next_in="
                        + config.remoteLinkHeartbeatSeconds() + "s");
            }
            if (message != null) {
                if (!handleServiceMessage(message)) {
                    RemoteEventHandler.handle(this, current, message);
                }
            }
            drainBackupOutbox(current);
            if (livenessProbeRequested.compareAndSet(true, false)) {
                sendManualPing(current, "liveness:" + livenessProbeReason);
                probeSentAt = SystemClock.elapsedRealtime();
                probeDeadlineAt = probeSentAt + LIVENESS_PROBE_TIMEOUT_MILLIS;
            }
            if (probeDeadlineAt > 0L) {
                if (current.lastInboundAtMillis() >= probeSentAt) {
                    probeDeadlineAt = 0L;
                    LogStore.append(this, "remote", "Liveness probe satisfied by inbound traffic");
                } else if (SystemClock.elapsedRealtime() >= probeDeadlineAt) {
                    LogStore.append(this, "remote", "Liveness probe timed out after "
                            + (LIVENESS_PROBE_TIMEOUT_MILLIS / 1000L) + "s; forcing reconnect");
                    throw new IOException("liveness probe timeout");
                }
            }
            long heartbeatResetAt = heartbeatResetAtMillis;
            if (heartbeatResetAt > lastHeartbeatResetAt) {
                lastHeartbeatResetAt = heartbeatResetAt;
                nextHeartbeatAt = heartbeatResetAt + heartbeatDelayMillis(config);
                LogStore.append(this, "remote", "Heartbeat timer reset by outbound traffic next_in="
                        + config.remoteLinkHeartbeatSeconds() + "s");
            }
            long now = SystemClock.elapsedRealtime();
            if (now >= nextHeartbeatAt) {
                sendHeartbeat(current);
                nextHeartbeatAt = now + heartbeatDelayMillis(config);
                if (heartbeatAckDeadlineAt == 0L) {
                    heartbeatAckDeadlineAt = now + HEARTBEAT_ACK_TIMEOUT_MILLIS;
                }
            }
            if (heartbeatAckDeadlineAt > 0L && now >= heartbeatAckDeadlineAt) {
                LogStore.append(this, "remote", "Heartbeat unanswered for "
                        + (HEARTBEAT_ACK_TIMEOUT_MILLIS / 1000L) + "s; forcing reconnect");
                throw new IOException("heartbeat ack timeout");
            }
        }
    }

    private boolean handleServiceMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");
            if ("gps_ack".equals(type)) {
                handleGpsAck(json);
                return true;
            }
            if ("notif_backup_ack".equals(type)) {
                handleNotifBackupAck(json);
                return true;
            }
            if ("notif_backup_status".equals(type)) {
                boolean enabled = json.optBoolean("enabled", false);
                NotificationBackupStateStore.setServerAvailable(this, enabled);
                // Re-check the outbox on the next pass now that we know the state.
                backupOutboxDirty = true;
                LogStore.append(this, "remote", "Notification backup status from server enabled=" + enabled);
                return true;
            }
            if ("hello_ack".equals(type) || "heartbeat_ack".equals(type)) {
                if (json.has("notif_backup")) {
                    NotificationBackupStateStore.setServerAvailable(this, json.optBoolean("notif_backup", false));
                }
                // Server signalled liveness; re-check the outbox (covers a server that
                // re-enables the store mid-session even if a queue nudge was missed).
                backupOutboxDirty = true;
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void handleGpsAck(JSONObject json) {
        String id = json.optString("id", "");
        if (id.isEmpty()) {
            LogStore.append(this, "remote", "GPS ack ignored; missing id");
            return;
        }
        synchronized (gpsAckLock) {
            PendingGpsAck pending = pendingGpsAcks.remove(id);
            if (pending == null) {
                LogStore.append(this, "remote", "GPS ack ignored id=" + id);
                return;
            }
            pending.complete = true;
            pending.ok = json.optBoolean("ok", true);
            pending.reason = json.optString("reason", "");
            gpsAckLock.notifyAll();
            LogStore.append(this, "remote", "GPS ack received id=" + id
                    + " ok=" + pending.ok + " reason=" + pending.reason);
        }
    }

    private void handleNotifBackupAck(JSONObject json) {
        String key = json.optString("key", "");
        if (key.isEmpty()) {
            return;
        }
        boolean ok = json.optBoolean("ok", false);
        String reason = json.optString("reason", "");
        if (ok) {
            backupInFlight.remove(key);
            NotificationBackupStore.remove(this, key);
            NotificationBackupStateStore.setServerAvailable(this, true);
        } else if ("store-not-configured".equals(reason)) {
            // Server has Notification Backup turned off. Keep the record queued and
            // stop draining until the server reports it available again.
            backupInFlight.remove(key);
            NotificationBackupStateStore.setServerAvailable(this, false);
            LogStore.append(this, "remote", "Notification backup rejected; server store not configured");
        } else {
            // Transient failure: leave it in-flight so the drain retries after the window.
            LogStore.append(this, "remote", "Notification backup not acked key=" + key + " reason=" + reason);
        }
    }

    private void sendBackupProbeIfRequested(RemoteWebSocketClient current) {
        if (!backupProbeRequested.compareAndSet(true, false)) {
            return;
        }
        try {
            JSONObject probe = new JSONObject();
            probe.put("type", "notif_backup_probe");
            probe.put("id", "android-" + UUID.randomUUID().toString());
            probe.put("ts", System.currentTimeMillis() / 1000L);
            current.sendText(probe.toString());
            LogStore.append(this, "remote", "Notification backup probe sent reason=" + backupProbeReason);
        } catch (Exception e) {
            LogStore.append(this, "remote", "Notification backup probe failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            current.close();
        }
    }

    private void drainBackupOutbox(RemoteWebSocketClient current) {
        Config config = Config.get(this);
        if (!config.notificationBackupEnabled()) {
            return;
        }
        if (!NotificationBackupStateStore.isServerAvailable(this)) {
            // Server backup is off; keep queuing locally (the outbox is capped) and
            // wait for a heartbeat_ack / probe to report it available again.
            return;
        }
        if (!backupOutboxDirty && backupInFlight.isEmpty()) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        Iterator<Map.Entry<String, Long>> inFlight = backupInFlight.entrySet().iterator();
        while (inFlight.hasNext()) {
            Map.Entry<String, Long> entry = inFlight.next();
            if (now - entry.getValue() >= BACKUP_RETRY_MILLIS) {
                inFlight.remove();
            }
        }

        List<JSONObject> pending = NotificationBackupStore.peek(this, 0);
        if (pending.isEmpty()) {
            backupInFlight.clear();
            backupOutboxDirty = false;
            return;
        }

        String uuid = config.installUuid();
        int sent = 0;
        for (JSONObject record : pending) {
            if (backupInFlight.size() >= BACKUP_MAX_IN_FLIGHT) {
                break;
            }
            String key = NotificationBackup.keyOf(record);
            if (key.isEmpty() || backupInFlight.containsKey(key)) {
                continue;
            }
            try {
                JSONObject payload = new JSONObject(record.toString());
                payload.put("type", NotificationBackup.MESSAGE_TYPE);
                payload.put("uuid", uuid);
                current.sendText(payload.toString());
                backupInFlight.put(key, now);
                heartbeatResetAtMillis = now;
                sent++;
            } catch (Exception e) {
                LogStore.append(this, "remote", "Notification backup send failed: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                current.close();
                return;
            }
        }
        // Stay dirty while records remain outstanding; the empty branch above
        // flips this off once the outbox is fully acked and drained.
        backupOutboxDirty = true;
        if (sent > 0) {
            LogStore.append(this, "remote", "Notification backup sent count=" + sent + " outbox=" + pending.size());
        }
    }

    private long heartbeatDelayMillis(Config config) {
        long configured = Math.max(1L, config.remoteLinkHeartbeatSeconds()) * 1000L;
        if (linkOnWifi) {
            return Math.min(configured, WIFI_HEARTBEAT_MAX_INTERVAL_MILLIS);
        }
        return configured;
    }

    private void sendHello(RemoteWebSocketClient current, Config config) throws IOException {
        current.sendText("{\"type\":\"hello\",\"app\":\"SystemManager\",\"version\":\"1.0\","
                + "\"uuid\":\"" + config.installUuid() + "\","
                + "\"heartbeat_seconds\":" + config.remoteLinkHeartbeatSeconds() + "}");
    }

    private void sendHeartbeat(RemoteWebSocketClient current) throws IOException {
        int battery = BatteryReader.batteryPercent(this);
        String vpnState = OpenVpnStateStore.state(this);
        boolean vpnServiceActive = OpenVpnService.isActive();
        boolean vpnEnabled = vpnServiceActive && OpenVpnStateStore.isLiveState(vpnState);
        boolean vpnConnected = vpnServiceActive && OpenVpnStateStore.STATE_CONNECTED.equals(vpnState);
        boolean vncEnabled = Config.get(this).vncEnabled();
        boolean vncConnected = VncManager.isConnected(this);
        current.sendText("{\"type\":\"heartbeat\",\"ts\":" + (System.currentTimeMillis() / 1000L)
                + ",\"battery\":" + battery
                + ",\"vpn_enabled\":" + vpnEnabled
                + ",\"vpn_connected\":" + vpnConnected
                + ",\"vnc_enabled\":" + vncEnabled
                + ",\"vnc_connected\":" + vncConnected + "}");
        LogStore.append(this, "remote", "Heartbeat sent battery=" + battery
                + " vpn_enabled=" + vpnEnabled
                + " vpn_connected=" + vpnConnected
                + " vnc_enabled=" + vncEnabled
                + " vnc_connected=" + vncConnected);
    }

    private boolean queueManualPing(String reason) {
        RemoteWebSocketClient current = client;
        if (current == null || !current.isOpen()) {
            return false;
        }
        pendingManualPingReasons.add(reason);
        LogStore.append(this, "remote", "Manual ping queued reason=" + reason);
        return true;
    }

    private void sendQueuedPings(RemoteWebSocketClient current) {
        String reason;
        while ((reason = pendingManualPingReasons.poll()) != null) {
            sendManualPing(current, reason);
        }
    }

    private void sendManualPing(RemoteWebSocketClient current, String reason) {
        String id = "android-" + UUID.randomUUID().toString();
        try {
            JSONObject ping = new JSONObject();
            ping.put("type", "ping");
            ping.put("id", id);
            ping.put("ts", System.currentTimeMillis() / 1000L);
            ping.put("source", "android");
            current.sendText(ping.toString());
            LogStore.append(this, "remote", "Manual ping sent id=" + id + " reason=" + reason);
        } catch (Exception e) {
            LogStore.append(this, "remote", "Manual ping failed id=" + id + ": "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            current.close();
        }
    }

    private boolean sendGpsPayload(JSONObject payload) {
        RemoteWebSocketClient current = client;
        if (current == null || !current.isOpen()) {
            LogStore.append(this, "remote", "Remote Link not connected for GPS payload");
            return false;
        }
        String id = payload.optString("id", "");
        if (id.isEmpty()) {
            id = "gps-" + UUID.randomUUID().toString();
            try {
                payload.put("id", id);
            } catch (Exception e) {
                LogStore.append(this, "remote", "GPS payload id failed: " + e.getMessage());
                return false;
            }
        }

        PendingGpsAck pending = new PendingGpsAck();
        synchronized (gpsAckLock) {
            pendingGpsAcks.put(id, pending);
        }
        try {
            current.sendText(payload.toString());
            heartbeatResetAtMillis = SystemClock.elapsedRealtime();
            LogStore.append(this, "remote", "GPS payload sent over Remote Link id=" + id);
        } catch (Exception e) {
            synchronized (gpsAckLock) {
                pendingGpsAcks.remove(id);
            }
            LogStore.append(this, "remote", "GPS payload send failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            current.close();
            return false;
        }

        long deadline = SystemClock.elapsedRealtime() + GPS_ACK_TIMEOUT_MILLIS;
        synchronized (gpsAckLock) {
            while (!pending.complete) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0L) {
                    pendingGpsAcks.remove(id);
                    LogStore.append(this, "remote", "GPS ack timeout id=" + id
                            + " timeout_ms=" + GPS_ACK_TIMEOUT_MILLIS);
                    current.close();
                    return false;
                }
                try {
                    gpsAckLock.wait(Math.min(remaining, 1000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pendingGpsAcks.remove(id);
                    LogStore.append(this, "remote", "GPS ack wait interrupted id=" + id);
                    return false;
                }
            }
            if (!pending.ok) {
                LogStore.append(this, "remote", "GPS ack reported failure id=" + id
                        + " reason=" + pending.reason);
                return false;
            }
        }
        return true;
    }

    private static final class PendingGpsAck {
        boolean complete;
        boolean ok;
        String reason = "";
    }

    private void closeClient() {
        RemoteWebSocketClient current = client;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException e) {
                LogStore.append(this, "remote", "Remote Link close failed: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    private boolean sleepUnlessWoken(long delayMillis) {
        long endAt = SystemClock.elapsedRealtime() + Math.max(0L, delayMillis);
        while (!stopRequested) {
            if (reconnectWakeRequested) {
                reconnectWakeRequested = false;
                return true;
            }
            long remaining = endAt - SystemClock.elapsedRealtime();
            if (remaining <= 0L) {
                return false;
            }
            try {
                Thread.sleep(Math.min(remaining, 250L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private long backoffDelayMillis(int consecutiveFailures) {
        long capSeconds = Math.max(1L, Config.get(this).remoteLinkReconnectSeconds());
        long seconds;
        if (consecutiveFailures <= 0) {
            seconds = BACKOFF_STEP_SECONDS[0];
        } else if (consecutiveFailures - 1 < BACKOFF_STEP_SECONDS.length) {
            seconds = BACKOFF_STEP_SECONDS[consecutiveFailures - 1];
        } else {
            seconds = capSeconds;
        }
        return Math.min(seconds, capSeconds) * 1000L;
    }

    private Network currentDefaultNetwork() {
        ConnectivityManager manager = connectivityManager;
        if (manager == null) {
            return null;
        }
        try {
            return manager.getActiveNetwork();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            LogStore.append(this, "remote", "Remote Link network callback unavailable; ConnectivityManager missing");
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                handleDefaultNetworkAvailable(network);
            }

            @Override
            public void onLost(Network network) {
                handleDefaultNetworkLost(network);
            }

            @Override
            public void onLosing(Network network, int maxMsToLive) {
                handleDefaultNetworkLosing(network, maxMsToLive);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                handleDefaultNetworkCapabilities(network, networkCapabilities);
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            LogStore.append(this, "remote", "Remote Link network callback registered");
        } catch (RuntimeException e) {
            networkCallback = null;
            LogStore.append(this, "remote", "Remote Link network callback registration failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager == null || networkCallback == null) {
            networkCallback = null;
            return;
        }
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) {
        }
        networkCallback = null;
    }

    private void handleDefaultNetworkAvailable(Network network) {
        RemoteWebSocketClient current = client;
        Network linkNetwork = connectedNetwork;
        if (current == null || !current.isOpen()) {
            requestReconnectWake("default-available");
            return;
        }
        if (linkNetwork == null) {
            requestLivenessProbe("default-available-unknown-link");
            return;
        }
        if (!network.equals(linkNetwork)) {
            LogStore.append(this, "remote", "Default network changed; reconnecting Remote Link");
            reconnectWakeRequested = true;
            current.close();
        }
    }

    private void handleDefaultNetworkLost(Network network) {
        RemoteWebSocketClient current = client;
        Network linkNetwork = connectedNetwork;
        if (current == null || !current.isOpen()) {
            return;
        }
        if (linkNetwork == null || network.equals(linkNetwork)) {
            LogStore.append(this, "remote", "Default network lost; closing Remote Link socket");
            current.close();
        }
    }

    private void handleDefaultNetworkLosing(Network network, int maxMsToLive) {
        RemoteWebSocketClient current = client;
        Network linkNetwork = connectedNetwork;
        if (current == null || !current.isOpen()) {
            return;
        }
        if (linkNetwork == null || network.equals(linkNetwork)) {
            requestLivenessProbe("losing-in-" + maxMsToLive + "ms");
        }
    }

    private void handleDefaultNetworkCapabilities(Network network, NetworkCapabilities capabilities) {
        Network linkNetwork = connectedNetwork;
        if (linkNetwork != null && network.equals(linkNetwork)) {
            linkOnWifi = capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        maybeProbeOnWeakSignal(network, capabilities);
        boolean validated = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean wasValidated = defaultValidated;
        defaultValidated = validated;
        if (wasValidated == validated) {
            return;
        }
        if (!validated) {
            RemoteWebSocketClient current = client;
            if (current != null && current.isOpen()
                    && (linkNetwork == null || network.equals(linkNetwork))) {
                requestLivenessProbe("validated-lost");
            }
        } else {
            requestReconnectWake("validated-restored");
        }
    }

    /**
     * As the phone walks toward the edge of wifi range the RSSI degrades well
     * before the link blackholes, so a weak reading is the earliest available
     * signal to verify the socket. Fires once on crossing the threshold, then
     * at most every WEAK_SIGNAL_PROBE_MIN_INTERVAL_MILLIS while it stays weak.
     */
    private void maybeProbeOnWeakSignal(Network network, NetworkCapabilities capabilities) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || capabilities == null) {
            return;
        }
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return;
        }
        int rssi = capabilities.getSignalStrength();
        if (rssi == Integer.MIN_VALUE || rssi > WIFI_WEAK_RSSI_DBM) {
            return;
        }
        RemoteWebSocketClient current = client;
        Network linkNetwork = connectedNetwork;
        if (current == null || !current.isOpen()
                || (linkNetwork != null && !network.equals(linkNetwork))) {
            return;
        }
        // Unconditional throttle: RSSI jitters across the threshold at the
        // edge of range, and each wobble must not become its own probe.
        long now = SystemClock.elapsedRealtime();
        if (now - lastWeakSignalProbeAt < WEAK_SIGNAL_PROBE_MIN_INTERVAL_MILLIS) {
            return;
        }
        lastWeakSignalProbeAt = now;
        requestLivenessProbe("weak-signal-rssi=" + rssi);
    }

    private boolean isWifiNetwork(Network network) {
        ConnectivityManager manager = connectivityManager;
        if (network == null || manager == null) {
            return false;
        }
        try {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void requestReconnectWake(String reason) {
        RemoteWebSocketClient current = client;
        if (current != null && current.isOpen()) {
            return;
        }
        if (!reconnectWakeRequested) {
            reconnectWakeRequested = true;
            LogStore.append(this, "remote", "Remote Link reconnect wake requested reason=" + reason);
        }
    }

    private void requestLivenessProbe(String reason) {
        RemoteWebSocketClient current = client;
        if (current == null || !current.isOpen()) {
            return;
        }
        livenessProbeReason = reason == null ? "unknown" : reason;
        if (livenessProbeRequested.compareAndSet(false, true)) {
            LogStore.append(this, "remote", "Remote Link liveness probe requested reason=" + livenessProbeReason);
        }
    }

    private void startForegroundRemoteLink() {
        boolean shown = ServiceNotifications.shown(this, ServiceNotifications.REMOTE_LINK);
        ensureNotificationChannel();
        Notification.Builder builder = new Notification.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_stat_system_manager)
                .setContentTitle("System Manager")
                .setContentText("Remote Link active")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true);
        ServiceNotifications.applyBehavior(builder, shown);
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Uncapped, unlike dataSync; see the manifest entry for why.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void removeForegroundNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (RuntimeException e) {
            LogStore.append(this, "remote", "Remote Link foreground stop failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void ensureNotificationChannel() {
        channelId = ServiceNotifications.channel(
                this,
                CHANNEL_ID,
                "Remote Link",
                "Keeps the System Manager Remote Link connected.",
                NotificationManager.IMPORTANCE_MIN,
                ServiceNotifications.shown(this, ServiceNotifications.REMOTE_LINK));
    }
}
