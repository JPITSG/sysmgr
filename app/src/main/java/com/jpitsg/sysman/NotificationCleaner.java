package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;

import java.util.HashSet;
import java.util.Set;

final class NotificationCleaner {
    private static final Set<String> CLEARABLE_CHANNELS = new HashSet<>();

    static {
        CLEARABLE_CHANNELS.add(RemoteEventHandler.CHANNEL_ID);
        CLEARABLE_CHANNELS.add(BatteryAlertManager.CHANNEL_ID);
    }

    private NotificationCleaner() {
    }

    static void clearOnAppOpen(Context context) {
        Context app = context.getApplicationContext();
        if (!Config.get(app).clearNotificationsOnOpen()) {
            return;
        }
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            LogStore.append(app, "notification", "Notification clear skipped; NotificationManager unavailable");
            return;
        }

        int cleared = 0;
        int inspected = 0;
        try {
            StatusBarNotification[] active = manager.getActiveNotifications();
            if (active == null) {
                return;
            }
            for (StatusBarNotification notification : active) {
                if (notification == null || !app.getPackageName().equals(notification.getPackageName())) {
                    continue;
                }
                inspected++;
                Notification raw = notification.getNotification();
                String channelId = raw == null ? "" : raw.getChannelId();
                if (!CLEARABLE_CHANNELS.contains(channelId)) {
                    continue;
                }
                manager.cancel(notification.getTag(), notification.getId());
                cleared++;
            }
        } catch (RuntimeException e) {
            LogStore.append(app, "notification", "Notification clear failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        if (cleared > 0) {
            LogStore.append(app, "notification", "Cleared active notifications count="
                    + cleared + " inspected=" + inspected + " reason=app-open");
        }
    }
}
