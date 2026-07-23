package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Persisted VPN engine state plus a package-local broadcast on every change.
 * Mirrors {@link RemoteLinkStateStore} but carries the richer state the VPN UI
 * and the remote-command handler both read.
 */
final class OpenVpnStateStore {
    static final String ACTION_STATE_CHANGED = "com.jpitsg.sysman.action.OPENVPN_STATE_CHANGED";

    // Detailed engine states (notification/log detail).
    static final String STATE_IDLE = "IDLE";
    static final String STATE_STARTING = "STARTING";
    static final String STATE_CONNECTING = "CONNECTING";
    static final String STATE_AUTH = "AUTH";
    static final String STATE_GET_CONFIG = "GET_CONFIG";
    static final String STATE_ASSIGN_IP = "ASSIGN_IP";
    static final String STATE_CONNECTED = "CONNECTED";
    static final String STATE_RECONNECTING = "RECONNECTING";
    static final String STATE_EXITING = "EXITING";
    static final String STATE_DISCONNECTED = "DISCONNECTED";
    static final String STATE_ERROR = "ERROR";

    // Coarse states used by the pill and remote acks.
    static final String SIMPLE_OFF = "off";
    static final String SIMPLE_CONNECTING = "connecting";
    static final String SIMPLE_CONNECTED = "connected";
    static final String SIMPLE_ERROR = "error";

    private static final String PREFS = "system_manager_vpn_state";
    private static final String KEY_STATE = "state";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_REMOTE = "remote";
    private static final String KEY_RX = "rx";
    private static final String KEY_TX = "tx";
    private static final String KEY_CONNECTED_AT = "connected_at";

    private OpenVpnStateStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void setState(Context context, String state, String lastError) {
        SharedPreferences.Editor editor = prefs(context).edit().putString(KEY_STATE, state);
        if (lastError != null) {
            editor.putString(KEY_LAST_ERROR, lastError);
        }
        if (STATE_CONNECTED.equals(state)) {
            editor.putLong(KEY_CONNECTED_AT, System.currentTimeMillis());
        }
        editor.apply();
        broadcast(context);
    }

    static void setRemote(Context context, String remote) {
        prefs(context).edit().putString(KEY_REMOTE, remote == null ? "" : remote).apply();
    }

    static void setByteCounts(Context context, long rx, long tx) {
        prefs(context).edit().putLong(KEY_RX, rx).putLong(KEY_TX, tx).apply();
    }

    static String state(Context context) {
        return prefs(context).getString(KEY_STATE, STATE_IDLE);
    }

    /** Coarse state for the pill and for remote-command acks. */
    static String simpleState(Context context) {
        String s = state(context);
        if (STATE_CONNECTED.equals(s)) {
            return SIMPLE_CONNECTED;
        }
        if (STATE_ERROR.equals(s)) {
            return SIMPLE_ERROR;
        }
        if (STATE_STARTING.equals(s) || STATE_CONNECTING.equals(s) || STATE_AUTH.equals(s)
                || STATE_GET_CONFIG.equals(s) || STATE_ASSIGN_IP.equals(s) || STATE_RECONNECTING.equals(s)) {
            return SIMPLE_CONNECTING;
        }
        return SIMPLE_OFF;
    }

    static boolean isLiveState(String state) {
        return STATE_STARTING.equals(state) || STATE_CONNECTING.equals(state) || STATE_AUTH.equals(state)
                || STATE_GET_CONFIG.equals(state) || STATE_ASSIGN_IP.equals(state)
                || STATE_CONNECTED.equals(state) || STATE_RECONNECTING.equals(state);
    }

    static String lastError(Context context) {
        return prefs(context).getString(KEY_LAST_ERROR, "");
    }

    static String remote(Context context) {
        return prefs(context).getString(KEY_REMOTE, "");
    }

    static long rxBytes(Context context) {
        return prefs(context).getLong(KEY_RX, 0L);
    }

    static long txBytes(Context context) {
        return prefs(context).getLong(KEY_TX, 0L);
    }

    static long connectedAtMillis(Context context) {
        return prefs(context).getLong(KEY_CONNECTED_AT, 0L);
    }

    private static void broadcast(Context context) {
        context.getApplicationContext().sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                .setPackage(context.getPackageName()));
    }
}
