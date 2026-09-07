package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * Keeps the iBeacon advertisement alive and picks its interval from the
 * battery rules.
 *
 * <p>A foreground service is what makes this work at all: the advertisement is
 * owned by this process, so a process the system reclaims is a beacon that goes
 * quiet. The type is {@code connectedDevice} rather than {@code dataSync}
 * deliberately — from Android 15 a dataSync foreground service is force-stopped
 * after six hours a day, which a beacon meant to run continuously cannot live
 * with.
 *
 * <p>The Bluetooth controller keeps its own transmit schedule. Reported failures
 * are retried with backoff after re-evaluating the current rules. No wake lock
 * is held: callbacks and retries can be delayed while the CPU is asleep.
 */
public final class BeaconService extends Service {
    private static final String CHANNEL_ID = "system_manager_beacon";
    private static final int NOTIFICATION_ID = 0x5305;
    private static final long RETRY_INITIAL_MILLIS = 1_000L;
    private static final long RETRY_MAX_MILLIS = 60_000L;
    private static final long STABLE_ADVERTISING_MILLIS = 60_000L;

    private static volatile boolean active;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable recoveryRunnable;
    private int recoveryFailures;
    private long advertisingSinceElapsed = -1L;
    private boolean foregroundStarted;
    private boolean destroyed;
    private BeaconAdvertiser advertiser;
    private BroadcastReceiver batteryReceiver;
    private BroadcastReceiver bluetoothReceiver;
    private BroadcastReceiver stateReceiver;
    private BroadcastReceiver powerReceiver;

    private int lastBatteryPercent = Integer.MIN_VALUE;
    private String activeIdentity = "";
    private int activeIntervalSeconds;

