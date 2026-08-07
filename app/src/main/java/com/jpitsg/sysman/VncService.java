package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Hosts the VNC server.
 *
 * <p>The ongoing notification is created here rather than through {@link
 * ServiceNotifications}. Every other service offers a switch to hide its
 * notification; this one deliberately does not. A server that mirrors the
 * screen and injects input should not be able to hide the only on-device sign
 * that it is running, and a switch that ignores the user would be worse than no
 * switch at all. The Wi-Fi monitor already owns its channel the same way.
 *
 * <p>The foreground type is {@code specialUse} rather than {@code dataSync} for
 * the reason recorded on {@link RemoteLinkService}: from Android 15 a dataSync
 * service is force-stopped after six hours in a day, which a server meant to
 * stay reachable cannot live with.
 */
public final class VncService extends Service {
    static final String ACTION_SYNC = "com.jpitsg.sysman.action.VNC_SYNC";
    static final String EXTRA_REASON = "reason";

    private static final String CHANNEL_ID = "system_manager_vnc";
    private static final int NOTIFICATION_ID = 0x5306;

    private static volatile boolean active;

    static boolean isActive() {
        return active;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        active = true;
        ensureNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String reason = intent == null ? "restart" : intent.getStringExtra(EXTRA_REASON);
        if (reason == null) {
            reason = "unknown";
        }

        if (!startForegroundServer()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        evaluate(reason);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        active = false;
        VncStateStore.setListenAddress(this, "");
        if (Config.get(this).vncEnabled() && VncStateStore.isLiveState(VncStateStore.state(this))) {
            // Still armed and still claiming to be live, so nobody asked for
            // this: task killed, low memory, or the system reclaimed us. Leave
            // a truthful state behind rather than a stale "listening". A
            // deliberate stop has already settled the state to OFF.
            VncStateStore.setState(this, VncStateStore.STATE_ERROR, "Service stopped");
        } else if (!Config.get(this).vncEnabled()) {
            VncStateStore.setState(this, VncStateStore.STATE_OFF, "");
        }
        LogStore.append(this, "vnc", "Service stopped");
        super.onDestroy();
    }

    private void evaluate(String reason) {
        if (!Config.get(this).vncEnabled()) {
            LogStore.append(this, "vnc", "Disabled; stopping reason=" + reason);
            stopSelf();
            return;
        }

        String blocking = VncManager.blockingReason(this);
        if (blocking != null) {
            VncStateStore.setState(this, VncStateStore.STATE_BLOCKED, blocking);
            LogStore.append(this, "vnc", "Blocked: " + blocking + " reason=" + reason);
            updateNotification();
            return;
        }

        // Phase 1 wires settings, state and lifecycle only. The capture engine,
        // the listening socket and the RFB session arrive in later phases; this
        // is the single place they hook in.
        VncStateStore.setState(this, VncStateStore.STATE_STARTING, "Capture engine not started yet");
        LogStore.append(this, "vnc", "Armed; capture engine pending reason=" + reason);
        updateNotification();
    }

    private boolean startForegroundServer() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
            return true;
        } catch (RuntimeException e) {
            LogStore.append(this, "vnc", "Foreground start failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            VncStateStore.setState(this, VncStateStore.STATE_ERROR,
                    "Foreground service refused: " + e.getMessage());
            return false;
        }
    }

    private void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            manager.notify(NOTIFICATION_ID, buildNotification());
        } catch (RuntimeException ignored) {
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_system_manager)
                .setContentTitle("VNC server")
                .setContentText(notificationText())
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pending)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .build();
    }

    private String notificationText() {
        String state = VncStateStore.state(this);
        String detail = VncStateStore.detail(this);
        if (VncStateStore.STATE_CONNECTED.equals(state)) {
            String client = VncStateStore.clientAddress(this);
            return client.isEmpty() ? "Client connected" : "Client connected from " + client;
        }
        if (VncStateStore.STATE_LISTENING.equals(state)) {
            String listen = VncStateStore.listenAddress(this);
            return listen.isEmpty() ? "Listening" : "Listening on " + listen;
        }
        String label = VncStateStore.label(state);
        return detail.isEmpty() ? label : label + " — " + detail;
    }

    private void ensureNotificationChannel() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "VNC server",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shows when the VNC server is armed or serving a client.");
        channel.setShowBadge(false);
        channel.setSound(null, null);
        try {
            manager.createNotificationChannel(channel);
        } catch (RuntimeException e) {
            LogStore.append(this, "vnc", "Channel setup failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
