package com.jpitsg.sysman;

import android.os.SystemClock;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collapses repeat high-priority triggers within a configurable window. Keyed on
 * source + title + text rather than the notification's key, so an app that
 * re-posts the same alert under a fresh notification id still de-dupes. Each
 * scope keeps its own history because the windows are configured independently
 * and a short window must not prune a longer one's entries.
 */
final class NotificationDeduper {
    static final String SCOPE_HIGH_PRIORITY = "high-priority";
    static final String SCOPE_REBOOT = "reboot";
    static final String SCOPE_REMOTE = "remote-socket";

    private static final int MAX_RECENT = 100;
    private static final Object LOCK = new Object();
    private static final Map<String, LinkedHashMap<String, Long>> SCOPES = new HashMap<>();

    private NotificationDeduper() {
    }

    static boolean wasRecentlyHandled(String scope, String source, String title, String text, int windowSeconds) {
        if (windowSeconds <= 0) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        long windowMillis = windowSeconds * 1000L;
        String fingerprint = safe(source) + "|" + safe(title) + "|" + Integer.toHexString(safe(text).hashCode());

        synchronized (LOCK) {
            LinkedHashMap<String, Long> recent = SCOPES.get(safe(scope));
            if (recent == null) {
                recent = new LinkedHashMap<>();
                SCOPES.put(safe(scope), recent);
            }
            prune(recent, now, windowMillis);
            Long lastSeen = recent.get(fingerprint);
            if (lastSeen != null && now - lastSeen < windowMillis) {
                return true;
            }
            recent.put(fingerprint, now);
            while (recent.size() > MAX_RECENT) {
                Iterator<String> iterator = recent.keySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
            return false;
        }
    }

    private static void prune(LinkedHashMap<String, Long> recent, long now, long windowMillis) {
        Iterator<Map.Entry<String, Long>> iterator = recent.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > windowMillis) {
                iterator.remove();
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
