package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Latest upgrade APK capability and metadata reported by sysmgrd. */
final class UpgradeStateStore {
    static final String ACTION_STATE_CHANGED =
            "com.jpitsg.sysman.action.UPGRADE_STATE_CHANGED";

    private static final String PREFS = "system_manager_upgrade_state";
    private static final String KEY_CHECKED = "checked";
    private static final String KEY_CONFIGURED = "configured";
    private static final String KEY_EXISTS = "exists";
    private static final String KEY_SIZE = "size";
    private static final String KEY_MTIME = "mtime";
    private static final String KEY_VERSION = "version";

    private UpgradeStateStore() {
    }

    /** Clears capabilities while a newly-connected daemon is being identified. */
    static void setChecking(Context context) {
        set(context, false, false, false, 0L, 0L, "");
    }

    /** Keeps the visible card stable while a user-requested refresh is in flight. */
    static void setRefreshing(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        set(app, false,
                prefs.getBoolean(KEY_CONFIGURED, false),
                prefs.getBoolean(KEY_EXISTS, false),
                prefs.getLong(KEY_SIZE, 0L),
                prefs.getLong(KEY_MTIME, 0L),
                prefs.getString(KEY_VERSION, ""));
    }

    static void setServerState(Context context, boolean configured, boolean exists,
                               long sizeBytes, long modifiedAtMillis) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        boolean available = configured && exists;
        long size = available ? Math.max(0L, sizeBytes) : 0L;
        long modifiedAt = available ? Math.max(0L, modifiedAtMillis) : 0L;
        boolean sameApk = available
                && prefs.getBoolean(KEY_EXISTS, false)
                && prefs.getLong(KEY_SIZE, 0L) == size
                && sameTimestamp(prefs.getLong(KEY_MTIME, 0L), modifiedAt);
        set(app, true, configured, available, size, modifiedAt,
                sameApk ? prefs.getString(KEY_VERSION, "") : "");
    }

    static boolean isChecked(Context context) {
        return prefs(context).getBoolean(KEY_CHECKED, false);
    }

    static boolean isConfigured(Context context) {
        return prefs(context).getBoolean(KEY_CONFIGURED, false);
    }

    static boolean apkExists(Context context) {
        return prefs(context).getBoolean(KEY_EXISTS, false);
    }

    static long apkSizeBytes(Context context) {
        return prefs(context).getLong(KEY_SIZE, 0L);
    }

    static long apkModifiedAtMillis(Context context) {
        return prefs(context).getLong(KEY_MTIME, 0L);
    }

    static String apkVersionName(Context context) {
        return prefs(context).getString(KEY_VERSION, "");
    }

    static void setApkVersionName(Context context, String versionName) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        String version = versionName == null ? "" : versionName.trim();
        if (version.equals(prefs.getString(KEY_VERSION, ""))) {
            return;
        }
        prefs.edit().putString(KEY_VERSION, version).apply();
        app.sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                .setPackage(app.getPackageName()));
    }

    private static void set(Context context, boolean checked, boolean configured,
                            boolean exists, long sizeBytes, long modifiedAtMillis,
                            String versionName) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        boolean changed = prefs.getBoolean(KEY_CHECKED, false) != checked
                || prefs.getBoolean(KEY_CONFIGURED, false) != configured
                || prefs.getBoolean(KEY_EXISTS, false) != exists
                || prefs.getLong(KEY_SIZE, 0L) != sizeBytes
                || prefs.getLong(KEY_MTIME, 0L) != modifiedAtMillis
                || !prefs.getString(KEY_VERSION, "").equals(versionName);
        prefs.edit()
                .putBoolean(KEY_CHECKED, checked)
                .putBoolean(KEY_CONFIGURED, configured)
                .putBoolean(KEY_EXISTS, exists)
                .putLong(KEY_SIZE, sizeBytes)
                .putLong(KEY_MTIME, modifiedAtMillis)
                .putString(KEY_VERSION, versionName)
                .apply();
        if (changed) {
            app.sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                    .setPackage(app.getPackageName()));
        }
    }

    private static boolean sameTimestamp(long first, long second) {
        return first > 0L && second > 0L && first / 1000L == second / 1000L;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
