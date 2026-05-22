package com.jpitsg.sysman;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        LogStore.append(context, "boot", "Received " + action);
        Config config = Config.get(context);
        try {
            BatteryAlertManager.checkAndNotify(context, "boot:" + action);
        } catch (RuntimeException e) {
            LogStore.append(context, "battery", "Boot battery alert check crashed: " + e.getMessage());
            AlarmScheduler.scheduleBatteryAlertCheck(context, "boot-check-crashed");
        }
        RebootManager.sync(context, "boot:" + action);
        RemoteLinkManager.sync(context, "boot:" + action);
        if (config.isTrackingEnabled()) {
            NetworkStateStore.seedIfMissing(context, WifiInfoReader.read(context), "boot");
            NetworkMonitorService.sync(context);
            if (config.postOnStartup()) {
                try {
                    SystemTaskService.startTask(context, TaskIds.GPS_POST, "startup:" + action, true);
                } catch (RuntimeException e) {
                    LogStore.append(context, "boot", "Startup GPS post failed to start: " + e.getMessage());
                    AlarmScheduler.scheduleGpsPostAfter(context, 5000L, "startup-fallback");
                }
                return;
            }
            AlarmScheduler.scheduleGpsPost(context, "boot");
        }
    }
}
