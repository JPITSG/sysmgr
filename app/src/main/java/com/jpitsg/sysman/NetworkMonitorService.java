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

public final class NetworkMonitorService extends Service {
    private static final String CHANNEL_ID_HIDDEN = "system_manager_network_monitor_hidden";
    private static final String CHANNEL_ID_VISIBLE = "system_manager_network_monitor_visible";
    private static final int NOTIFICATION_ID = 0x5302;

    private WifiChangeMonitor monitor;

    static void sync(Context context) {
        Config config = Config.get(context);
        if (config.isTrackingEnabled() && config.postOnWifiChange() && config.showWifiMonitorNotification()) {
            SystemManagerAccessibilityService.sync(context);
            start(context);
        } else {
            stop(context);
            SystemManagerAccessibilityService.sync(context);
        }
    }

    static void start(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, NetworkMonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (RuntimeException e) {
            LogStore.append(app, "network", "Could not start network monitor: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    static void stop(Context context) {
        context.getApplicationContext().stopService(new Intent(context.getApplicationContext(), NetworkMonitorService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        monitor = new WifiChangeMonitor(this, "foreground");
        ensureNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Config.get(this).isTrackingEnabled() || !Config.get(this).postOnWifiChange()) {
            LogStore.append(this, "network", "Monitor stopping; tracking or Wi-Fi change posts disabled");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (!startForegroundMonitor()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        monitor.start();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (monitor != null) {
            monitor.stop();
        }
        super.onDestroy();
    }

    private boolean startForegroundMonitor() {
        Config config = Config.get(this);
        boolean visible = config.showWifiMonitorNotification();
        Notification notification = new Notification.Builder(this, visible ? CHANNEL_ID_VISIBLE : CHANNEL_ID_HIDDEN)
                .setSmallIcon(R.drawable.ic_stat_system_manager)
                .setContentTitle("System Manager")
                .setContentText("Monitoring Wi-Fi changes")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .setDefaults(0)
                .setVibrate(new long[0])
                .setSound(null)
                .setPriority(visible ? Notification.PRIORITY_LOW : Notification.PRIORITY_MIN)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (RuntimeException e) {
            LogStore.append(this, "network", "Could not start foreground monitor: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
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
        NotificationChannel hidden = new NotificationChannel(
                CHANNEL_ID_HIDDEN,
                "Network monitor hidden",
                NotificationManager.IMPORTANCE_MIN);
        hidden.setDescription("Keeps System Manager aware of Wi-Fi connection changes with a minimized silent notification.");
        hidden.setShowBadge(false);
        hidden.setSound(null, null);
        manager.createNotificationChannel(hidden);

        NotificationChannel visible = new NotificationChannel(
                CHANNEL_ID_VISIBLE,
                "Network monitor",
                NotificationManager.IMPORTANCE_LOW);
        visible.setDescription("Shows when System Manager is watching Wi-Fi connection changes.");
        visible.setShowBadge(false);
        manager.createNotificationChannel(visible);
    }
}
