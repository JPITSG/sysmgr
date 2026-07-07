package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

final class BatteryAlertManager {
    static final String CHANNEL_ID = "battery_alerts";
    private static final int NOTIFICATION_ID = 0xB477;

    private BatteryAlertManager() {
    }

    static void sync(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (Config.get(app).batteryAlertEnabled()) {
            AlarmScheduler.scheduleBatteryAlertCheck(app, reason);
        } else {
            AlarmScheduler.cancelBatteryAlertCheck(app);
        }
    }

    static void checkAndNotify(Context context, String reason) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        if (!config.batteryAlertEnabled()) {
            AlarmScheduler.cancelBatteryAlertCheck(app);
            return;
        }

        int percent = BatteryReader.batteryPercent(app);
        int threshold = config.batteryAlertThresholdPercent();
        if (percent < 0) {
            LogStore.append(app, "battery", "Battery level unavailable reason=" + reason);
            AlarmScheduler.scheduleBatteryAlertCheck(app, "battery-unavailable");
            return;
        }

        boolean below = percent <= threshold;
        boolean wasBelow = BatteryAlertState.wasBelow(app, threshold);
        LogStore.append(app, "battery", "Check percent=" + percent + " threshold=" + threshold
                + " below=" + below + " wasBelow=" + wasBelow + " reason=" + reason);

        if (below && !wasBelow) {
            issueNotification(app, percent, false);
            BatteryAlertState.mark(app, true, threshold, percent);
        } else if (!below) {
            if (wasBelow) {
                LogStore.append(app, "battery", "Battery rose above threshold; alert reset percent=" + percent
                        + " previous=" + BatteryAlertState.lastPercent(app));
            }
            BatteryAlertState.mark(app, false, threshold, percent);
        }

        AlarmScheduler.scheduleBatteryAlertCheck(app, "after-check");
    }

    static void sendTestNotification(Context context) {
        Context app = context.getApplicationContext();
        int percent = BatteryReader.batteryPercent(app);
        issueNotification(app, percent, true);
    }

    private static void issueNotification(Context context, int percent, boolean test) {
        Config config = Config.get(context);
        String message = batteryMessage(percent);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            LogStore.append(context, "battery", "NotificationManager unavailable");
            vibrate(context, config.batteryAlertVibrateSeconds());
            return;
        }
        if (!PermissionState.notificationsEnabled(context)) {
            LogStore.append(context, "battery", "Notifications are disabled; vibration will still run");
        }

        ensureChannel(manager);
        Intent openIntent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0xB477, openIntent, flags);

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.jpitsg.sysman.R.drawable.ic_stat_system_manager)
                .setContentTitle(test ? "Battery alert test" : "Battery alert")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();

        try {
            manager.notify(NOTIFICATION_ID, notification);
            NotificationHistoryStore.add(context, "Battery", test ? "Battery alert test" : "Battery alert", message, "", false);
            LogStore.append(context, "battery", (test ? "Test notification sent: " : "Notification sent: ") + message);
        } catch (RuntimeException e) {
            LogStore.append(context, "battery", "Notification failed: " + e.getMessage());
        }
        vibrate(context, config.batteryAlertVibrateSeconds());
    }

    private static String batteryMessage(int percent) {
        if (percent < 0) {
            return "The battery is at unknown percent!";
        }
        return "The battery is at " + percent + " percent!";
    }

    private static void ensureChannel(NotificationManager manager) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Battery alerts",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Low battery alerts from System Manager");
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    @SuppressWarnings("deprecation")
    private static void vibrate(Context context, int seconds) {
        if (seconds <= 0) {
            return;
        }
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            LogStore.append(context, "battery", "Vibration unavailable");
            return;
        }

        long durationMillis = seconds * 1000L;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMillis);
            }
            LogStore.append(context, "battery", "Vibrating for " + seconds + "s");
        } catch (RuntimeException e) {
            LogStore.append(context, "battery", "Vibration failed: " + e.getMessage());
        }
    }
}
