package com.jpitsg.sysman;

import android.content.Context;
import android.content.SharedPreferences;

final class NetworkStateStore {
    private static final String PREFS = "system_manager_network_state";
    private static final String KEY_WIFI_SIGNATURE = "wifi_signature";

    private NetworkStateStore() {
    }

    static synchronized boolean seedIfMissing(Context context, WifiSnapshot snapshot, String reason) {
        SharedPreferences prefs = prefs(context);
        String current = prefs.getString(KEY_WIFI_SIGNATURE, null);
        if (current != null) {
            return false;
        }
        String signature = signature(snapshot);
        prefs.edit().putString(KEY_WIFI_SIGNATURE, signature).apply();
        LogStore.append(context, "network", "Seeded Wi-Fi state " + signature + " reason=" + reason);
        return true;
    }

    static synchronized boolean updateAndCheckChanged(Context context, WifiSnapshot snapshot, String reason) {
        SharedPreferences prefs = prefs(context);
        String next = signature(snapshot);
        String previous = prefs.getString(KEY_WIFI_SIGNATURE, null);
        prefs.edit().putString(KEY_WIFI_SIGNATURE, next).apply();
        if (previous == null) {
            LogStore.append(context, "network", "Initialized Wi-Fi state " + next + " reason=" + reason);
            return false;
        }
        boolean changed = !previous.equals(next);
        LogStore.append(context, "network", "Wi-Fi state check previous=" + previous + " next=" + next + " changed=" + changed + " reason=" + reason);
        return changed;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String signature(WifiSnapshot snapshot) {
        if (snapshot == null || !snapshot.connected) {
            return "none";
        }
        if (!snapshot.ssid.isEmpty()) {
            return "ssid:" + snapshot.ssid;
        }
        if (!snapshot.bssid.isEmpty()) {
            return "bssid:" + snapshot.bssid;
        }
        return "unknown";
    }
}
