package com.jpitsg.sysman;

import android.content.Context;

final class RebootManager {
    private RebootManager() {
    }

    static void sync(Context context, String reason) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        if (config.rebootAutomationEnabled() && config.rebootScheduleEnabled()) {
            AlarmScheduler.scheduleDailyReboot(app, reason);
        } else {
            AlarmScheduler.cancelDailyReboot(app);
        }
    }

    static void scheduleDelayedTest(Context context, String reason) {
        Context app = context.getApplicationContext();
        int seconds = Config.get(app).rebootDelayedTestSeconds();
        AlarmScheduler.scheduleDelayedRebootTest(app, seconds * 1000L, reason);
        LogStore.append(app, "reboot", "Delayed reboot test scheduled in " + seconds + "s");
    }

    static boolean requestReboot(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (!PermissionState.accessibilityServiceEnabled(app)) {
            LogStore.append(app, "reboot", "Reboot automation needs Accessibility service enabled reason=" + reason);
            return false;
        }
        return SystemManagerAccessibilityService.requestReboot(app, reason);
    }

    static void handleNotification(Context context, String packageName, NotificationPayload payload) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        if (!config.rebootAutomationEnabled() || !config.rebootNotificationTriggerEnabled()) {
            return;
        }
        if (!equalsIgnoreCase(config.rebootTriggerPackage(), packageName)) {
            return;
        }
        if (!containsIgnoreCase(payload.title, config.rebootTriggerTitle())) {
            return;
        }
        if (!containsIgnoreCase(payload.text, config.rebootTriggerText())) {
            return;
        }
        if (NotificationDeduper.wasRecentlyHandled(NotificationDeduper.SCOPE_REBOOT, packageName,
                payload.title, payload.text, config.highPriorityDedupeSeconds())) {
            LogStore.append(app, "reboot", "Duplicate reboot notification suppressed title=" + payload.shortTitle());
            return;
        }
        LogStore.append(app, "reboot", "Reboot notification matched title=" + payload.shortTitle()
                + " text=" + payload.shortText());
        NotificationHistoryStore.add(app, "Reboot", payload.title, payload.text, packageName, false);
        requestReboot(app, "notification:" + packageName);
    }

    static void handleRemoteCommand(Context context, String messageId) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        if (!config.rebootAutomationEnabled()) {
            LogStore.append(app, "reboot", "Remote reboot ignored; automation disabled id=" + messageId);
            return;
        }
        if (!config.rebootRemoteTriggerEnabled()) {
            LogStore.append(app, "reboot", "Remote reboot ignored; WSS trigger disabled id=" + messageId);
            return;
        }
        LogStore.append(app, "reboot", "Remote reboot command accepted id=" + messageId);
        NotificationHistoryStore.add(app, "Reboot", "Remote reboot command", "action=reboot id=" + messageId, "remote", false);
        requestReboot(app, "remote:" + messageId);
    }

    static void handleScheduledAlarm(Context context, String reason) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        try {
            if (!config.rebootAutomationEnabled() || !config.rebootScheduleEnabled()) {
                LogStore.append(app, "reboot", "Scheduled reboot ignored; automation or schedule disabled");
                return;
            }

            WifiSnapshot wifi = WifiInfoReader.read(app);
            boolean matches = PatternMatcher.simpleMatch(config.rebootWifiPattern(), wifi.ssid, false);
            if (config.rebootOnlyWhenWifiNotMatching() && matches) {
                LogStore.append(app, "reboot", "Scheduled reboot skipped; Wi-Fi matches pattern="
                        + config.rebootWifiPattern() + " ssid=" + wifi.displaySsid);
                return;
            }

            LogStore.append(app, "reboot", "Scheduled reboot conditions passed reason=" + reason
                    + " ssid=" + wifi.displaySsid + " matches=" + matches);
            requestReboot(app, "scheduled:" + reason);
        } finally {
            sync(app, "after-scheduled-alarm");
        }
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        if (needle == null || needle.trim().isEmpty()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase().contains(needle.trim().toLowerCase());
    }

    private static boolean equalsIgnoreCase(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }
}
