package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class NotificationHistoryStore {
    static final String ACTION_CHANGED = "com.jpitsg.sysman.action.NOTIFICATION_HISTORY_CHANGED";
    private static final String HISTORY_FILE = "notification-history.json";
    private static final String HISTORY_TMP_FILE = "notification-history.json.tmp";
    private static final String IMAGE_DIR = "notification-history-images";
    private static final int MAX_ENTRIES = 100;
    private static final int MAX_FIELD_CHARS = 1000;
    private static final Object LOCK = new Object();

    private NotificationHistoryStore() {
    }

    static final class Entry {
        final long timestampMillis;
        final String id;
        final String source;
        final String title;
        final String message;
        final String icon;
        final boolean hasImage;
        final String imageFileName;
        final boolean highPriority;

        Entry(long timestampMillis, String id, String source, String title, String message, String icon,
              boolean hasImage, String imageFileName, boolean highPriority) {
            this.timestampMillis = timestampMillis;
            this.id = id == null ? "" : id;
            this.source = source;
            this.title = title;
            this.message = message;
            this.icon = icon;
            this.hasImage = hasImage;
            this.imageFileName = imageFileName == null ? "" : imageFileName;
            this.highPriority = highPriority;
        }
    }

    static Entry add(Context context, String source, String title, String message, String icon, boolean hasImage) {
        return add(context, source, title, message, icon, hasImage, false);
    }

    static Entry add(Context context, String source, String title, String message, String icon, boolean hasImage, boolean highPriority) {
        return add(context, source, title, message, icon, hasImage, "", highPriority);
    }

    static Entry add(Context context, String source, String title, String message, String icon, String imageBase64) {
        return add(context, source, title, message, icon, imageBase64, false);
    }

    static Entry add(Context context, String source, String title, String message, String icon, String imageBase64, boolean highPriority) {
        Context app = context.getApplicationContext();
        String imageFileName = writeImage(app, imageBase64);
        return add(app, source, title, message, icon, !imageFileName.isEmpty(), imageFileName, highPriority);
    }

    private static Entry add(Context context, String source, String title, String message, String icon,
                            boolean hasImage, String imageFileName, boolean highPriority) {
        Context app = context.getApplicationContext();
        Entry entry = new Entry(
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                clean(source, "Notification"),
                clean(title, ""),
                clean(message, ""),
                clean(icon, ""),
                hasImage,
                cleanImageFileName(imageFileName),
                highPriority);
        synchronized (LOCK) {
            List<Entry> entries = readLocked(app);
            entries.add(0, entry);
            while (entries.size() > MAX_ENTRIES) {
                Entry removed = entries.remove(entries.size() - 1);
                deleteImage(app, removed.imageFileName);
            }
            writeLocked(app, entries);
        }
        broadcastChanged(app);
        return entry;
    }

    static void remove(Context context, Entry target) {
        if (target == null) {
            return;
        }
        removeById(context, target.id);
    }

    static boolean removeById(Context context, String targetId) {
        String cleanId = targetId == null ? "" : targetId.trim();
        if (cleanId.isEmpty()) {
            return false;
        }
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            List<Entry> entries = readLocked(app);
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if (cleanId.equals(entry.id)) {
                    entries.remove(i);
                    deleteImage(app, entry.imageFileName);
                    writeLocked(app, entries);
                    broadcastChanged(app);
                    return true;
                }
            }
        }
        return false;
    }

    static List<Entry> read(Context context, int maxEntries) {
        synchronized (LOCK) {
            List<Entry> entries = readLocked(context.getApplicationContext());
            int limit = maxEntries <= 0 ? entries.size() : Math.min(maxEntries, entries.size());
            return new ArrayList<>(entries.subList(0, limit));
        }
    }

    static int count(Context context) {
        synchronized (LOCK) {
            return readLocked(context.getApplicationContext()).size();
        }
    }

    static void clear(Context context) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            writeLocked(app, new ArrayList<Entry>());
            deleteAllImages(app);
        }
        broadcastChanged(app);
    }

    static File imageFile(Context context, Entry entry) {
        if (entry == null || entry.imageFileName.isEmpty()) {
            return null;
        }
        String name = cleanImageFileName(entry.imageFileName);
        if (name.isEmpty()) {
            return null;
        }
        File file = new File(imageDir(context.getApplicationContext()), name);
        return file.exists() ? file : null;
    }

    private static List<Entry> readLocked(Context context) {
        ArrayList<Entry> entries = new ArrayList<>();
        File file = historyFile(context);
        if (!file.exists()) {
            return entries;
        }

        try {
            StringBuilder raw = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    raw.append(line);
                }
            }
            JSONArray array = new JSONArray(raw.toString());
            for (int i = 0; i < array.length() && entries.size() < MAX_ENTRIES; i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String id = object.optString("id", "");
                String source = object.optString("source", "Notification");
                String title = object.optString("title", "");
                String message = object.optString("message", "");
                String icon = object.optString("icon", "");
                String imageFile = cleanImageFileName(object.optString("image_file", ""));
                boolean highPriority = object.optBoolean("high_priority", "High Priority".equalsIgnoreCase(source));
                long ts = object.optLong("ts", 0L);
                if (id.isEmpty()) {
                    id = legacyId(ts, source, title, message, icon, imageFile);
                }
                entries.add(new Entry(
                        ts,
                        id,
                        source,
                        title,
                        message,
                        icon,
                        object.optBoolean("image", false) || !imageFile.isEmpty(),
                        imageFile,
                        highPriority));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return entries;
    }

    private static void writeLocked(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries) {
                JSONObject object = new JSONObject();
                object.put("ts", entry.timestampMillis);
                object.put("id", entry.id);
                object.put("source", entry.source);
                object.put("title", entry.title);
                object.put("message", entry.message);
                object.put("icon", entry.icon);
                object.put("image", entry.hasImage);
                object.put("image_file", entry.imageFileName);
                object.put("high_priority", entry.highPriority);
                array.put(object);
            }
        } catch (Exception ignored) {
            return;
        }
        writeAtomically(context, historyFile(context), array.toString());
        pruneImages(context, entries);
    }

    private static void writeAtomically(Context context, File target, String content) {
        File temp = new File(context.getFilesDir(), HISTORY_TMP_FILE);
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
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

    private static File historyFile(Context context) {
        return new File(context.getFilesDir(), HISTORY_FILE);
    }

    private static File imageDir(Context context) {
        File dir = new File(context.getFilesDir(), IMAGE_DIR);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    private static String writeImage(Context context, String imageBase64) {
        if (imageBase64 == null || imageBase64.trim().isEmpty()) {
            return "";
        }
        byte[] data;
        try {
            data = Base64.decode(imageBase64, Base64.DEFAULT);
        } catch (RuntimeException e) {
            return "";
        }
        if (data.length == 0) {
            return "";
        }

        String name = "notification-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString() + ".img";
        File dir = imageDir(context);
        File target = new File(dir, name);
        File temp = new File(dir, name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        } catch (Exception ignored) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return "";
        }
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return "";
        }
        return name;
    }

    private static void pruneImages(Context context, List<Entry> entries) {
        Set<String> keep = new HashSet<>();
        for (Entry entry : entries) {
            if (!entry.imageFileName.isEmpty()) {
                keep.add(entry.imageFileName);
            }
        }
        File[] files = imageDir(context).listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && !keep.contains(file.getName())) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static void deleteImage(Context context, String imageFileName) {
        String name = cleanImageFileName(imageFileName);
        if (name.isEmpty()) {
            return;
        }
        File file = new File(imageDir(context), name);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static void deleteAllImages(Context context) {
        File[] files = imageDir(context).listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static void broadcastChanged(Context context) {
        Intent intent = new Intent(ACTION_CHANGED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private static String clean(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            text = fallback;
        }
        if (text.length() > MAX_FIELD_CHARS) {
            return text.substring(0, MAX_FIELD_CHARS - 3) + "...";
        }
        return text;
    }

    private static boolean sameEntry(Entry left, Entry right) {
        if (left == null || right == null) {
            return false;
        }
        if (!left.id.isEmpty() || !right.id.isEmpty()) {
            return Objects.equals(left.id, right.id);
        }
        return left.timestampMillis == right.timestampMillis
                && Objects.equals(left.source, right.source)
                && Objects.equals(left.title, right.title)
                && Objects.equals(left.message, right.message)
                && Objects.equals(left.icon, right.icon)
                && Objects.equals(left.imageFileName, right.imageFileName);
    }

    private static String legacyId(long timestampMillis, String source, String title, String message, String icon, String imageFileName) {
        return "legacy-" + timestampMillis + "-" + Integer.toHexString(Objects.hash(source, title, message, icon, imageFileName));
    }

    private static String cleanImageFileName(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.isEmpty() || text.contains("/") || text.contains("\\") || !text.startsWith("notification-")) {
            return "";
        }
        return text;
    }
}
