package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemoteLinkService extends Service {
    private static final String CHANNEL_ID = "system_manager_remote_link";
    private static final int NOTIFICATION_ID = 0x5303;
    private static final long GPS_ACK_TIMEOUT_MILLIS = 10_000L;
    private static volatile RemoteLinkService activeService;

    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<String> pendingManualPingReasons = new ConcurrentLinkedQueue<>();
    private final Object gpsAckLock = new Object();
    private final java.util.Map<String, PendingGpsAck> pendingGpsAcks = new java.util.HashMap<>();
    private volatile boolean stopRequested;
    private volatile RemoteWebSocketClient client;
    private volatile long heartbeatResetAtMillis;
    private Thread worker;

    @Override
    public void onCreate() {
        super.onCreate();
        activeService = this;
        ensureNotificationChannel();
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

        if (config.remoteLinkShowNotification()) {
            try {
                startForegroundRemoteLink();
            } catch (RuntimeException e) {
                LogStore.append(this, "remote", "Remote Link foreground start failed: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                stopSelf(startId);
                return START_NOT_STICKY;
            }
        } else {
            removeForegroundNotification();
        }
        if (RemoteLinkManager.ACTION_RESTART.equals(action)) {
            LogStore.append(this, "remote", "Remote Link reconnect requested reason=" + reason);
            closeClient();
        }
        startWorkerIfNeeded(reason);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopRequested = true;
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
        int failedConnectsSinceSuccess = 0;
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
                try {
                    LogStore.append(this, "remote", "Connecting to " + config.remoteLinkEndpoint());
                    current.connect();
                    connected = true;
                    failedConnectsSinceSuccess = 0;
                    RemoteLinkStateStore.setConnected(this, true);
                    LogStore.append(this, "remote", "Remote Link connected");
                    sendHello(current, config);
                    runConnectedLoop(current);
                    RemoteLinkStateStore.setConnected(this, false);
                    LogStore.append(this, "remote", "Remote Link disconnected");
                } catch (Exception e) {
                    RemoteLinkStateStore.setConnected(this, false);
                    if (connected) {
                        failedConnectsSinceSuccess = 0;
                        LogStore.append(this, "remote", "Remote Link lost: "
                                + e.getClass().getSimpleName() + ": " + e.getMessage());
                    } else {
                        long delayMillis = failedConnectsSinceSuccess == 0
                                ? 1000L
                                : Config.get(this).remoteLinkReconnectSeconds() * 1000L;
                        failedConnectsSinceSuccess++;
                        LogStore.append(this, "remote", "Remote Link connect failed; retry in "
                                + (delayMillis / 1000L) + "s: "
                                + e.getClass().getSimpleName() + ": " + e.getMessage());
                        sleepInterruptibly(delayMillis);
                    }
                } finally {
                    current.close();
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
        long lastHeartbeatResetAt = heartbeatResetAtMillis;
        while (!stopRequested && Config.get(this).remoteLinkEnabled() && current.isOpen()) {
            Config config = Config.get(this);
            sendQueuedPings(current);
            String message = current.readTextFrame();
            long inboundAt = current.lastInboundAtMillis();
            if (inboundAt > lastInboundAt) {
                lastInboundAt = inboundAt;
                nextHeartbeatAt = inboundAt + heartbeatDelayMillis(config);
                LogStore.append(this, "remote", "Heartbeat timer reset by inbound traffic next_in="
                        + config.remoteLinkHeartbeatSeconds() + "s");
            }
            if (message != null) {
                if (!handleServiceMessage(message)) {
                    RemoteEventHandler.handle(this, current, message);
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
            }
        }
    }

    private boolean handleServiceMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            if ("gps_ack".equals(json.optString("type", ""))) {
                handleGpsAck(json);
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

    private long heartbeatDelayMillis(Config config) {
        return Math.max(1L, config.remoteLinkHeartbeatSeconds()) * 1000L;
    }

    private void sendHello(RemoteWebSocketClient current, Config config) throws IOException {
        current.sendText("{\"type\":\"hello\",\"app\":\"SystemManager\",\"version\":\"1.0\","
                + "\"uuid\":\"" + config.installUuid() + "\","
                + "\"heartbeat_seconds\":" + config.remoteLinkHeartbeatSeconds() + "}");
    }

    private void sendHeartbeat(RemoteWebSocketClient current) throws IOException {
        int battery = BatteryReader.batteryPercent(this);
        current.sendText("{\"type\":\"heartbeat\",\"ts\":" + (System.currentTimeMillis() / 1000L)
                + ",\"battery\":" + battery + "}");
        LogStore.append(this, "remote", "Heartbeat sent battery=" + battery);
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

    private void sleepInterruptibly(long delayMillis) {
        long endAt = SystemClock.elapsedRealtime() + Math.max(0L, delayMillis);
        while (!stopRequested) {
            long remaining = endAt - SystemClock.elapsedRealtime();
            if (remaining <= 0L) {
                return;
            }
            try {
                Thread.sleep(Math.min(remaining, 1000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void startForegroundRemoteLink() {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_system_manager)
                .setContentTitle("System Manager")
                .setContentText("Remote Link active")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setPriority(Notification.PRIORITY_MIN)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Remote Link",
                NotificationManager.IMPORTANCE_MIN);
        channel.setDescription("Keeps the System Manager Remote Link connected.");
        channel.setShowBadge(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
    }
}
