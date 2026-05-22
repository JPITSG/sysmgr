package com.jpitsg.sysman;

import android.content.Context;
import android.content.SharedPreferences;

final class BatteryAlertState {
    private static final String PREFS = "battery_alert_state";
    private static final String KEY_WAS_BELOW = "was_below";
    private static final String KEY_THRESHOLD = "threshold";
    private static final String KEY_LAST_PERCENT = "last_percent";

    private BatteryAlertState() {
    }

    static boolean wasBelow(Context context, int threshold) {
        SharedPreferences prefs = prefs(context);
        return prefs.getBoolean(KEY_WAS_BELOW, false)
                && prefs.getInt(KEY_THRESHOLD, -1) == threshold;
    }

    static void mark(Context context, boolean below, int threshold, int percent) {
        prefs(context).edit()
                .putBoolean(KEY_WAS_BELOW, below)
                .putInt(KEY_THRESHOLD, threshold)
                .putInt(KEY_LAST_PERCENT, percent)
                .apply();
    }

    static int lastPercent(Context context) {
        return prefs(context).getInt(KEY_LAST_PERCENT, -1);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
