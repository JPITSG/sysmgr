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
    private static final String KEY_THROUGHPUT_BPS = "throughput_bps";

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
                .remove(KEY_THROUGHPUT_BPS)
                .apply();
        notifyChanged(context);
    }

    static void setThroughputResult(Context context, long bitsPerSecond) {
        prefs(context).edit()
                .putBoolean(KEY_THROUGHPUT_TESTING, false)
                .putLong(KEY_THROUGHPUT_BPS, Math.max(0L, bitsPerSecond))
                .apply();
        notifyChanged(context);
    }

    static void setThroughputUnknown(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_THROUGHPUT_TESTING, false)
                .remove(KEY_THROUGHPUT_BPS)
                .apply();
        notifyChanged(context);
    }

    static boolean isThroughputTesting(Context context) {
        return prefs(context).getBoolean(KEY_THROUGHPUT_TESTING, false);
    }

    static long throughputBitsPerSecond(Context context) {
        return prefs(context).getLong(KEY_THROUGHPUT_BPS, -1L);
    }

    /** A process death can leave a persisted Testing label with no worker behind it. */
    static void clearInterruptedTests(Context context) {
        SharedPreferences state = prefs(context);
        boolean latency = state.getBoolean(KEY_LATENCY_TESTING, false);
        boolean throughput = state.getBoolean(KEY_THROUGHPUT_TESTING, false);
        if (!latency && !throughput) {
            return;
        }
        SharedPreferences.Editor edit = state.edit();
        if (latency) {
            edit.putBoolean(KEY_LATENCY_TESTING, false).remove(KEY_LATENCY_MICROS);
        }
        if (throughput) {
            edit.putBoolean(KEY_THROUGHPUT_TESTING, false).remove(KEY_THROUGHPUT_BPS);
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
