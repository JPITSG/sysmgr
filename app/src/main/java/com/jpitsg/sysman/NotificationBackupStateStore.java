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
}
