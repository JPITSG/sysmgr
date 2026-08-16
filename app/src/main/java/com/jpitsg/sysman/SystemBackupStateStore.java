package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Latest backup capability reported by the connected sysmgrd instance. */
final class SystemBackupStateStore {
    static final String ACTION_STATE_CHANGED =
            "com.jpitsg.sysman.action.SYSTEM_BACKUP_STATE_CHANGED";

    private static final String PREFS = "system_manager_backup_state";
    private static final String KEY_CHECKED = "checked";
    private static final String KEY_SERVER_AVAILABLE = "server_available";
    private static final String KEY_BACKUP_EXISTS = "backup_exists";
    private static final String KEY_BACKUP_MTIME = "backup_mtime";

    private SystemBackupStateStore() {
    }

    static void setChecking(Context context) {
        set(context, false, false, false, 0L);
    }

    static void setServerState(Context context, boolean available, boolean exists,
                               long modifiedAtMillis) {
        set(context, true, available, available && exists,
                available && exists ? Math.max(0L, modifiedAtMillis) : 0L);
    }

    static boolean isChecked(Context context) {
        return prefs(context).getBoolean(KEY_CHECKED, false);
    }

    static boolean isServerAvailable(Context context) {
        return prefs(context).getBoolean(KEY_SERVER_AVAILABLE, false);
    }

    static boolean backupExists(Context context) {
        return prefs(context).getBoolean(KEY_BACKUP_EXISTS, false);
    }

    static long backupModifiedAtMillis(Context context) {
        return prefs(context).getLong(KEY_BACKUP_MTIME, 0L);
    }

    private static void set(Context context, boolean checked, boolean available,
                            boolean exists, long modifiedAtMillis) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        boolean changed = prefs.getBoolean(KEY_CHECKED, false) != checked
                || prefs.getBoolean(KEY_SERVER_AVAILABLE, false) != available
                || prefs.getBoolean(KEY_BACKUP_EXISTS, false) != exists
                || prefs.getLong(KEY_BACKUP_MTIME, 0L) != modifiedAtMillis;
        prefs.edit()
                .putBoolean(KEY_CHECKED, checked)
                .putBoolean(KEY_SERVER_AVAILABLE, available)
                .putBoolean(KEY_BACKUP_EXISTS, exists)
                .putLong(KEY_BACKUP_MTIME, modifiedAtMillis)
                .apply();
        if (changed) {
            app.sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                    .setPackage(app.getPackageName()));
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
