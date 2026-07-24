package com.jpitsg.sysman;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class NotificationActionReceiver extends BroadcastReceiver {
    static final String ACTION_DELETE_NOTIFICATION = "com.jpitsg.sysman.action.DELETE_NOTIFICATION";
    static final String ACTION_CLEAR_NOTIFICATION = "com.jpitsg.sysman.action.CLEAR_NOTIFICATION";
    static final String EXTRA_NOTIFICATION_ID = "notification_id";
    static final String EXTRA_HISTORY_ID = "history_id";
    private static final int MISSING_NOTIFICATION_ID = Integer.MIN_VALUE;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        boolean delete = ACTION_DELETE_NOTIFICATION.equals(action);
        boolean clear = ACTION_CLEAR_NOTIFICATION.equals(action);
        if (!delete && !clear) {
            return;
        }

        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, MISSING_NOTIFICATION_ID);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && notificationId != MISSING_NOTIFICATION_ID) {
            manager.cancel(notificationId);
        }

        if (clear) {
            // Dismiss from the shade but keep the entry in history.
            LogStore.append(context, "notification", "Notification clear action notification_id="
                    + notificationId + " (kept in history)");
            return;
        }

        String historyId = intent.getStringExtra(EXTRA_HISTORY_ID);
        boolean removed = NotificationHistoryStore.removeById(context, historyId);
        LogStore.append(context, "notification", "Notification delete action notification_id="
                + notificationId + " history_id=" + (historyId == null ? "" : historyId)
                + " history_removed=" + removed);
    }
}
