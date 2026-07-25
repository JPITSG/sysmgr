package com.jpitsg.sysman;

import android.app.Notification;
import android.service.notification.StatusBarNotification;

/**
 * Decides whether a posted notification is a real, user-facing alert worth
 * backing up. Filters out the ambient/system chrome the user asked to exclude —
 * ongoing media transports (e.g. "Spotify is playing"), foreground-service
 * notifications, group summaries, and empty placeholders.
 */
final class NotificationBackupFilter {
    private NotificationBackupFilter() {
    }

    static boolean isUserFacing(StatusBarNotification sbn, NotificationPayload payload) {
        if (sbn == null) {
            return false;
        }
        Notification notification = sbn.getNotification();
        if (notification == null) {
            return false;
        }
        // Ongoing / non-clearable notifications are transports and status chrome
        // (media playback, downloads, foreground services), not user-facing alerts.
        if (!sbn.isClearable()) {
            return false;
        }
        int flags = notification.flags;
        if ((flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            return false;
        }
        if ((flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return false;
        }
        // The group summary duplicates its children; back up the children instead.
        if ((flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return false;
        }
        if (hasMediaSession(notification)) {
            return false;
        }
        if (payload == null || (payload.title.isEmpty() && payload.text.isEmpty())) {
            return false;
        }
        return true;
    }

    private static boolean hasMediaSession(Notification notification) {
        try {
            return notification.extras != null
                    && notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
