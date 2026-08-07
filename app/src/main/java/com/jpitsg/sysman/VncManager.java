package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Static facade for the VNC server, mirroring {@link OpenVpnManager}. The UI and
 * the boot hook drive the server through here rather than touching the service
 * directly.
 *
 * <p>Nothing outside the service evaluates the auto-enable rules. The service
 * owns the network watcher and stays up for as long as the feature is armed,
 * because a background network callback cannot start a foreground service on
 * Android 12 and up.
 */
final class VncManager {

    private VncManager() {
    }

    /**
     * Starts or stops the service to match the current settings. The master
     * toggle means <em>armed</em>, not running — a rule that is not satisfied
     * leaves the service up in {@link VncStateStore#STATE_WAITING} rather than
     * turning the user's own switch off behind their back.
     *
     * <p>Leaves a manual hold in place: a settings edit is not a request to
     * undo the user's own Stop.
     */
    static void sync(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (!Config.get(app).vncEnabled()) {
            stop(app, reason);
            return;
        }
        send(app, VncService.ACTION_SYNC, reason);
    }

    /** Explicit user start. Clears a manual hold. */
    static void start(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (!Config.get(app).vncEnabled()) {
            LogStore.append(app, "vnc", "Start ignored; not enabled reason=" + reason);
            return;
        }
        send(app, VncService.ACTION_START, reason);
    }

    /**
     * Explicit user stop while the feature is still armed. The service stays up
     * holding the watcher so the next network change can re-arm it; only
     * turning the master switch off takes the service down.
     */
    static void hold(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (!Config.get(app).vncEnabled() || !VncService.isActive()) {
            stop(app, reason);
            return;
        }
        send(app, VncService.ACTION_HOLD, reason);
    }

    /**
     * Settles the state to OFF before tearing the service down. The service
     * cannot tell a deliberate stop from being killed, so it treats a live
     * state in {@code onDestroy} as an error; writing OFF first is what marks
     * this one as intentional.
     */
    static void stop(Context context, String reason) {
        Context app = context.getApplicationContext();
        LogStore.append(app, "vnc", "Stop requested reason=" + reason);
        VncStateStore.setState(app, VncStateStore.STATE_OFF, "");
        VncStateStore.setListenAddress(app, "");
        if (VncService.isActive()) {
            try {
                app.stopService(new Intent(app, VncService.class));
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Re-arms UI state after a process restart: a live state with no service is stale. */
    static void syncStateOnLaunch(Context context) {
        Context app = context.getApplicationContext();
        String state = VncStateStore.state(app);
        if (VncStateStore.isLiveState(state) && !VncService.isActive()) {
            VncStateStore.setState(app, VncStateStore.STATE_OFF, "Service terminated");
            VncStateStore.setListenAddress(app, "");
        }
    }

    /**
     * The reason the server cannot run right now, or null when nothing is in
     * the way. Checked before the rules so the panel can say what to fix
     * instead of failing silently.
     */
    static String blockingReason(Context context) {
        Context app = context.getApplicationContext();
        if (!VncSecretStore.hasPassword(app)) {
            return "Set a VNC password";
        }
        if (Config.VNC_ENGINE_ACCESSIBILITY.equals(Config.get(app).vncEngine())) {
            // Asks the service itself rather than only checking the settings
            // flag: adding the screenshot capability can leave an older grant
            // in place that no longer covers it, and the two cases need
            // different fixes.
            return SystemManagerAccessibilityService.screenshotBlockedReason(app);
        }
        return null;
    }

    private static void send(Context app, String action, String reason) {
        Intent intent = new Intent(app, VncService.class).setAction(action);
        intent.putExtra(VncService.EXTRA_REASON, reason);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (RuntimeException e) {
            LogStore.append(app, "vnc", "Could not start service: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            VncStateStore.setState(app, VncStateStore.STATE_ERROR,
                    "Service refused to start: " + e.getMessage());
        }
    }
}
