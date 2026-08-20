package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Persists the most recent Remote Link quality measurements for the status card. */
final class RemoteLinkTestStateStore {
    static final String ACTION_STATE_CHANGED =
            "com.jpitsg.sysman.action.REMOTE_LINK_TEST_STATE_CHANGED";

    private static final String PREFS = "system_manager_remote_link_test_state";
    private static final String KEY_LATENCY_TESTING = "latency_testing";
    private static final String KEY_LATENCY_MICROS = "latency_micros";
    private static final String KEY_THROUGHPUT_TESTING = "throughput_testing";
    private static final String KEY_THROUGHPUT_PHASE = "throughput_phase";
    private static final String KEY_THROUGHPUT_UPLOAD_BPS = "throughput_upload_bps";
    private static final String KEY_THROUGHPUT_DOWNLOAD_BPS = "throughput_download_bps";
    private static final String KEY_THROUGHPUT_UPLOAD_BYTES = "throughput_upload_bytes";
    private static final String KEY_THROUGHPUT_DOWNLOAD_BYTES = "throughput_download_bytes";
    private static final String KEY_LEGACY_THROUGHPUT_BPS = "throughput_bps";

    static final String THROUGHPUT_PHASE_UPLOAD = "upload";
    static final String THROUGHPUT_PHASE_DOWNLOAD = "download";

    private RemoteLinkTestStateStore() {
    }

