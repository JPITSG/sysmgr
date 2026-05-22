package com.jpitsg.sysman;

import android.os.SystemClock;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class NotificationDeduper {
    private static final int MAX_RECENT = 100;
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, Long> RECENT = new LinkedHashMap<>();

    private NotificationDeduper() {
    }

    static boolean wasRecentlyHandled(String notificationKey, String text, int windowSeconds) {
        if (windowSeconds <= 0) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        long windowMillis = windowSeconds * 1000L;
        String fingerprint = safe(notificationKey) + "|" + Integer.toHexString(safe(text).hashCode());

        synchronized (LOCK) {
            prune(now, windowMillis);
            Long lastSeen = RECENT.get(fingerprint);
            if (lastSeen != null && now - lastSeen < windowMillis) {
                return true;
            }
            RECENT.put(fingerprint, now);
            while (RECENT.size() > MAX_RECENT) {
                Iterator<String> iterator = RECENT.keySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
            return false;
        }
    }

    private static void prune(long now, long windowMillis) {
        Iterator<Map.Entry<String, Long>> iterator = RECENT.entrySet().iterator();
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
