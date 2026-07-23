package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.SystemClock;

/**
 * Static facade for the VPN engine, mirroring {@link RemoteLinkManager}. The
 * UI, the boot/settings sync points, and the remote-command handler all drive
 * the VPN through here.
 */
final class OpenVpnManager {

    interface ResultCallback {
        void onResult(boolean ok, String state, String reason);
    }

    private OpenVpnManager() {
    }

    /** Consent intent to launch, or null if consent is already granted. */
    static Intent consentIntentOrNull(Context context) {
        try {
            return VpnService.prepare(context);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void connect(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (!OpenVpnProfileStore.hasProfile(app)) {
            LogStore.append(app, "vpn", "connect ignored; no profile (reason=" + reason + ")");
            return;
        }
        String state = OpenVpnStateStore.state(app);
        if (OpenVpnStateStore.isLiveState(state)) {
            LogStore.append(app, "vpn", "connect ignored; already " + state + " (reason=" + reason + ")");
            return;
        }
        LogStore.append(app, "vpn", "connect requested reason=" + reason);
        Intent intent = new Intent(app, OpenVpnService.class).setAction(OpenVpnService.ACTION_CONNECT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent);
        } else {
            app.startService(intent);
        }
    }

    static void disconnect(Context context, String reason) {
        Context app = context.getApplicationContext();
        LogStore.append(app, "vpn", "disconnect requested reason=" + reason);
        if (OpenVpnService.isActive()) {
            Intent intent = new Intent(app, OpenVpnService.class).setAction(OpenVpnService.ACTION_DISCONNECT);
            try {
                app.startService(intent);
            } catch (RuntimeException e) {
                OpenVpnStateStore.setState(app, OpenVpnStateStore.STATE_DISCONNECTED, null);
            }
        } else {
            OpenVpnStateStore.setState(app, OpenVpnStateStore.STATE_DISCONNECTED, null);
        }
    }

    /** Re-arms UI state after process restart: a live state with no service is stale. */
    static void syncStateOnLaunch(Context context) {
        Context app = context.getApplicationContext();
        String state = OpenVpnStateStore.state(app);
        if (OpenVpnStateStore.isLiveState(state) && !OpenVpnService.isActive()) {
            OpenVpnStateStore.setState(app, OpenVpnStateStore.STATE_DISCONNECTED, "service terminated");
        }
    }

    /** No-op sync hook so callers match the other managers' shape. */
    static void sync(Context context, String reason) {
        syncStateOnLaunch(context);
    }

    /**
     * Runs a remote connect/disconnect/status command and reports the settled
     * verdict via the callback (on a worker thread). Performs its own consent
     * check since no Activity is available from a background command.
     */
    static void executeRemoteCommand(final Context context, final String cmd, final String reason,
                                     final long verdictTimeoutMillis, final ResultCallback callback) {
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                runRemoteCommand(app, cmd, reason, verdictTimeoutMillis, callback);
            }
        }, "SystemManagerVpnRemote").start();
    }

    private static void runRemoteCommand(Context app, String cmd, String reason,
                                         long verdictTimeoutMillis, ResultCallback callback) {
        if ("status".equals(cmd)) {
            callback.onResult(true, OpenVpnStateStore.simpleState(app),
                    OpenVpnStateStore.lastError(app).isEmpty() ? "state report" : OpenVpnStateStore.lastError(app));
            return;
        }
        if ("disconnect".equals(cmd)) {
            disconnect(app, reason);
            waitForState(app, OpenVpnStateStore.SIMPLE_OFF, 8_000L);
            callback.onResult(true, OpenVpnStateStore.simpleState(app), "disconnected");
            return;
        }
        if (!"connect".equals(cmd)) {
            callback.onResult(false, OpenVpnStateStore.simpleState(app), "unknown vpn command: " + cmd);
            return;
        }
        if (!OpenVpnProfileStore.hasProfile(app)) {
            callback.onResult(false, OpenVpnStateStore.simpleState(app), "no VPN profile configured");
            return;
        }
        if (consentIntentOrNull(app) != null) {
            callback.onResult(false, OpenVpnStateStore.simpleState(app),
                    "VPN consent not granted; open the app once");
            return;
        }
        if (OpenVpnStateStore.SIMPLE_CONNECTED.equals(OpenVpnStateStore.simpleState(app))) {
            callback.onResult(true, OpenVpnStateStore.SIMPLE_CONNECTED, "already connected");
            return;
        }
        connect(app, reason);
        String verdict = waitForTerminal(app, verdictTimeoutMillis);
        if (OpenVpnStateStore.SIMPLE_CONNECTED.equals(verdict)) {
            callback.onResult(true, verdict, "connected");
        } else if (OpenVpnStateStore.SIMPLE_ERROR.equals(verdict)) {
            String err = OpenVpnStateStore.lastError(app);
            callback.onResult(false, verdict, err.isEmpty() ? "connect failed" : err);
        } else {
            callback.onResult(false, verdict, "timeout waiting for connect");
        }
    }

    private static String waitForTerminal(Context app, long timeoutMillis) {
        long start = SystemClock.elapsedRealtime();
        long deadline = start + timeoutMillis;
        boolean leftOff = false;
        while (SystemClock.elapsedRealtime() < deadline) {
            String simple = OpenVpnStateStore.simpleState(app);
            if (OpenVpnStateStore.SIMPLE_CONNECTED.equals(simple) || OpenVpnStateStore.SIMPLE_ERROR.equals(simple)) {
                return simple;
            }
            if (!OpenVpnStateStore.SIMPLE_OFF.equals(simple)) {
                leftOff = true;
            } else if (leftOff || SystemClock.elapsedRealtime() - start > 3_000L) {
                // Returned to OFF after starting (or never started within the grace window).
                return OpenVpnStateStore.SIMPLE_OFF;
            }
            sleep(250L);
        }
        return OpenVpnStateStore.simpleState(app);
    }

    private static void waitForState(Context app, String target, long timeoutMillis) {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (target.equals(OpenVpnStateStore.simpleState(app))) {
                return;
            }
            sleep(200L);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
