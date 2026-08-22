package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Tracks whether the server is currently accepting Notification Backups (i.e.
 * whether sysmgrd was started with a notification store configured). Learned from
 * the server on every connect (hello_ack), every heartbeat (heartbeat_ack), an
 * explicit probe (notif_backup_status), and delivery acks — so the app reflects
 * the server disabling the feature mid-session, not just at connect time.
 */
final class NotificationBackupStateStore {
    static final String ACTION_STATE_CHANGED = "com.jpitsg.sysman.action.NOTIFICATION_BACKUP_STATE_CHANGED";
    private static final String PREFS = "system_manager_notification_backup_state";
    private static final String KEY_SERVER_AVAILABLE = "server_available";
    private static final String KEY_CHECKED = "checked";
    private static final String KEY_SENT_COUNT = "sent_count";
    private static final String KEY_LAST_SENT_AT_MILLIS = "last_sent_at_millis";
    private static final Object STATS_LOCK = new Object();

    private NotificationBackupStateStore() {
    }

    static void setChecking(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean changed = prefs.getBoolean(KEY_CHECKED, false)
                || prefs.getBoolean(KEY_SERVER_AVAILABLE, false);
        prefs.edit()
                .putBoolean(KEY_SERVER_AVAILABLE, false)
                .putBoolean(KEY_CHECKED, false)
                .apply();
        if (changed) {
            app.sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(app.getPackageName()));
        }
    }

    static void setServerAvailable(Context context, boolean available) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean changed = prefs.getBoolean(KEY_SERVER_AVAILABLE, false) != available
                || !prefs.getBoolean(KEY_CHECKED, false);
        prefs.edit()
                .putBoolean(KEY_SERVER_AVAILABLE, available)
                .putBoolean(KEY_CHECKED, true)
                .apply();
        if (changed) {
            app.sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(app.getPackageName()));
        }
    }

    static boolean isServerAvailable(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SERVER_AVAILABLE, false);
    }

    static boolean isChecked(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CHECKED, false);
    }

    static void recordSuccessfulSend(Context context) {
        Context app = context.getApplicationContext();
        synchronized (STATS_LOCK) {
            SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long current = Math.max(0L, prefs.getLong(KEY_SENT_COUNT, 0L));
            long updated = current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L;
            prefs.edit()
                    .putLong(KEY_SENT_COUNT, updated)
                    .putLong(KEY_LAST_SENT_AT_MILLIS, System.currentTimeMillis())
                    .apply();
        }
        app.sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(app.getPackageName()));
    }

    static long sentCount(Context context) {
        return Math.max(0L, context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_SENT_COUNT, 0L));
    }

    static long lastSentAtMillis(Context context) {
        return Math.max(0L, context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SENT_AT_MILLIS, 0L));
    }
}