    static void setLatencyTesting(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_LATENCY_TESTING, true)
                .remove(KEY_LATENCY_MICROS)
                .apply();
        notifyChanged(context);
    }

    static void setLatencyResult(Context context, long latencyMicros) {
        prefs(context).edit()
                .putBoolean(KEY_LATENCY_TESTING, false)
                .putLong(KEY_LATENCY_MICROS, Math.max(0L, latencyMicros))
                .apply();
        notifyChanged(context);
    }

    static void setLatencyProgress(Context context, long latencyMicros) {
        prefs(context).edit()
                .putBoolean(KEY_LATENCY_TESTING, true)
                .putLong(KEY_LATENCY_MICROS, Math.max(0L, latencyMicros))
                .apply();
        notifyChanged(context);
    }

    static void setLatencyUnknown(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_LATENCY_TESTING, false)
                .remove(KEY_LATENCY_MICROS)
                .apply();
        notifyChanged(context);
    }

    static boolean isLatencyTesting(Context context) {
        return prefs(context).getBoolean(KEY_LATENCY_TESTING, false);
    }

    static long latencyMicros(Context context) {
        return prefs(context).getLong(KEY_LATENCY_MICROS, -1L);
    }

    static void setThroughputTesting(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_THROUGHPUT_TESTING, true)
                .putString(KEY_THROUGHPUT_PHASE, THROUGHPUT_PHASE_UPLOAD)
                .remove(KEY_THROUGHPUT_UPLOAD_BPS)
                .remove(KEY_THROUGHPUT_DOWNLOAD_BPS)
                .putLong(KEY_THROUGHPUT_UPLOAD_BYTES, 0L)
                .putLong(KEY_THROUGHPUT_DOWNLOAD_BYTES, 0L)
                .remove(KEY_LEGACY_THROUGHPUT_BPS)
                .apply();
        notifyChanged(context);
    }

    static void setThroughputUploadProgress(Context context, long uploadBytes) {
        prefs(context).edit()
                .putBoolean(KEY_THROUGHPUT_TESTING, true)
                .putString(KEY_THROUGHPUT_PHASE, THROUGHPUT_PHASE_UPLOAD)
                .putLong(KEY_THROUGHPUT_UPLOAD_BYTES, Math.max(0L, uploadBytes))
                .apply();
        notifyChanged(context);
    }

    static void setThroughputReceiving(Context context, long uploadBitsPerSecond) {
        prefs(context).edit()
                .putBoolean(KEY_THROUGHPUT_TESTING, true)
                .putString(KEY_THROUGHPUT_PHASE, THROUGHPUT_PHASE_DOWNLOAD)
                .putLong(KEY_THROUGHPUT_UPLOAD_BPS, Math.max(0L, uploadBitsPerSecond))
                .remove(KEY_THROUGHPUT_DOWNLOAD_BPS)
                .putLong(KEY_THROUGHPUT_DOWNLOAD_BYTES, 0L)
                .remove(KEY_LEGACY_THROUGHPUT_BPS)
                .apply();
        notifyChanged(context);
    }

    static void setThroughputDownloadProgress(Context context, long downloadBytes) {
        prefs(context).edit()
                .putBoolean(KEY_THROUGHPUT_TESTING, true)
                .putString(KEY_THROUGHPUT_PHASE, THROUGHPUT_PHASE_DOWNLOAD)
                .putLong(KEY_THROUGHPUT_DOWNLOAD_BYTES, Math.max(0L, downloadBytes))
                .apply();
        notifyChanged(context);
    }

    static void setThroughputResult(Context context, long uploadBitsPerSecond,
                                    long downloadBitsPerSecond) {
        prefs(context).edit()
                .putBoolean(KEY_THROUGHPUT_TESTING, false)
                .remove(KEY_THROUGHPUT_PHASE)
                .putLong(KEY_THROUGHPUT_UPLOAD_BPS, Math.max(0L, uploadBitsPerSecond))
                .putLong(KEY_THROUGHPUT_DOWNLOAD_BPS, Math.max(0L, downloadBitsPerSecond))
                .remove(KEY_THROUGHPUT_UPLOAD_BYTES)
                .remove(KEY_THROUGHPUT_DOWNLOAD_BYTES)
                .remove(KEY_LEGACY_THROUGHPUT_BPS)
                .apply();
        notifyChanged(context);
    }

    static void setThroughputFailed(Context context, long uploadBitsPerSecond) {
        SharedPreferences.Editor edit = prefs(context).edit()
                .putBoolean(KEY_THROUGHPUT_TESTING, false)
                .remove(KEY_THROUGHPUT_PHASE)
                .remove(KEY_THROUGHPUT_DOWNLOAD_BPS)
                .remove(KEY_THROUGHPUT_UPLOAD_BYTES)
                .remove(KEY_THROUGHPUT_DOWNLOAD_BYTES)
                .remove(KEY_LEGACY_THROUGHPUT_BPS);
        if (uploadBitsPerSecond >= 0L) {
            edit.putLong(KEY_THROUGHPUT_UPLOAD_BPS, uploadBitsPerSecond);
        } else {
            edit.remove(KEY_THROUGHPUT_UPLOAD_BPS);
        }
        edit.apply();
        notifyChanged(context);
    }

    static boolean isThroughputTesting(Context context) {
        return prefs(context).getBoolean(KEY_THROUGHPUT_TESTING, false);
    }

    static String throughputPhase(Context context) {
        return prefs(context).getString(KEY_THROUGHPUT_PHASE, "");
    }

    static long uploadBitsPerSecond(Context context) {
        return prefs(context).getLong(KEY_THROUGHPUT_UPLOAD_BPS, -1L);
    }

    static long downloadBitsPerSecond(Context context) {
        return prefs(context).getLong(KEY_THROUGHPUT_DOWNLOAD_BPS, -1L);
    }

    static long uploadBytes(Context context) {
        return prefs(context).getLong(KEY_THROUGHPUT_UPLOAD_BYTES, 0L);
    }

    static long downloadBytes(Context context) {
        return prefs(context).getLong(KEY_THROUGHPUT_DOWNLOAD_BYTES, 0L);
    }

    /** A process death can leave a persisted Testing label with no worker behind it. */
    static void clearInterruptedTests(Context context) {
        SharedPreferences state = prefs(context);
        boolean latency = state.getBoolean(KEY_LATENCY_TESTING, false);
        boolean throughput = state.getBoolean(KEY_THROUGHPUT_TESTING, false);
        boolean hasLegacyThroughput = state.contains(KEY_LEGACY_THROUGHPUT_BPS);
        if (!latency && !throughput && !hasLegacyThroughput) {
            return;
        }
        SharedPreferences.Editor edit = state.edit();
        if (latency) {
            edit.putBoolean(KEY_LATENCY_TESTING, false).remove(KEY_LATENCY_MICROS);
        }
        if (throughput) {
            edit.putBoolean(KEY_THROUGHPUT_TESTING, false)
                    .remove(KEY_THROUGHPUT_PHASE)
                    .remove(KEY_THROUGHPUT_DOWNLOAD_BPS)
                    .remove(KEY_THROUGHPUT_UPLOAD_BYTES)
                    .remove(KEY_THROUGHPUT_DOWNLOAD_BYTES);
        }
        if (hasLegacyThroughput) {
            if (!state.contains(KEY_THROUGHPUT_UPLOAD_BPS)) {
                edit.putLong(KEY_THROUGHPUT_UPLOAD_BPS,
                        Math.max(0L, state.getLong(KEY_LEGACY_THROUGHPUT_BPS, 0L)));
            }
            edit.remove(KEY_LEGACY_THROUGHPUT_BPS);
        }
        edit.apply();
        notifyChanged(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void notifyChanged(Context context) {
        Context app = context.getApplicationContext();
        app.sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(app.getPackageName()));
    }
}
