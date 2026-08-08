package com.jpitsg.sysman;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

/**
 * Channel setup for the ongoing notification that every foreground service is
 * obliged to post. Each service gets one named channel, and visibility belongs
 * to the OS: hiding a service's notification is done per channel in Android's
 * notification settings, not through in-app switches.
 *
 * <p>Earlier releases implemented in-app visibility by swapping each service
 * between its channel and a blocked "&lt;id&gt;_hidden" twin at
 * {@link NotificationManager#IMPORTANCE_NONE}. The twins are deleted — on each
 * service's channel setup and in a sweep at app open — so the system's channel
 * list keeps one entry per service.
 */
final class ServiceNotifications {
    /** Base ids whose "_hidden" twins earlier releases may have created. */
    private static final String[] LEGACY_HIDDEN_BASE_IDS = {
            "system_manager_task_service",
            "system_manager_remote_link",
            "system_manager_vpn",
            "system_manager_beacon",
            "system_manager_vnc_listening",
    };

    private ServiceNotifications() {
    }

    /** Creates the service's channel and removes its legacy hidden twin. */
    static void ensureChannel(Context context, String id, String name, String description,
                              int importance) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(description);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        try {
            manager.createNotificationChannel(channel);
            manager.deleteNotificationChannel(id + "_hidden");
        } catch (RuntimeException e) {
            LogStore.append(context, "notification", "Channel setup failed for " + id + ": "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Removes every legacy hidden twin in one pass, covering services that may
     * not run again for a while (or ever) and so would never clean their own.
     */
    static void deleteLegacyHiddenChannels(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        for (String baseId : LEGACY_HIDDEN_BASE_IDS) {
            try {
                manager.deleteNotificationChannel(baseId + "_hidden");
            } catch (RuntimeException ignored) {
            }
        }
    }
}
