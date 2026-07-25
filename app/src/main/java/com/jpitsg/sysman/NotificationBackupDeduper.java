package com.jpitsg.sysman;

import android.os.SystemClock;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collapses repeat posts of the same notification within a short window so a
 * chatty app (progress updates, re-posted alerts) is backed up once, not dozens
 * of times. Keyed on package + title + text so identical content de-dupes
 * regardless of the notification's transient key. Dedicated to backup so it
 * never competes with {@link NotificationDeduper}'s high-priority window.
 */
final class NotificationBackupDeduper {
    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_RECENT = 400;
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, Long> RECENT = new LinkedHashMap<>();

    private NotificationBackupDeduper() {
    }

    static boolean wasRecentlyBackedUp(String packageName, String title, String text) {
        long now = SystemClock.elapsedRealtime();
        long windowMillis = WINDOW_SECONDS * 1000L;
        String fingerprint = safe(packageName) + "|" + safe(title) + "|" + Integer.toHexString(safe(text).hashCode());

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
