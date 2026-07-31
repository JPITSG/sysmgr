package com.jpitsg.sysman;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.Calendar;
import java.util.List;

final class AlarmScheduler {
    static final String ACTION_RUN_TASK = "com.jpitsg.sysman.action.RUN_TASK";
    static final String EXTRA_TASK_ID = "task_id";
    static final String EXTRA_REASON = "reason";
    // Nothing but opening the app used to restart a Remote Link whose service
    // had died with its process, which left the link down for hours. This is the
    // ceiling on how long that can now go unnoticed.
    private static final long REMOTE_LINK_WATCHDOG_INTERVAL_MILLIS = 15L * 60_000L;

    private AlarmScheduler() {
    }

    static void scheduleGpsPost(Context context, String reason) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        if (!config.isTrackingEnabled()) {
            LogStore.append(app, "alarm", "GPS scheduling skipped; tracking is disabled");
            return;
        }

        int batteryPercent = BatteryReader.batteryPercent(app);
        int intervalMinutes = config.intervalMinutesForBattery(batteryPercent);
        LogStore.append(app, "alarm", "Interval selected battery=" + batteryPercent
                + " threshold=" + config.batteryThresholdPercent()
                + " high=" + config.highBatteryIntervalMinutes()
                + " low=" + config.lowBatteryIntervalMinutes()
                + " selected=" + intervalMinutes
                + " reason=" + reason);
        scheduleGpsPostAfter(app, intervalMinutes * 60_000L, reason);
    }

    static void scheduleGpsPostAfter(Context context, long delayMillis, String reason) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        if (!config.isTrackingEnabled()) {
            LogStore.append(app, "alarm", "GPS scheduling skipped; tracking is disabled");
            return;
        }

        long safeDelayMillis = Math.max(0L, delayMillis);
        long triggerAt = SystemClock.elapsedRealtime() + safeDelayMillis;
        PendingIntent pendingIntent = pendingIntent(app, TaskIds.GPS_POST, "alarm:" + reason);
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            LogStore.append(app, "alarm", "AlarmManager unavailable");
            return;
        }

        try {
            if (config.useExactAlarms() && canScheduleExact(alarmManager)) {
                if (config.allowIdleAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
                    LogStore.append(app, "alarm", "Scheduled exact idle GPS alarm in " + (safeDelayMillis / 1000L) + " sec");
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
                    LogStore.append(app, "alarm", "Scheduled exact GPS alarm in " + (safeDelayMillis / 1000L) + " sec");
                }
            } else {
                if (config.useExactAlarms()) {
                    LogStore.append(app, "alarm", "Exact alarms not allowed; falling back to inexact alarm");
                }
                if (config.allowIdleAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
                }
                LogStore.append(app, "alarm", "Scheduled inexact GPS alarm in " + (safeDelayMillis / 1000L) + " sec");
            }
        } catch (SecurityException e) {
            LogStore.append(app, "alarm", "Alarm scheduling failed: " + e.getMessage());
        }
    }

    static void cancelGpsPost(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(app, TaskIds.GPS_POST, "cancel"));
        }
        LogStore.append(app, "alarm", "Canceled GPS alarm");
    }

    static void scheduleBatteryAlertCheck(Context context, String reason) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        if (!config.batteryAlertEnabled()) {
            LogStore.append(app, "alarm", "Battery alert scheduling skipped; alerts are disabled");
            return;
        }
        scheduleBatteryAlertCheckAfter(app, config.batteryAlertCheckIntervalMinutes() * 60_000L, reason);
    }

    static void scheduleBatteryAlertCheckAfter(Context context, long delayMillis, String reason) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        if (!config.batteryAlertEnabled()) {
            LogStore.append(app, "alarm", "Battery alert scheduling skipped; alerts are disabled");
            return;
        }

        long safeDelayMillis = Math.max(0L, delayMillis);
        long triggerAt = SystemClock.elapsedRealtime() + safeDelayMillis;
        PendingIntent pendingIntent = pendingIntent(app, TaskIds.BATTERY_ALERT_CHECK, "battery:" + reason);
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            LogStore.append(app, "alarm", "AlarmManager unavailable for battery alert");
            return;
        }

        try {
            if (config.batteryAlertUseExactAlarms() && canScheduleExact(alarmManager)) {
                if (config.batteryAlertAllowIdleAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
                    LogStore.append(app, "alarm", "Scheduled exact idle battery alert check in " + (safeDelayMillis / 1000L) + " sec");
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
                    LogStore.append(app, "alarm", "Scheduled exact battery alert check in " + (safeDelayMillis / 1000L) + " sec");
                }
            } else {
                if (config.batteryAlertUseExactAlarms()) {
                    LogStore.append(app, "alarm", "Exact alarms not allowed; falling back to inexact battery alert check");
                }
                if (config.batteryAlertAllowIdleAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
                }
                LogStore.append(app, "alarm", "Scheduled inexact battery alert check in " + (safeDelayMillis / 1000L) + " sec");
            }
        } catch (SecurityException e) {
            LogStore.append(app, "alarm", "Battery alert scheduling failed: " + e.getMessage());
        }
    }

    static void cancelBatteryAlertCheck(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(app, TaskIds.BATTERY_ALERT_CHECK, "cancel"));
        }
        LogStore.append(app, "alarm", "Canceled battery alert check");
    }

    static void scheduleRemoteLinkWatchdog(Context context, String reason) {
        scheduleRemoteLinkWatchdogAfter(context, REMOTE_LINK_WATCHDOG_INTERVAL_MILLIS, reason);
    }

    static void scheduleRemoteLinkWatchdogAfter(Context context, long delayMillis, String reason) {
        Context app = context.getApplicationContext();
        if (!Config.get(app).remoteLinkEnabled()) {
            cancelRemoteLinkWatchdog(app);
            return;
        }
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            LogStore.append(app, "alarm", "AlarmManager unavailable for Remote Link watchdog");
            return;
        }

        long safeDelayMillis = Math.max(0L, delayMillis);
        long triggerAt = SystemClock.elapsedRealtime() + safeDelayMillis;
        PendingIntent pendingIntent = pendingIntent(app, TaskIds.REMOTE_LINK_WATCHDOG, "watchdog:" + reason);
        try {
            // Always allow-while-idle, and never behind the GPS alarm's exact/idle
            // toggles: a link that died overnight is precisely the case this
            // exists for, so Doze must not be able to defer it past morning.
            if (canScheduleExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            }
            LogStore.append(app, "alarm", "Scheduled Remote Link watchdog in "
                    + (safeDelayMillis / 1000L) + " sec reason=" + reason);
        } catch (SecurityException e) {
            LogStore.append(app, "alarm", "Remote Link watchdog scheduling failed: " + e.getMessage());
        }
    }

    static void cancelRemoteLinkWatchdog(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(app, TaskIds.REMOTE_LINK_WATCHDOG, "cancel"));
        }
        LogStore.append(app, "alarm", "Canceled Remote Link watchdog");
    }

    static void scheduleNextVolumeRule(Context context, String reason) {
        Context app = context.getApplicationContext();
        List<Config.VolumeRule> rules = Config.get(app).volumeRules();
        if (rules.isEmpty()) {
            cancelVolumeRule(app);
            LogStore.append(app, "alarm", "Volume rule scheduling skipped; no rules configured");
            return;
        }
        Calendar next = VolumeControlManager.nextRuleTimeAfter(rules, Calendar.getInstance());
        if (next == null) {
            cancelVolumeRule(app);
            LogStore.append(app, "alarm", "Volume rule scheduling skipped; no next rule");
            return;
        }
        scheduleVolumeRuleAt(app, next.getTimeInMillis(), reason);
    }

    static void cancelVolumeRule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(app, TaskIds.VOLUME_RULE_APPLY, "cancel"));
        }
        LogStore.append(app, "alarm", "Canceled volume rule alarm");
    }

    static void scheduleDailyReboot(Context context, String reason) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        if (!config.rebootAutomationEnabled() || !config.rebootScheduleEnabled()) {
            LogStore.append(app, "alarm", "Reboot schedule skipped; automation or schedule disabled");
            return;
        }

        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, config.rebootScheduleHour());
        next.set(Calendar.MINUTE, config.rebootScheduleMinute());
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        scheduleRebootAlarmAt(app, TaskIds.REBOOT_SCHEDULED, next.getTimeInMillis(), "scheduled:" + reason);
    }

    static void scheduleDelayedRebootTest(Context context, long delayMillis, String reason) {
        Context app = context.getApplicationContext();
        long safeDelayMillis = Math.max(0L, delayMillis);
        scheduleRebootAlarmAt(app, TaskIds.REBOOT_DELAYED_TEST, System.currentTimeMillis() + safeDelayMillis, "delayed-test:" + reason);
    }

    static void cancelDailyReboot(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(app, TaskIds.REBOOT_SCHEDULED, "cancel"));
        }
        LogStore.append(app, "alarm", "Canceled scheduled reboot alarm");
    }

    private static void scheduleVolumeRuleAt(Context context, long triggerAtMillis, String reason) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            LogStore.append(app, "alarm", "AlarmManager unavailable for volume rule");
            return;
        }

        PendingIntent pendingIntent = pendingIntent(app, TaskIds.VOLUME_RULE_APPLY, "volume:" + reason);
        long delaySeconds = Math.max(0L, (triggerAtMillis - System.currentTimeMillis()) / 1000L);
        try {
            if (config.useExactAlarms() && canScheduleExact(alarmManager)) {
                if (config.allowIdleAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
                LogStore.append(app, "alarm", "Scheduled exact volume rule alarm in " + delaySeconds + " sec");
            } else {
                if (config.useExactAlarms()) {
                    LogStore.append(app, "alarm", "Exact alarms not allowed; falling back to inexact volume rule alarm");
                }
                if (config.allowIdleAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
                LogStore.append(app, "alarm", "Scheduled inexact volume rule alarm in " + delaySeconds + " sec");
            }
        } catch (SecurityException e) {
            LogStore.append(app, "alarm", "Volume rule alarm scheduling failed: " + e.getMessage());
        }
    }

    private static void scheduleRebootAlarmAt(Context context, String taskId, long triggerAtMillis, String reason) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            LogStore.append(app, "alarm", "AlarmManager unavailable for reboot");
            return;
        }

        PendingIntent pendingIntent = pendingIntent(app, taskId, reason);
        long delaySeconds = Math.max(0L, (triggerAtMillis - System.currentTimeMillis()) / 1000L);
        try {
            if (config.useExactAlarms() && canScheduleExact(alarmManager)) {
                if (config.allowIdleAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
                LogStore.append(app, "alarm", "Scheduled exact reboot alarm task=" + taskId + " in " + delaySeconds + " sec");
            } else {
                if (config.allowIdleAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
                LogStore.append(app, "alarm", "Scheduled inexact reboot alarm task=" + taskId + " in " + delaySeconds + " sec");
            }
        } catch (SecurityException e) {
            LogStore.append(app, "alarm", "Reboot alarm scheduling failed: " + e.getMessage());
        }
    }

    static boolean canScheduleExact(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && canScheduleExact(alarmManager);
    }

    static Intent exactAlarmSettingsIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            return intent;
        }
        return new Intent(Settings.ACTION_APPLICATION_SETTINGS);
    }

    private static boolean canScheduleExact(AlarmManager alarmManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return alarmManager.canScheduleExactAlarms();
        }
        return true;
    }

    private static PendingIntent pendingIntent(Context context, String taskId, String reason) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_RUN_TASK);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        intent.putExtra(EXTRA_REASON, reason);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCode(taskId), intent, flags);
    }

    private static int requestCode(String taskId) {
        return 0x5100 + Math.abs(taskId.hashCode() % 1000);
    }
}
