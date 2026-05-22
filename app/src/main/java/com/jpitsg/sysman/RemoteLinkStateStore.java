package com.jpitsg.sysman;

import android.content.Context;
import android.content.SharedPreferences;

final class RemoteLinkStateStore {
    static final String ACTION_STATE_CHANGED = "com.jpitsg.sysman.action.REMOTE_LINK_STATE_CHANGED";
    private static final String PREFS = "system_manager_remote_link_state";
    private static final String KEY_CONNECTED = "connected";

    private RemoteLinkStateStore() {
    }

    static void setConnected(Context context, boolean connected) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CONNECTED, connected)
                .apply();
        context.getApplicationContext().sendBroadcast(new android.content.Intent(ACTION_STATE_CHANGED)
                .setPackage(context.getPackageName()));
    }

    static boolean isConnected(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CONNECTED, false);
    }
}
