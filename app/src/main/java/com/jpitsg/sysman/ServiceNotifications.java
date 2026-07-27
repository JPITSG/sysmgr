package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * Per-service control over the ongoing notification that every foreground
 * service is obliged to post.
 *
 * <p>Android gives no way to run a foreground service without one:
 * {@code startForeground()} takes a notification, and a service started with
 * {@code startForegroundService()} that never posts is killed with
 * {@code ForegroundServiceDidNotStartInTimeException}. What an app <em>can</em>
 * do is post on a channel it created at {@link
 * NotificationManager#IMPORTANCE_NONE}, which the platform never displays. So
 * "off" here means the notification is still handed to the system — the
 * service keeps its foreground status and the exemptions that come with it —
 * but nothing reaches the shade or the status bar. Android may still count the
 * app in its own "running in the background" entry; that notification belongs
 * to the system, not to us.
 */
final class ServiceNotifications {
    static final String TASK_RUNNER = "task_runner";
    static final String REMOTE_LINK = "remote_link";
    static final String VPN = "vpn";
    static final String BEACON = "beacon";

    private ServiceNotifications() {
    }

    /** True when the user wants this service's ongoing notification on screen. */
    static boolean shown(Context context, String service) {
        Config config = Config.get(context);
        if (REMOTE_LINK.equals(service)) {
            return config.remoteLinkShowNotification();
        }
        if (VPN.equals(service)) {
            return config.vpnNotificationEnabled();
        }
        if (BEACON.equals(service)) {
            return config.beaconNotificationEnabled();
        }
        return config.taskServiceNotificationEnabled();
    }

    /**
     * Creates the channel matching the current preference and returns its id.
     * A channel's importance is fixed at creation — the user owns it after
     * that — so hiding and showing means two channels, one blocked and one
     * not. The unused variant is deleted so the system's channel list keeps
     * one entry per service instead of a visible/hidden pair.
     */
    static String channel(Context context, String baseId, String name, String description,
                          int shownImportance, boolean shown) {
        String hiddenId = baseId + "_hidden";
        String id = shown ? baseId : hiddenId;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return id;
        }
        NotificationChannel channel = new NotificationChannel(
                id,
                shown ? name : name + " (hidden)",
                shown ? shownImportance : NotificationManager.IMPORTANCE_NONE);
        channel.setDescription(description);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        try {
            manager.createNotificationChannel(channel);
            manager.deleteNotificationChannel(shown ? hiddenId : baseId);
        } catch (RuntimeException e) {
            LogStore.append(context, "notification", "Channel setup failed for " + id + ": "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return id;
    }

    /**
     * Asks the platform to defer a hidden notification for the ten seconds it
     * allows, so a service that finishes inside that window is never even
     * considered for display. Belt and braces over the blocked channel, and
     * the only thing that helps on a channel the user has re-enabled.
     */
    static void applyBehavior(Notification.Builder builder, boolean shown) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(shown
                    ? Notification.FOREGROUND_SERVICE_DEFAULT
                    : Notification.FOREGROUND_SERVICE_DEFERRED);
        }
    }
}
