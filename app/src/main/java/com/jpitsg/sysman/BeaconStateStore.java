package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Persisted beacon engine state plus a package-local broadcast on every change.
 * Mirrors {@link OpenVpnStateStore}: the panel reads it to render the status
 * card, and {@link BeaconService} is the only writer.
 */
final class BeaconStateStore {
    static final String ACTION_STATE_CHANGED = "com.jpitsg.sysman.action.BEACON_STATE_CHANGED";

    /** Feature switched off. */
    static final String STATE_OFF = "OFF";
    /** Radio is transmitting. */
    static final String STATE_ADVERTISING = "ADVERTISING";
    /** A rule matched and asked for silence (interval 0). */
    static final String STATE_PAUSED = "PAUSED";
    /** Enabled, but no rule covers the current battery level. */
    static final String STATE_NO_RULE = "NO_RULE";
    static final String STATE_BLUETOOTH_OFF = "BLUETOOTH_OFF";
    static final String STATE_NO_PERMISSION = "NO_PERMISSION";
    static final String STATE_UNSUPPORTED = "UNSUPPORTED";
    static final String STATE_ERROR = "ERROR";

    private static final String PREFS = "system_manager_beacon_state";
    private static final String KEY_STATE = "state";
    private static final String KEY_DETAIL = "detail";
    private static final String KEY_INTERVAL_SECONDS = "interval_seconds";
    private static final String KEY_REQUESTED_INTERVAL_SECONDS = "requested_interval_seconds";
    private static final String KEY_LEGACY_FALLBACK = "legacy_fallback";
    private static final String KEY_TX_POWER_DBM = "tx_power_dbm";
    private static final String KEY_RULE_ID = "rule_id";
    private static final String KEY_BATTERY_PERCENT = "battery_percent";
    private static final String KEY_ADVERTISING_SINCE = "advertising_since";
    private static final String KEY_UPDATED_AT = "updated_at";

    private BeaconStateStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void setAdvertising(Context context, int intervalSeconds, boolean legacyFallback, int txPowerDbm) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_STATE, STATE_ADVERTISING)
                .putString(KEY_DETAIL, "")
                .putInt(KEY_INTERVAL_SECONDS, Math.max(0, intervalSeconds))
                .putBoolean(KEY_LEGACY_FALLBACK, legacyFallback)
                .putInt(KEY_TX_POWER_DBM, txPowerDbm)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis());
        // Only stamp the start time on the transition in, so re-asserting the
        // same advertisement doesn't reset the "advertising for" counter.
        if (!STATE_ADVERTISING.equals(prefs.getString(KEY_STATE, STATE_OFF))
                || prefs.getLong(KEY_ADVERTISING_SINCE, 0L) <= 0L) {
            editor.putLong(KEY_ADVERTISING_SINCE, System.currentTimeMillis());
        }
        editor.apply();
        broadcast(context);
    }

    static void setState(Context context, String state, String detail) {
        SharedPreferences prefs = prefs(context);
        String cleanDetail = detail == null ? "" : detail;
        // Re-evaluation runs on every battery tick and mostly reaches the same
        // conclusion; skipping the no-op keeps the panel and the service
        // notification from being rebuilt for nothing.
        if (state.equals(prefs.getString(KEY_STATE, null))
                && cleanDetail.equals(prefs.getString(KEY_DETAIL, null))) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_STATE, state)
                .putString(KEY_DETAIL, cleanDetail)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis());
        if (!STATE_ADVERTISING.equals(state)) {
            editor.putInt(KEY_INTERVAL_SECONDS, 0)
                    .putBoolean(KEY_LEGACY_FALLBACK, false)
                    .putLong(KEY_ADVERTISING_SINCE, 0L);
        }
        editor.apply();
        broadcast(context);
    }

    static void setError(Context context, String reason) {
        setState(context, STATE_ERROR, reason);
    }

    /** Records what the rule engine resolved, independent of whether the radio started. */
    static void setRuleContext(Context context, int batteryPercent, String ruleId, int requestedIntervalSeconds) {
        prefs(context).edit()
                .putInt(KEY_BATTERY_PERCENT, batteryPercent)
                .putString(KEY_RULE_ID, ruleId == null ? "" : ruleId)
                .putInt(KEY_REQUESTED_INTERVAL_SECONDS, Math.max(0, requestedIntervalSeconds))
                .apply();
    }

    static String state(Context context) {
        return prefs(context).getString(KEY_STATE, STATE_OFF);
    }

    static String detail(Context context) {
        return prefs(context).getString(KEY_DETAIL, "");
    }

    static int intervalSeconds(Context context) {
        return prefs(context).getInt(KEY_INTERVAL_SECONDS, 0);
    }

    static int requestedIntervalSeconds(Context context) {
        return prefs(context).getInt(KEY_REQUESTED_INTERVAL_SECONDS, 0);
    }

    static boolean legacyFallback(Context context) {
        return prefs(context).getBoolean(KEY_LEGACY_FALLBACK, false);
    }

    static int txPowerDbm(Context context) {
        return prefs(context).getInt(KEY_TX_POWER_DBM, 0);
    }

    static String ruleId(Context context) {
        return prefs(context).getString(KEY_RULE_ID, "");
    }

    static int batteryPercent(Context context) {
        return prefs(context).getInt(KEY_BATTERY_PERCENT, -1);
    }

    static long advertisingSinceMillis(Context context) {
        return prefs(context).getLong(KEY_ADVERTISING_SINCE, 0L);
    }

    static long updatedAtMillis(Context context) {
        return prefs(context).getLong(KEY_UPDATED_AT, 0L);
    }

    static boolean isAdvertising(Context context) {
        return STATE_ADVERTISING.equals(state(context));
    }

    /** Human-readable label for a state, e.g. "Advertising" for ADVERTISING. */
    static String label(String state) {
        if (state == null) {
            return "";
        }
        switch (state) {
            case STATE_OFF: return "Off";
            case STATE_ADVERTISING: return "Advertising";
            case STATE_PAUSED: return "Paused by rule";
            case STATE_NO_RULE: return "No matching rule";
            case STATE_BLUETOOTH_OFF: return "Bluetooth off";
            case STATE_NO_PERMISSION: return "Permission needed";
            case STATE_UNSUPPORTED: return "Not supported";
            case STATE_ERROR: return "Error";
            default: return state;
        }
    }

    private static void broadcast(Context context) {
        context.getApplicationContext().sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                .setPackage(context.getPackageName()));
    }
}
