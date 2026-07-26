package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;

/**
 * Static facade for the BLE beacon, mirroring {@link RemoteLinkManager}. The
 * UI, the boot hook, and the settings save points all drive the beacon through
 * here rather than touching {@link BeaconService} directly.
 */
final class BeaconManager {
    static final String ACTION_SYNC = "com.jpitsg.sysman.action.BEACON_SYNC";
    static final String ACTION_REFRESH = "com.jpitsg.sysman.action.BEACON_REFRESH";
    static final String EXTRA_REASON = "reason";

    private BeaconManager() {
    }

    /** Starts the service when the feature is on, stops it when it isn't. */
    static void sync(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (Config.get(app).beaconEnabled()) {
            start(app, ACTION_SYNC, reason);
        } else {
            stop(app);
        }
    }

    /** Asks a running beacon to re-evaluate its rules (settings or battery changed). */
    static void refresh(Context context, String reason) {
        Context app = context.getApplicationContext();
        if (Config.get(app).beaconEnabled()) {
            start(app, ACTION_REFRESH, reason);
        } else {
            stop(app);
        }
    }

    static void stop(Context context) {
        Context app = context.getApplicationContext();
        if (!BeaconService.isActive() && BeaconStateStore.STATE_OFF.equals(BeaconStateStore.state(app))) {
            return;
        }
        try {
            app.stopService(new Intent(app, BeaconService.class));
        } catch (RuntimeException e) {
            LogStore.append(app, "beacon", "Beacon stop failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        BeaconStateStore.setState(app, BeaconStateStore.STATE_OFF, "");
    }

    /**
     * The state that prevents the radio from starting right now, or null when
     * nothing is in the way. Shared by the service and the panel so both agree
     * on why a beacon isn't transmitting.
     */
    static String blockingState(Context context) {
        Context app = context.getApplicationContext();
        if (!BeaconAdvertiser.isSupported(app)) {
            return BeaconStateStore.STATE_UNSUPPORTED;
        }
        if (!PermissionState.hasBluetoothAdvertise(app)) {
            return BeaconStateStore.STATE_NO_PERMISSION;
        }
        if (!BeaconAdvertiser.isBluetoothOn(app)) {
            return BeaconStateStore.STATE_BLUETOOTH_OFF;
        }
        return null;
    }

    private static void start(Context context, String action, String reason) {
        Intent intent = new Intent(context, BeaconService.class)
                .setAction(action)
                .putExtra(EXTRA_REASON, reason == null ? "unknown" : reason);
        try {
            // Always a foreground start: the advertisement lives in this
            // process, so a background-killed process is a silent beacon.
            context.startForegroundService(intent);
        } catch (RuntimeException e) {
            LogStore.append(context, "beacon", "Beacon start failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            BeaconStateStore.setError(context, "Could not start beacon service: " + e.getMessage());
        }
    }
}