    static boolean isActive() {
        return active;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        active = true;
        LogStore.append(this, "beacon", "Beacon service created pid=" + android.os.Process.myPid()
                + " previousState=" + BeaconStateStore.state(this));
        advertiser = new BeaconAdvertiser(this, new BeaconAdvertiser.Listener() {
            @Override
            public void onStarted() {
                advertisingSinceElapsed = SystemClock.elapsedRealtime();
                logDeviceState("advertising-started");
            }

            @Override
            public void onFailure(String reason) {
                scheduleRecovery(reason);
            }
        });
        BeaconStateStore.setState(this, Config.get(this).beaconEnabled()
                ? BeaconStateStore.STATE_STARTING : BeaconStateStore.STATE_OFF, "");
        resolveChannel();
        registerReceivers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? BeaconManager.ACTION_SYNC : intent.getAction();
        String reason = intent == null ? "restart" : intent.getStringExtra(BeaconManager.EXTRA_REASON);
        if (reason == null) {
            reason = "unknown";
        }

        try {
            startForegroundBeacon();
            foregroundStarted = true;
        } catch (RuntimeException e) {
            LogStore.append(this, "beacon", "Beacon foreground start failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            BeaconStateStore.setError(this, "Foreground service refused: " + e.getMessage());
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        logDeviceState("service-start reason=" + reason + " systemRestart=" + (intent == null)
                + " startId=" + startId + " flags=" + flags);
        if (BeaconManager.ACTION_REFRESH.equals(action)) {
            // A settings change may have altered the payload, so drop the cached
            // identity and let evaluate() rebuild the advertisement.
            activeIdentity = "";
        }
        evaluate(reason);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        logDeviceState("service-destroy");
        active = false;
        destroyed = true;
        foregroundStarted = false;
        unregisterReceivers();
        stopAdvertising();
        if (Config.get(this).beaconEnabled()) {
            // onDestroy is not guaranteed on process death; onCreate also clears
            // any persisted advertising claim when the service is recreated.
            if (!BeaconStateStore.STATE_ERROR.equals(BeaconStateStore.state(this))) {
                BeaconStateStore.setError(this, "Beacon service stopped");
            }
        } else {
            BeaconStateStore.setState(this, BeaconStateStore.STATE_OFF, "");
        }
        LogStore.append(this, "beacon", "Beacon service stopped");
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        logDeviceState("task-removed");
        super.onTaskRemoved(rootIntent);
    }

    // ---- Rule evaluation ----------------------------------------------------

    private void evaluate(String reason) {
        if (destroyed || !foregroundStarted) {
            return;
        }
        Config config = Config.get(this);
        if (!config.beaconEnabled()) {
            LogStore.append(this, "beacon", "Beacon disabled; stopping reason=" + reason);
            stopAdvertising();
            stopSelf();
            return;
        }

        String blocking = BeaconManager.blockingState(this);
        if (blocking != null) {
            stopAdvertising();
            BeaconStateStore.setState(this, blocking, blockingDetail(blocking));
            LogStore.append(this, "beacon", "Beacon blocked: " + BeaconStateStore.label(blocking)
                    + " reason=" + reason);
            return;
        }

        int battery = BatteryReader.batteryPercent(this);
        lastBatteryPercent = battery;
        Config.BeaconRule rule = config.beaconRuleFor(battery);
        int requestedInterval = rule == null ? Config.BEACON_INTERVAL_OFF : rule.intervalSeconds;
        BeaconStateStore.setRuleContext(this, battery, rule == null ? "" : rule.id, requestedInterval);

        if (rule == null) {
            stopAdvertising();
            BeaconStateStore.setState(this, BeaconStateStore.STATE_NO_RULE,
                    battery < 0 ? "Battery level unknown" : "No rule covers " + battery + "%");
            LogStore.append(this, "beacon", "No beacon rule for battery=" + battery + "% reason=" + reason);
            return;
        }

        if (!rule.broadcasts()) {
            stopAdvertising();
            BeaconStateStore.setState(this, BeaconStateStore.STATE_PAUSED,
                    rule.displayThreshold() + " — don't broadcast");
            LogStore.append(this, "beacon", "Beacon paused by rule " + rule.displayThreshold()
                    + " battery=" + battery + "% reason=" + reason);
            return;
        }

        String identity = identitySignature(config);
        boolean sameRequest = identity.equals(activeIdentity) && activeIntervalSeconds == requestedInterval;
        if (sameRequest && (advertiser.isRunningAt(requestedInterval) || recoveryRunnable != null)) {
            return;
        }
        if (!sameRequest) {
            cancelRecovery();
            recoveryFailures = 0;
        }

        LogStore.append(this, "beacon", "Applying rule " + rule.displayThreshold() + " → "
                + rule.displayInterval() + " battery=" + battery + "% reason=" + reason);
        activeIdentity = identity;
        activeIntervalSeconds = requestedInterval;
        advertisingSinceElapsed = -1L;
        advertiser.start(requestedInterval);
    }

    private void stopAdvertising() {
        cancelRecovery();
        recoveryFailures = 0;
        advertisingSinceElapsed = -1L;
        if (advertiser != null) {
            advertiser.stop();
        }
        activeIdentity = "";
        activeIntervalSeconds = 0;
    }

    private void cancelRecovery() {
        if (recoveryRunnable != null) {
            handler.removeCallbacks(recoveryRunnable);
            recoveryRunnable = null;
        }
    }

    private void scheduleRecovery(final String reason) {
        if (destroyed || !foregroundStarted || recoveryRunnable != null) {
            return;
        }
        if (advertisingSinceElapsed >= 0L
                && SystemClock.elapsedRealtime() - advertisingSinceElapsed >= STABLE_ADVERTISING_MILLIS) {
            recoveryFailures = 0;
        }
        advertisingSinceElapsed = -1L;
        long delayMillis = Math.min(RETRY_MAX_MILLIS,
                RETRY_INITIAL_MILLIS << Math.min(recoveryFailures, 6));
        recoveryFailures = Math.min(recoveryFailures + 1, 7);
        BeaconStateStore.setState(this, BeaconStateStore.STATE_RETRYING,
                reason + "; retry in " + delayMillis / 1000L + "s");
        logDeviceState("recovery-scheduled delayMs=" + delayMillis + " reason=" + reason);
        recoveryRunnable = new Runnable() {
            @Override
            public void run() {
                if (destroyed || recoveryRunnable != this) {
                    return;
                }
                recoveryRunnable = null;
                // Never restart on stale battery rules or revoked permissions.
                evaluate("recovery:" + reason);
            }
        };
        handler.postDelayed(recoveryRunnable, delayMillis);
    }

    private void logDeviceState(String event) {
        try {
            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            LogStore.append(this, "beacon", event
                    + " pid=" + android.os.Process.myPid()
                    + " elapsedMs=" + SystemClock.elapsedRealtime()
                    + " interactive=" + (power == null ? "unknown" : power.isInteractive())
                    + " idle=" + (power == null ? "unknown" : power.isDeviceIdleMode())
                    + " lightIdle=" + (power == null || Build.VERSION.SDK_INT < 33
                            ? "unknown" : power.isDeviceLightIdleMode())
                    + " powerSave=" + (power == null ? "unknown" : power.isPowerSaveMode())
                    + " batteryExempt=" + PermissionState.ignoringBatteryOptimizations(this)
                    + " advertising=" + (advertiser != null && advertiser.isAdvertising())
                    + " state=" + BeaconStateStore.state(this));
        } catch (RuntimeException e) {
            LogStore.append(this, "beacon", event + " device state unavailable: " + e.getMessage());
        }
    }

    /** Everything that, when changed, requires the advertisement to be rebuilt. */
    private static String identitySignature(Config config) {
        return config.beaconUuid()
                + "/" + config.beaconMajor()
                + "/" + config.beaconMinor()
                + "/" + config.beaconMeasuredPower()
                + "/" + config.beaconTxPowerDbm();
    }

    private String blockingDetail(String blocking) {
        if (BeaconStateStore.STATE_UNSUPPORTED.equals(blocking)) {
            return "This device can't advertise over Bluetooth LE";
        }
        if (BeaconStateStore.STATE_NO_PERMISSION.equals(blocking)) {
            return "Grant Nearby devices (Bluetooth advertise)";
        }
        if (BeaconStateStore.STATE_BLUETOOTH_OFF.equals(blocking)) {
            return "Turn Bluetooth on to resume";
        }
        return "";
    }

    // ---- Receivers ----------------------------------------------------------

    private void registerReceivers() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // ACTION_BATTERY_CHANGED also fires for voltage and temperature
                // drift; only a level change can change which rule applies.
                int percent = BatteryReader.batteryPercent(context);
                if (percent == lastBatteryPercent) {
                    return;
                }
                evaluate("battery:" + percent + "%");
            }
        };
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    evaluate("bluetooth-on");
                } else if (state == BluetoothAdapter.STATE_TURNING_OFF
                        || state == BluetoothAdapter.STATE_OFF) {
                    // The stack drops the advertisement itself; clear our handle
                    // so the next start isn't refused as already-running.
                    stopAdvertising();
                    evaluate("bluetooth-off");
                }
            }
        };
        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // The advertiser reports success asynchronously; re-render the
                // notification so it never claims more than the radio is doing.
                updateNotification();
            }
        };
        powerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    logDeviceState("device-event:" + intent.getAction());
                    // Re-evaluate failures on a power transition, without restarting
                    // healthy advertising or bypassing an outstanding retry delay.
                    evaluate("device-event:" + intent.getAction());
                }
            }
        };
        IntentFilter powerFilter = new IntentFilter();
        powerFilter.addAction(Intent.ACTION_SCREEN_ON);
        powerFilter.addAction(Intent.ACTION_SCREEN_OFF);
        powerFilter.addAction(Intent.ACTION_USER_PRESENT);
        powerFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        powerFilter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            powerFilter.addAction(PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED);
        }
        registerInternal(powerReceiver, powerFilter);
        registerFramework(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerFramework(bluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerInternal(stateReceiver, new IntentFilter(BeaconStateStore.ACTION_STATE_CHANGED));
    }

    private void registerFramework(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Bluetooth state comes from a privileged framework app rather
            // than the system UID on some devices, so it needs this flag.
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void registerInternal(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void unregisterReceivers() {
        batteryReceiver = unregister(batteryReceiver);
        bluetoothReceiver = unregister(bluetoothReceiver);
        stateReceiver = unregister(stateReceiver);
        powerReceiver = unregister(powerReceiver);
    }

    private BroadcastReceiver unregister(BroadcastReceiver receiver) {
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    // ---- Foreground notification -------------------------------------------

    private void startForegroundBeacon() {
        resolveChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            manager.notify(NOTIFICATION_ID, buildNotification());
        } catch (RuntimeException ignored) {
        }
    }

    private Notification buildNotification() {
        String state = BeaconStateStore.state(this);
        String text;
        if (BeaconStateStore.STATE_ADVERTISING.equals(state)) {
            text = "Broadcasting " + Config.beaconIntervalDisplay(
                    BeaconStateStore.intervalSeconds(this)).toLowerCase(java.util.Locale.US);
        } else {
            String detail = BeaconStateStore.detail(this);
            text = BeaconStateStore.label(state) + (detail.isEmpty() ? "" : " — " + detail);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, NOTIFICATION_ID, new Intent(this, MainActivity.class), flags);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_system_manager)
                .setContentTitle("Beacon")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true)
                .build();
    }

    private void resolveChannel() {
        ServiceNotifications.ensureChannel(
                this,
                CHANNEL_ID,
                "Beacon",
                "Keeps the System Manager BLE beacon broadcasting.",
                NotificationManager.IMPORTANCE_MIN);
    }
}
