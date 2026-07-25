package com.jpitsg.sysman;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.service.notification.StatusBarNotification;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Builds the JSON record that Notification Backup stores in the outbox and sends
 * to the server. The record is transport-agnostic: the same object is persisted
 * to {@link NotificationBackupStore} and, at send time, tagged with a {@code type}
 * and the install UUID before it goes over the Remote Link.
 */
final class NotificationBackup {
    static final String MESSAGE_TYPE = "notif_backup";
    private static final int MAX_FIELD_CHARS = 4000;

    private NotificationBackup() {
    }

    static JSONObject buildRecord(Context context, StatusBarNotification sbn, NotificationPayload payload,
                                  boolean isSysmgr) throws JSONException {
        Context app = context.getApplicationContext();
        String packageName = sbn.getPackageName() == null ? "" : sbn.getPackageName();
        long shownAtMillis = System.currentTimeMillis();
        long postedAtMillis = sbn.getPostTime();

        android.app.Notification notification = sbn.getNotification();
        String category = notification != null && notification.category != null ? notification.category : "";
        String channel = notification != null && notification.getChannelId() != null ? notification.getChannelId() : "";
        int priority = notification != null ? notification.priority : 0;

        String title = clamp(payload.title);
        String text = clamp(payload.text);
        String key = hashKey(Config.get(app).installUuid(), packageName, sbn.getKey(), postedAtMillis,
                shownAtMillis, title, text);

        JSONObject record = new JSONObject();
        record.put("key", key);
        record.put("shown_at", shownAtMillis);
        record.put("posted_at", postedAtMillis);
        record.put("package", packageName);
        record.put("app_label", appLabel(app, packageName));
        record.put("category", category);
        record.put("channel", channel);
        record.put("priority", priority);
        record.put("is_sysmgr", isSysmgr);
        record.put("title", title);
        record.put("text", text);
        return record;
    }

    static String keyOf(JSONObject record) {
        return record == null ? "" : record.optString("key", "");
    }

    private static String hashKey(String uuid, String packageName, String notificationKey, long postedAtMillis,
                                  long shownAtMillis, String title, String text) {
        String canonical = safe(uuid) + "|" + safe(packageName) + "|" + safe(notificationKey) + "|"
                + postedAtMillis + "|" + shownAtMillis + "|" + safe(title) + "|" + safe(text);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (Exception e) {
            // SHA-256 is always available on Android; fall back to a stable non-crypto key.
            return "h" + Integer.toHexString(canonical.hashCode()) + "-" + shownAtMillis;
        }
    }

    private static String appLabel(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "";
        }
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private static String clamp(String value) {
        String text = value == null ? "" : value;
        if (text.length() <= MAX_FIELD_CHARS) {
            return text;
        }
        return text.substring(0, MAX_FIELD_CHARS - 3) + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
