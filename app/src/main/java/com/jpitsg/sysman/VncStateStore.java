package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Persisted VNC server state plus a package-local broadcast on every change.
 * Mirrors {@link OpenVpnStateStore}: the panel, the notification and the
 * service all read their view of the world from here rather than from each
 * other.
 */
final class VncStateStore {
    static final String ACTION_STATE_CHANGED = "com.jpitsg.sysman.action.VNC_STATE_CHANGED";

    /** Disabled, or the service is not running. */
    static final String STATE_OFF = "OFF";
    /** Enabled and armed, but the auto-enable rules are not satisfied. */
    static final String STATE_WAITING = "WAITING";
    /** Service up, capture engine coming online. */
    static final String STATE_STARTING = "STARTING";
    /** Socket open, no client yet. */
    static final String STATE_LISTENING = "LISTENING";
    /** A client is attached. */
    static final String STATE_CONNECTED = "CONNECTED";
    /** Screen Capture engine selected but not authorised. */
    static final String STATE_CONSENT = "CONSENT";
    /** A precondition is missing: no password, Accessibility service off. */
    static final String STATE_BLOCKED = "BLOCKED";
    /** Something failed at runtime; see {@link #detail}. */
    static final String STATE_ERROR = "ERROR";

    private static final String PREFS = "system_manager_vnc_state";
    private static final String KEY_STATE = "state";
    private static final String KEY_DETAIL = "detail";
    private static final String KEY_LISTEN_ADDRESS = "listen_address";
    private static final String KEY_CLIENT_ADDRESS = "client_address";
    private static final String KEY_RX = "rx";
    private static final String KEY_TX = "tx";
    private static final String KEY_STARTED_AT = "started_at";
    private static final String KEY_PROBE_RESULT = "probe_result";

    private VncStateStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void setState(Context context, String state, String detail) {
        SharedPreferences prefs = prefs(context);
        String nextDetail = detail == null ? "" : detail;
        if (state.equals(prefs.getString(KEY_STATE, STATE_OFF))
                && nextDetail.equals(prefs.getString(KEY_DETAIL, ""))) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_STATE, state)
                .putString(KEY_DETAIL, nextDetail);
        if (STATE_LISTENING.equals(state) && !isLiveState(prefs.getString(KEY_STATE, STATE_OFF))) {
            editor.putLong(KEY_STARTED_AT, System.currentTimeMillis());
        }
        if (!isLiveState(state)) {
            editor.putString(KEY_CLIENT_ADDRESS, "");
        }
        editor.apply();
        broadcast(context);
    }

    static void setListenAddress(Context context, String address) {
        SharedPreferences prefs = prefs(context);
        String next = address == null ? "" : address;
        if (next.equals(prefs.getString(KEY_LISTEN_ADDRESS, ""))) {
            return;
        }
        prefs.edit().putString(KEY_LISTEN_ADDRESS, next).apply();
        broadcast(context);
    }

    static void setClientAddress(Context context, String address) {
        SharedPreferences prefs = prefs(context);
        String next = address == null ? "" : address;
        if (next.equals(prefs.getString(KEY_CLIENT_ADDRESS, ""))) {
            return;
        }
        prefs.edit().putString(KEY_CLIENT_ADDRESS, next).apply();
        broadcast(context);
    }

    static void setByteCounts(Context context, long rx, long tx) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getLong(KEY_RX, 0L) == rx && prefs.getLong(KEY_TX, 0L) == tx) {
            return;
        }
        prefs.edit().putLong(KEY_RX, rx).putLong(KEY_TX, tx).apply();
        broadcast(context);
    }

    static String state(Context context) {
        return prefs(context).getString(KEY_STATE, STATE_OFF);
    }

    static String detail(Context context) {
        return prefs(context).getString(KEY_DETAIL, "");
    }

    static String listenAddress(Context context) {
        return prefs(context).getString(KEY_LISTEN_ADDRESS, "");
    }

    static String clientAddress(Context context) {
        return prefs(context).getString(KEY_CLIENT_ADDRESS, "");
    }

    static long rxBytes(Context context) {
        return prefs(context).getLong(KEY_RX, 0L);
    }

    static long txBytes(Context context) {
        return prefs(context).getLong(KEY_TX, 0L);
    }

    static long startedAtMillis(Context context) {
        return prefs(context).getLong(KEY_STARTED_AT, 0L);
    }

    /** Last capture-probe summary, kept so the panel can show it after a rebuild. */
    static void setProbeResult(Context context, String summary) {
        prefs(context).edit().putString(KEY_PROBE_RESULT, summary == null ? "" : summary).apply();
        broadcast(context);
    }

    static String probeResult(Context context) {
        return prefs(context).getString(KEY_PROBE_RESULT, "");
    }

    /** True while the service is expected to be running. */
    static boolean isLiveState(String state) {
        return STATE_STARTING.equals(state)
                || STATE_LISTENING.equals(state)
                || STATE_CONNECTED.equals(state);
    }

    /** Human-readable label for the status block. */
    static String label(String state) {
        if (state == null) {
            return "";
        }
        switch (state) {
            case STATE_OFF: return "Off";
            case STATE_WAITING: return "Waiting";
            case STATE_STARTING: return "Starting";
            case STATE_LISTENING: return "Listening";
            case STATE_CONNECTED: return "Connected";
            case STATE_CONSENT: return "Needs authorisation";
            case STATE_BLOCKED: return "Blocked";
            case STATE_ERROR: return "Error";
            default: return state;
        }
    }

    /** Short uppercase text for the panel pill. */
    static String pillLabel(String state) {
        if (state == null) {
            return "DISABLED";
        }
        switch (state) {
            case STATE_WAITING: return "WAITING";
            case STATE_STARTING: return "STARTING";
            case STATE_LISTENING: return "LISTENING";
            case STATE_CONNECTED: return "CONNECTED";
            case STATE_CONSENT: return "CONSENT";
            case STATE_BLOCKED: return "BLOCKED";
            case STATE_ERROR: return "ERROR";
            default: return "DISABLED";
        }
    }

    private static void broadcast(Context context) {
        Context app = context.getApplicationContext();
        app.sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(app.getPackageName()));
    }
}
