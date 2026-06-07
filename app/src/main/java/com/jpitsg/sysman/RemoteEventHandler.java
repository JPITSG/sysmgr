package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;

import org.json.JSONObject;

final class RemoteEventHandler {
    private static final String CHANNEL_ID = "remote_notifications";

    private RemoteEventHandler() {
    }

    static void handle(Context context, RemoteWebSocketClient client, String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");
            if ("notification".equals(type)) {
                handleNotification(context, client, json);
                return;
            }
            if ("ping".equals(type)) {
                handlePing(context, client, json);
                return;
            }
            if ("pong".equals(type)) {
                LogStore.append(context, "remote", "Pong received id=" + json.optString("id", ""));
                return;
            }
            LogStore.append(context, "remote", "Received server message: " + shorten(message, 240));
        } catch (Exception e) {
            LogStore.append(context, "remote", "Received malformed server message: " + shorten(message, 240));
        }
    }

    private static void handlePing(Context context, RemoteWebSocketClient client, JSONObject json) {
        String id = json.optString("id", "");
        LogStore.append(context, "remote", "Ping received id=" + id);
        try {
            JSONObject pong = new JSONObject();
            pong.put("type", "pong");
            pong.put("id", id);
            pong.put("ts", System.currentTimeMillis() / 1000L);
            pong.put("source", "android");
            client.sendText(pong.toString());
            LogStore.append(context, "remote", "Pong sent id=" + id);
        } catch (Exception e) {
            LogStore.append(context, "remote", "Pong failed id=" + id + ": " + e.getMessage());
        }
    }

    private static void handleNotification(Context context, RemoteWebSocketClient client, JSONObject json) {
        String id = json.optString("id", "");
        String body = json.optString("message", "");
        String action = json.optString("action", "message");
        String title = json.optString("title", "");
        String imageMime = json.optString("image_mime", "");
        String imageBase64 = json.optString("image_base64", "");
        boolean rebootAction = "reboot".equalsIgnoreCase(action);
        boolean alarmAction = "alarm".equalsIgnoreCase(action);
        if (rebootAction && body.isEmpty()) {
            body = "Remote reboot requested";
        }
        if (alarmAction && body.isEmpty()) {
            body = "Remote alarm requested";
        }
        if (id.isEmpty() || body.isEmpty()) {
            LogStore.append(context, "remote", "Notification message missing id or body");
            return;
        }

        if (alarmAction) {
            handleAlarmAction(context, client, id, json);
            return;
        }

        if (!sendAck(context, client, id, true, "accepted")) {
            return;
        }

        if (rebootAction) {
            RebootManager.handleRemoteCommand(context, id);
            return;
        }

        boolean highPriority = handleHighPrioritySocketAlert(context, id, title, body);
        NotificationHistoryStore.Entry historyEntry =
                NotificationHistoryStore.add(context, "Remote", title, body, "", imageBase64, highPriority);
        showNotification(context, id, action, title, body, imageMime, imageBase64, historyEntry);
    }

    private static void handleAlarmAction(final Context context, final RemoteWebSocketClient client,
                                          final String id, JSONObject json) {
        String tone = json.optString("tone", "");
        int length = Math.max(1, Math.min(300, json.optInt("length", 10)));
        boolean vibrate = json.optBoolean("vibrate", true);
        LogStore.append(context, "remote", "Remote alarm requested id=" + id
                + " tone=" + tone + " length=" + length + " vibrate=" + vibrate);
        HighPriorityAlertPlayer.playRemoteAlarm(
                context,
                tone,
                length,
                vibrate,
                "remote-link:" + id,
                new HighPriorityAlertPlayer.StartCallback() {
                    @Override
                    public void onResult(boolean ok, String reason) {
                        sendAck(context, client, id, ok, reason);
                    }
                });
    }

    private static boolean sendAck(Context context, RemoteWebSocketClient client, String id, boolean ok, String reason) {
        try {
            JSONObject ack = new JSONObject();
            ack.put("type", "ack");
            ack.put("id", id);
            ack.put("ok", ok);
            if (reason != null && !reason.trim().isEmpty()) {
                ack.put("reason", reason);
            }
            client.sendText(ack.toString());
            LogStore.append(context, "remote", "Acked remote notification id=" + id
                    + " ok=" + ok + " reason=" + (reason == null ? "" : reason));
            return true;
        } catch (Exception e) {
            LogStore.append(context, "remote", "Remote notification ack failed id=" + id + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean handleHighPrioritySocketAlert(Context context, String id, String title, String body) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        if (!config.highPriorityRemoteEnabled()) {
            return false;
        }
        String filter = config.highPriorityRemoteTextFilter();
        String haystack = cleanTitle(title) + "\n" + body;
        if (!hasText(filter) || !haystack.contains(filter)) {
            LogStore.append(app, "remote", "Remote Link high-priority alert ignored; text did not contain filter id=" + id);
            return false;
        }
        if (NotificationDeduper.wasRecentlyHandled("remote-socket", haystack, config.highPriorityRemoteDedupeSeconds())) {
            LogStore.append(app, "remote", "Duplicate Remote Link high-priority alert suppressed id=" + id);
            return true;
        }
        LogStore.append(app, "remote", "Remote Link high-priority alert matched id=" + id + " filter=" + filter);
        HighPriorityAlertPlayer.handleNotification(app, "remote-link:" + id);
        return true;
    }

    private static void showNotification(Context context, String id, String action, String title, String body,
                                         String imageMime, String imageBase64,
                                         NotificationHistoryStore.Entry historyEntry) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            LogStore.append(context, "remote", "NotificationManager unavailable for remote notification id=" + id);
            return;
        }
        if (!PermissionState.notificationsEnabled(context)) {
            LogStore.append(context, "remote", "Notifications disabled for remote notification id=" + id);
        }

        ensureChannel(manager);
        Intent openIntent = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0x5200 + Math.abs(id.hashCode() % 1000), openIntent, flags);
        int notificationId = notificationIdFor(id);

        Intent deleteIntent = new Intent(context, NotificationActionReceiver.class)
                .setAction(NotificationActionReceiver.ACTION_DELETE_NOTIFICATION)
                .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(NotificationActionReceiver.EXTRA_HISTORY_ID, historyEntry == null ? "" : historyEntry.id);
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(
                context,
                0x6400 + Math.abs(id.hashCode() % 10000),
                deleteIntent,
                flags);

        Bitmap picture = decodeNotificationImage(imageBase64);
        boolean hasTitle = hasText(title);
        String contentTitle = hasTitle ? cleanTitle(title) : body;
        String contentText = hasTitle ? body : "";
        boolean hadImagePayload = imageBase64 != null && !imageBase64.trim().isEmpty();
        if (hadImagePayload && picture == null) {
            LogStore.append(context, "remote", "Remote notification image decode failed id=" + id + " mime=" + imageMime);
        }
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_system_manager)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(Notification.PRIORITY_HIGH)
                .addAction(R.drawable.ic_stat_system_manager, "Delete", deletePendingIntent);
        if (picture != null) {
            Notification.BigPictureStyle style = new Notification.BigPictureStyle().bigPicture(picture);
            if (hasTitle) {
                style.setBigContentTitle(contentTitle).setSummaryText(body);
            }
            builder.setStyle(style);
        } else {
            builder.setStyle(new Notification.BigTextStyle().bigText(body));
        }
        Notification notification = builder.build();

        try {
            manager.notify(notificationId, notification);
            LogStore.append(context, "remote", "Remote notification shown id=" + id + " action=" + action
                    + " image=" + (picture != null));
        } catch (RuntimeException e) {
            LogStore.append(context, "remote", "Remote notification failed id=" + id + ": " + e.getMessage());
        }
    }

    private static int notificationIdFor(String id) {
        return 0x6200 + Math.abs(id.hashCode() % 10000);
    }

    private static String cleanTitle(String title) {
        return title.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Bitmap decodeNotificationImage(String imageBase64) {
        if (imageBase64 == null || imageBase64.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] data = Base64.decode(imageBase64, Base64.DEFAULT);
            if (data.length == 0) {
                return null;
            }
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void ensureChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Remote notifications",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifications sent through the System Manager Remote Link.");
        manager.createNotificationChannel(channel);
    }

    private static String shorten(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }
}
