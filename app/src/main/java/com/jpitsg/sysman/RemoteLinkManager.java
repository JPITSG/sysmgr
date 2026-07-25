package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class RemoteLinkManager {
    static final String ACTION_SYNC = "com.jpitsg.sysman.action.REMOTE_LINK_SYNC";
    static final String ACTION_RESTART = "com.jpitsg.sysman.action.REMOTE_LINK_RESTART";
    static final String EXTRA_REASON = "reason";

    private RemoteLinkManager() {
    }

    static void sync(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (Config.get(app).remoteLinkEnabled()) {
            start(app, ACTION_SYNC, reason);
        } else {
            RemoteLinkStateStore.setConnected(app, false);
            stop(app);
        }
    }

    static void restart(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (Config.get(app).remoteLinkEnabled()) {
            start(app, ACTION_RESTART, reason);
        } else {
            RemoteLinkStateStore.setConnected(app, false);
            stop(app);
        }
    }

    static void flushBackups(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (Config.get(app).remoteLinkEnabled()) {
            start(app, ACTION_SYNC, reason);
        }
    }

    static void flushBackups(Context context) {
        flushBackups(context, "notification-backup");
    }

    static boolean probeBackup(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (!Config.get(app).remoteLinkEnabled()) {
            return false;
        }
        return RemoteLinkService.sendBackupProbeIfRunning(app, reason);
    }

    static boolean ping(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (!Config.get(app).remoteLinkEnabled()) {
            LogStore.append(app, "remote", "Remote Link ping skipped; disabled");
            return false;
        }
        if (!RemoteLinkService.sendPingIfRunning(app, reason)) {
            LogStore.append(app, "remote", "Remote Link ping skipped; service is not running");
            return false;
        }
        return true;
    }

    static void stop(Context context) {
        RemoteLinkStateStore.setConnected(context, false);
        context.getApplicationContext().stopService(new Intent(context.getApplicationContext(), RemoteLinkService.class));
    }

    private static void start(Context context, String action, String reason) {
        Intent intent = new Intent(context, RemoteLinkService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_REASON, reason == null ? "unknown" : reason);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Config.get(context).remoteLinkShowNotification()) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            LogStore.append(context, "remote", "Remote Link start failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
