package com.jpitsg.sysman;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !AlarmScheduler.ACTION_RUN_TASK.equals(intent.getAction())) {
            return;
        }
        String taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_ID);
        if (taskId == null) {
            taskId = TaskIds.GPS_POST;
        }
        String reason = intent.getStringExtra(AlarmScheduler.EXTRA_REASON);
        if (reason == null) {
            reason = "alarm";
        }
        LogStore.append(context, "alarm", "Received alarm for " + taskId + " reason=" + reason);
        if (TaskIds.BATTERY_ALERT_CHECK.equals(taskId)) {
            try {
                BatteryAlertManager.checkAndNotify(context, reason);
            } catch (RuntimeException e) {
                LogStore.append(context, "battery", "Battery alert check crashed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                AlarmScheduler.scheduleBatteryAlertCheck(context, "check-crashed");
            }
            return;
        }
        if (TaskIds.REBOOT_SCHEDULED.equals(taskId)) {
            RebootManager.handleScheduledAlarm(context, reason);
            return;
        }
        if (TaskIds.REBOOT_DELAYED_TEST.equals(taskId)) {
            RebootManager.requestReboot(context, reason);
            return;
        }
        try {
            SystemTaskService.startTask(context, taskId, reason, true);
        } catch (RuntimeException e) {
            LogStore.append(context, "alarm", "Could not start task service: " + e.getMessage());
            if (TaskIds.GPS_POST.equals(taskId)) {
                AlarmScheduler.scheduleGpsPost(context, "service-start-failed");
            }
        }
    }
}
