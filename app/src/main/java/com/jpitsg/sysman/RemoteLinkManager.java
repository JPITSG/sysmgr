package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;

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

    static boolean testLatency(Context context) {
        Context app = context.getApplicationContext();
        if (!Config.get(app).remoteLinkEnabled()
                || !RemoteLinkService.startLatencyTestIfRunning(app)) {
            LogStore.append(app, "remote", "Remote Link latency test skipped; link unavailable or test busy");
            return false;
        }
        return true;
    }

    static boolean testThroughput(Context context) {
        Context app = context.getApplicationContext();
        if (!Config.get(app).remoteLinkEnabled()
                || !RemoteLinkService.startThroughputTestIfRunning(app)) {
            LogStore.append(app, "remote", "Remote Link throughput test skipped; link unavailable or test busy");
            return false;
        }
        return true;
    }

    static void stop(Context context) {
        RemoteLinkStateStore.setConnected(context, false);
        AlarmScheduler.cancelRemoteLinkWatchdog(context);
        context.getApplicationContext().stopService(new Intent(context.getApplicationContext(), RemoteLinkService.class));
    }

    private static void start(Context context, String action, String reason) {
        if (!RemoteLinkService.isRunning()) {
            // A process death cannot run Service.onDestroy(), so clear any
            // persisted connection state before starting a replacement.
            RemoteLinkStateStore.setConnected(context, false);
        }
        Intent intent = new Intent(context, RemoteLinkService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_REASON, reason == null ? "unknown" : reason);
        // Armed before the start, not after: the watchdog has to keep ticking
        // even when the start itself is what failed. Every enabled path into the
        // service comes through here, so this is also what keeps it re-armed.
        AlarmScheduler.scheduleRemoteLinkWatchdog(context, reason);
        try {
            // Always a foreground start, even when the notification is hidden:
            // startService() from a background caller (boot, an alarm, a
            // settings change) throws on API 26+, and the socket needs to
            // outlive the app being backgrounded either way.
            context.startForegroundService(intent);
        } catch (RuntimeException e) {
            if (!RemoteLinkService.isRunning()) {
                RemoteLinkStateStore.setConnected(context, false);
            }
            LogStore.append(context, "remote", "Remote Link start failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
