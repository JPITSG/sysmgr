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
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_CONNECTED, false) == connected) {
            RemoteLinkAvailabilityStore.recordState(app, connected);
            return;
        }
        prefs.edit()
                .putBoolean(KEY_CONNECTED, connected)
                .apply();
        RemoteLinkAvailabilityStore.recordState(app, connected);
        app.sendBroadcast(new android.content.Intent(ACTION_STATE_CHANGED)
                .setPackage(app.getPackageName()));
    }

    static boolean isConnected(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CONNECTED, false);
    }
}
