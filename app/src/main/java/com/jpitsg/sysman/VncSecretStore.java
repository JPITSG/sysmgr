package com.jpitsg.sysman;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The VNC password, deliberately kept out of {@link Config}.
 *
 * <p>The password stays separate from ordinary configuration reads and edits.
 * A full system backup deliberately includes this private preference store so
 * a restored installation keeps its VNC authentication state.
 */
final class VncSecretStore {
    /** VNC authentication uses a DES key built from the first eight bytes. */
    static final int MAX_PASSWORD_LENGTH = 8;

    private static final String PREFS = "system_manager_vnc_secret";
    private static final String KEY_PASSWORD = "password";

    private VncSecretStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String password(Context context) {
        String value = prefs(context).getString(KEY_PASSWORD, "");
        return value == null ? "" : value;
    }

    static void setPassword(Context context, String password) {
        String next = password == null ? "" : password.trim();
        if (next.equals(password(context))) {
            return;
        }
        prefs(context).edit().putString(KEY_PASSWORD, next).apply();
    }

    static boolean hasPassword(Context context) {
        return !password(context).isEmpty();
    }

    /** True once the password is longer than the protocol can actually use. */
    static boolean isTruncated(Context context) {
        return password(context).length() > MAX_PASSWORD_LENGTH;
    }
}
