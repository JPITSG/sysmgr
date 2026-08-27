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
    private static final String KEY_VERSION_CODE = "version_code";
    private static final String KEY_VERSION_READY = "version_ready";

    private UpgradeStateStore() {
    }

    /** Marks the status pending while retaining the last result reported by the server. */
    static void setChecking(Context context) {
        setPending(context);
    }

    /** Marks the status pending while fresh daemon metadata is requested. */
    static void setRefreshing(Context context) {
        setPending(context);
    }

    private static void setPending(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        if (!prefs.getBoolean(KEY_CHECKED, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_CHECKED, false).apply();
        app.sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                .setPackage(app.getPackageName()));
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
                sameApk ? prefs.getString(KEY_VERSION, "") : "",
                sameApk ? prefs.getLong(KEY_VERSION_CODE, 0L) : 0L,
                sameApk && prefs.getBoolean(KEY_VERSION_READY, false));
    }

    static void setServerState(Context context, boolean configured, boolean exists,
                               long sizeBytes, long modifiedAtMillis,
                               String versionName, long versionCode,
                               boolean versionReady) {
        boolean available = configured && exists;
        String version = available && versionReady && versionName != null
                ? versionName.trim() : "";
        long code = available && versionReady ? Math.max(0L, versionCode) : 0L;
        boolean ready = available && versionReady && !version.isEmpty() && code > 0L;
        set(context, true, configured, available,
                available ? Math.max(0L, sizeBytes) : 0L,
                available ? Math.max(0L, modifiedAtMillis) : 0L,
                ready ? version : "", ready ? code : 0L, ready);
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

    static long apkVersionCode(Context context) {
        return prefs(context).getLong(KEY_VERSION_CODE, 0L);
    }

    static boolean isApkVersionReady(Context context) {
        return prefs(context).getBoolean(KEY_VERSION_READY, false);
    }

    static void setApkVersion(Context context, String versionName, long versionCode) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        String version = versionName == null ? "" : versionName.trim();
        long code = Math.max(0L, versionCode);
        boolean ready = !version.isEmpty() && code > 0L;
        if (version.equals(prefs.getString(KEY_VERSION, ""))
                && code == prefs.getLong(KEY_VERSION_CODE, 0L)
                && ready == prefs.getBoolean(KEY_VERSION_READY, false)) {
            return;
        }
        prefs.edit()
                .putString(KEY_VERSION, ready ? version : "")
                .putLong(KEY_VERSION_CODE, ready ? code : 0L)
                .putBoolean(KEY_VERSION_READY, ready)
                .apply();
        app.sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                .setPackage(app.getPackageName()));
    }

    private static void set(Context context, boolean checked, boolean configured,
                            boolean exists, long sizeBytes, long modifiedAtMillis,
                            String versionName, long versionCode,
                            boolean versionReady) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = prefs(app);
        boolean changed = prefs.getBoolean(KEY_CHECKED, false) != checked
                || prefs.getBoolean(KEY_CONFIGURED, false) != configured
                || prefs.getBoolean(KEY_EXISTS, false) != exists
                || prefs.getLong(KEY_SIZE, 0L) != sizeBytes
                || prefs.getLong(KEY_MTIME, 0L) != modifiedAtMillis
                || !prefs.getString(KEY_VERSION, "").equals(versionName)
                || prefs.getLong(KEY_VERSION_CODE, 0L) != versionCode
                || prefs.getBoolean(KEY_VERSION_READY, false) != versionReady;
        prefs.edit()
                .putBoolean(KEY_CHECKED, checked)
                .putBoolean(KEY_CONFIGURED, configured)
                .putBoolean(KEY_EXISTS, exists)
                .putLong(KEY_SIZE, sizeBytes)
                .putLong(KEY_MTIME, modifiedAtMillis)
                .putString(KEY_VERSION, versionName)
                .putLong(KEY_VERSION_CODE, versionCode)
                .putBoolean(KEY_VERSION_READY, versionReady)
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
