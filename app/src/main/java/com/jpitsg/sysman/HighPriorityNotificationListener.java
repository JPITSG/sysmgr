package com.jpitsg.sysman;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

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

        if (config.highPriorityEnabled() && config.highPriorityPackage().equals(packageName)) {
            String filter = config.highPriorityTextFilter();
            if (!payload.textContains(filter)) {
                LogStore.append(this, "notification", "Package-based notification ignored; text did not contain filter title="
                        + payload.shortTitle());
                return;
            }

            if (NotificationDeduper.wasRecentlyHandled(sbn.getKey(), payload.text, config.highPriorityDedupeSeconds())) {
                LogStore.append(this, "notification", "Duplicate package-based high-priority update suppressed title="
                        + payload.shortTitle());
                return;
            }

            LogStore.append(this, "notification", "Package-based high-priority match title=" + payload.shortTitle()
                    + " text=" + payload.shortText());
            NotificationHistoryStore.add(this, "High Priority", payload.title, payload.text, packageName, false);
            HighPriorityAlertPlayer.handleNotification(this, "notification:" + packageName);
        }
    }
}
