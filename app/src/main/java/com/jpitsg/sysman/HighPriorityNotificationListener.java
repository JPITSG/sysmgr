package com.jpitsg.sysman;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONObject;

public final class HighPriorityNotificationListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        LogStore.append(this, "notification", "Notification listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        LogStore.append(this, "notification", "Notification listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }

        Config config = Config.get(this);
        String packageName = sbn.getPackageName();
        NotificationPayload payload = NotificationTextExtractor.extract(sbn);
        RebootManager.handleNotification(this, packageName, payload, sbn.getKey());

        maybeBackup(config, sbn, packageName, payload);

        if (config.highPriorityEnabled() && config.highPriorityPackage().equals(packageName)) {
            AlertTextFilter filter = config.highPriorityFilter();
            String rejection = filter.rejection(payload.title, payload.text);
            if (rejection != null) {
                LogStore.append(this, "notification", "Package-based notification ignored; " + rejection
                        + " title=" + payload.shortTitle());
                return;
            }

            if (NotificationDeduper.wasRecentlyHandled(sbn.getKey(), payload.text, config.highPriorityDedupeSeconds())) {
                LogStore.append(this, "notification", "Duplicate package-based high-priority update suppressed title="
                        + payload.shortTitle());
                return;
            }

            LogStore.append(this, "notification", "Package-based high-priority match title=" + payload.shortTitle()
                    + " text=" + payload.shortText());
            NotificationHistoryStore.add(this, "High Priority", payload.title, payload.text, packageName, false, true);
            HighPriorityAlertPlayer.handleNotification(this, "notification:" + packageName);
        }
    }

    private void maybeBackup(Config config, StatusBarNotification sbn, String packageName, NotificationPayload payload) {
        if (!config.notificationBackupEnabled()) {
            return;
        }
        boolean isSysmgr = packageName != null && packageName.equals(getPackageName());
        if (isSysmgr && !config.notificationBackupIncludeSysmgr()) {
            return;
        }
        if (!NotificationBackupFilter.isUserFacing(sbn, payload)) {
            return;
        }
        if (NotificationBackupDeduper.wasRecentlyBackedUp(packageName, payload.title, payload.text)) {
            LogStore.append(this, "notification", "Backup skipped duplicate within window title=" + payload.shortTitle());
            return;
        }
        try {
            JSONObject record = NotificationBackup.buildRecord(this, sbn, payload, isSysmgr);
            NotificationBackupStore.enqueue(this, record);
            LogStore.append(this, "notification", "Backup queued package=" + packageName
                    + " title=" + payload.shortTitle());
            RemoteLinkManager.flushBackups(this);
        } catch (Exception e) {
            LogStore.append(this, "notification", "Backup enqueue failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
