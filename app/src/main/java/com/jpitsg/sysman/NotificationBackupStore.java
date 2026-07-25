package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable outbox for Notification Backup. Records are persisted first, then sent
 * over the Remote Link, then removed once the server acknowledges them — so a
 * process death or reboot never loses a queued notification. Oldest-first FIFO,
 * capped at {@link #MAX_ITEMS}; when full, the oldest queued record is dropped
 * (logged, never silently) to bound on-device storage.
 */
final class NotificationBackupStore {
    static final String ACTION_CHANGED = "com.jpitsg.sysman.action.NOTIFICATION_BACKUP_CHANGED";
    private static final String OUTBOX_FILE = "notification-backup-outbox.json";
    private static final String OUTBOX_TMP_FILE = "notification-backup-outbox.json.tmp";
    private static final int MAX_ITEMS = 1000;
    private static final Object LOCK = new Object();

    private NotificationBackupStore() {
    }

    static void enqueue(Context context, JSONObject record) {
        if (record == null || NotificationBackup.keyOf(record).isEmpty()) {
            return;
        }
        Context app = context.getApplicationContext();
        int dropped = 0;
        synchronized (LOCK) {
            List<JSONObject> entries = readLocked(app);
            entries.add(record);
            while (entries.size() > MAX_ITEMS) {
                entries.remove(0);
                dropped++;
            }
            writeLocked(app, entries);
        }
        if (dropped > 0) {
            LogStore.append(app, "notification", "Backup outbox full; dropped " + dropped
                    + " oldest queued notification(s) (cap=" + MAX_ITEMS + ")");
        }
        broadcastChanged(app);
    }

    /** Oldest-first view of up to {@code max} queued records (0 = all). */
    static List<JSONObject> peek(Context context, int max) {
        synchronized (LOCK) {
            List<JSONObject> entries = readLocked(context.getApplicationContext());
            if (max <= 0 || max >= entries.size()) {
                return entries;
            }
            return new ArrayList<>(entries.subList(0, max));
        }
    }

    static boolean remove(Context context, String key) {
        String target = key == null ? "" : key.trim();
        if (target.isEmpty()) {
            return false;
        }
        Context app = context.getApplicationContext();
        boolean removed = false;
        synchronized (LOCK) {
            List<JSONObject> entries = readLocked(app);
            for (int i = 0; i < entries.size(); i++) {
                if (target.equals(NotificationBackup.keyOf(entries.get(i)))) {
                    entries.remove(i);
                    removed = true;
                    break;
                }
            }
            if (removed) {
                writeLocked(app, entries);
            }
        }
        if (removed) {
            broadcastChanged(app);
        }
        return removed;
    }

    static int count(Context context) {
        synchronized (LOCK) {
            return readLocked(context.getApplicationContext()).size();
        }
    }

    static void clear(Context context) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            writeLocked(app, new ArrayList<JSONObject>());
        }
        broadcastChanged(app);
    }

    private static List<JSONObject> readLocked(Context context) {
        ArrayList<JSONObject> entries = new ArrayList<>();
        File file = outboxFile(context);
        if (!file.exists()) {
            return entries;
        }
        try {
            StringBuilder raw = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    raw.append(line);
                }
            }
            JSONArray array = new JSONArray(raw.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null && !NotificationBackup.keyOf(object).isEmpty()) {
                    entries.add(object);
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return entries;
    }

    private static void writeLocked(Context context, List<JSONObject> entries) {
        JSONArray array = new JSONArray();
        for (JSONObject entry : entries) {
            array.put(entry);
        }
        File target = outboxFile(context);
        File temp = new File(context.getFilesDir(), OUTBOX_TMP_FILE);
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(array.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        } catch (Exception ignored) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return;
        }
        if (target.exists() && !target.delete()) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return;
        }
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private static File outboxFile(Context context) {
        return new File(context.getFilesDir(), OUTBOX_FILE);
    }

    private static void broadcastChanged(Context context) {
        Intent intent = new Intent(ACTION_CHANGED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }
}
