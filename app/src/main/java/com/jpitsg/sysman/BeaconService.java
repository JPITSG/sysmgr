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
import android.os.IBinder;

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
 * <p>No polling loop and no wake lock: the Bluetooth controller keeps its own
 * transmit schedule, and the service only wakes to re-evaluate when the battery
 * level moves, Bluetooth is toggled, or settings change.
 */
public final class BeaconService extends Service {
    private static final String CHANNEL_ID = "system_manager_beacon";
    private static final int NOTIFICATION_ID = 0x5305;

    private static volatile boolean active;

    /** Re-resolved before every foreground start, so the toggle applies at once. */
    private String channelId = CHANNEL_ID;

    private BeaconAdvertiser advertiser;
    private BroadcastReceiver batteryReceiver;
    private BroadcastReceiver bluetoothReceiver;
    private BroadcastReceiver stateReceiver;

    private int lastBatteryPercent = Integer.MIN_VALUE;
    private String activeIdentity = "";

    static boolean isActive() {
        return active;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        active = true;
        advertiser = new BeaconAdvertiser(this);
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
        } catch (RuntimeException e) {
            LogStore.append(this, "beacon", "Beacon foreground start failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            BeaconStateStore.setError(this, "Foreground service refused: " + e.getMessage());
            stopSelf(startId);
            return START_NOT_STICKY;
        }

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
        active = false;
        unregisterReceivers();
        if (advertiser != null) {
            advertiser.stop();
        }
        activeIdentity = "";
        if (Config.get(this).beaconEnabled()) {
            // Stopped while still enabled (task killed, low memory): leave a
            // truthful state behind rather than a stale "advertising".
            BeaconStateStore.setState(this, BeaconStateStore.STATE_ERROR, "Beacon service stopped");
        } else {
            BeaconStateStore.setState(this, BeaconStateStore.STATE_OFF, "");
        }
        LogStore.append(this, "beacon", "Beacon service stopped");
        super.onDestroy();
    }

    // ---- Rule evaluation ----------------------------------------------------

    private void evaluate(String reason) {
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
        if (advertiser.isRunningAt(requestedInterval) && identity.equals(activeIdentity)) {
            return;
        }

        LogStore.append(this, "beacon", "Applying rule " + rule.displayThreshold() + " → "
                + rule.displayInterval() + " battery=" + battery + "% reason=" + reason);
        activeIdentity = identity;
        advertiser.start(requestedInterval);
    }

    private void stopAdvertising() {
        if (advertiser != null) {
            advertiser.stop();
        }
        activeIdentity = "";
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
        // All three actions are protected system or package-local broadcasts,
        // so they still reach a non-exported receiver.
        registerInternal(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerInternal(bluetoothReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerInternal(stateReceiver, new IntentFilter(BeaconStateStore.ACTION_STATE_CHANGED));
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

        boolean shown = ServiceNotifications.shown(this, ServiceNotifications.BEACON);
        Notification.Builder builder = new Notification.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_stat_system_manager)
                .setContentTitle("Beacon")
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true);
        ServiceNotifications.applyBehavior(builder, shown);
        return builder.build();
    }

    private void resolveChannel() {
        channelId = ServiceNotifications.channel(
                this,
                CHANNEL_ID,
                "Beacon",
                "Keeps the System Manager BLE beacon broadcasting.",
                NotificationManager.IMPORTANCE_MIN,
                ServiceNotifications.shown(this, ServiceNotifications.BEACON));
    }
}
