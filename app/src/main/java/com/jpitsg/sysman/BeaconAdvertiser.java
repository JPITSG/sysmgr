package com.jpitsg.sysman;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.UUID;

/**
 * The radio layer: builds an iBeacon advertisement and hands it to the platform
 * BLE advertiser.
 *
 * <p>Timing is delegated to the Bluetooth controller rather than duty-cycled
 * from the CPU. Once an advertising set is started the controller keeps
 * transmitting on its own clock. No wake lock is held here; platform callbacks
 * report interruptions to {@link BeaconService} for recovery. A successful
 * callback is not proof that packets are still reaching a receiver.
 *
 * <p>Interval control needs {@code startAdvertisingSet} (API 26, our
 * minSdkVersion); the legacy {@code startAdvertising} entry point only offers
 * three fixed modes. Legacy <em>PDUs</em> are still used, because that is what
 * ordinary iBeacon receivers scan for. Some controllers refuse legacy PDUs
 * beyond the 10.24 s spec cap, so a failed start is retried clamped and then
 * through the legacy API — {@link #intervalSecondsInUse()} reports what the
 * radio actually settled on.
 */
final class BeaconAdvertiser {
    /** Apple's Bluetooth SIG company identifier, which iBeacon payloads ride on. */
    static final int MANUFACTURER_ID_APPLE = 0x004C;
    /** Advertising intervals are expressed in units of 0.625 ms. */
    private static final int UNITS_PER_SECOND = 1600;
    /** Spec cap for legacy advertising PDUs: 0x4000 units = 10.24 s. */
    private static final int LEGACY_INTERVAL_UNITS_CAP = 16384;
    /** Android returns this placeholder instead of the real adapter address. */
    private static final String REDACTED_ADDRESS = "02:00:00:00:00:00";

    private static volatile Boolean supportedCache;

    private static final int ATTEMPT_REQUESTED = 0;
    private static final int ATTEMPT_CLAMPED = 1;
    private static final int ATTEMPT_LEGACY = 2;
    private static final long START_TIMEOUT_MILLIS = 10_000L;

    interface Listener {
        void onStarted();
        void onFailure(String reason);
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Listener listener;
    private long generation;
    private Runnable startTimeout;
    private BluetoothLeAdvertiser activeAdvertiser;
    private AdvertisingSetCallback setCallback;
    private AdvertiseCallback legacyCallback;
    private AdvertisingSet activeSet;
    private boolean advertising;
    private boolean startPending;
    private int attempt;
    private int requestedSeconds;
    private int intervalUnitsInUse;
    private boolean legacyFallbackInUse;

    BeaconAdvertiser(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    // ---- Capability probing -------------------------------------------------

    static BluetoothAdapter adapter(Context context) {
        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    /**
     * True when this device has BLE and can act as a peripheral (advertise).
     * Hardware capability, so the answer is cached — the panel asks on every
     * status refresh and each miss is a binder round trip.
     */
    static boolean isSupported(Context context) {
        Boolean cached = supportedCache;
        if (cached != null) {
            return cached;
        }
        boolean supported = false;
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            BluetoothAdapter adapter = adapter(context);
            try {
                supported = adapter != null && adapter.isMultipleAdvertisementSupported();
            } catch (RuntimeException e) {
                supported = false;
            }
        }
        supportedCache = supported;
        return supported;
    }

    static boolean isBluetoothOn(Context context) {
        BluetoothAdapter adapter = adapter(context);
        try {
            return adapter != null && adapter.isEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The adapter's own address, or "" when Android withholds it — which is the
     * normal case for an unprivileged app since Android 6. Even when a real
     * value comes back it is the identity address, not the rotating private
     * address actually put on air, so receivers must match on the payload UUID.
     */
    static String localAddress(Context context) {
        BluetoothAdapter adapter = adapter(context);
        if (adapter == null) {
            return "";
        }
        try {
            String address = adapter.getAddress();
            if (address == null) {
                return "";
            }
            String upper = address.trim().toUpperCase(Locale.US);
            return upper.isEmpty() || REDACTED_ADDRESS.equals(upper) ? "" : upper;
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---- Payload ------------------------------------------------------------

    /**
     * The 23 manufacturer-data bytes that follow Apple's company ID in an
     * iBeacon frame: {@code 02 15 | UUID | major | minor | measured power}.
     * With the 2-byte company ID and AD header this is a 27-byte structure,
     * which leaves room for the 3-byte flags inside the 31-byte legacy limit.
     */
    static byte[] iBeaconPayload(UUID uuid, int major, int minor, int measuredPower) {
        ByteBuffer buffer = ByteBuffer.allocate(23).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0x02);
        buffer.put((byte) 0x15);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        buffer.putShort((short) (major & 0xFFFF));
        buffer.putShort((short) (minor & 0xFFFF));
        buffer.put((byte) measuredPower);
        return buffer.array();
    }

    static int intervalUnits(int seconds) {
        long units = (long) Math.max(1, seconds) * UNITS_PER_SECOND;
        if (units < AdvertisingSetParameters.INTERVAL_MIN) {
            return AdvertisingSetParameters.INTERVAL_MIN;
        }
        if (units > AdvertisingSetParameters.INTERVAL_MAX) {
            return AdvertisingSetParameters.INTERVAL_MAX;
        }
        return (int) units;
    }

    /** Rounds an interval in 0.625 ms units back to whole seconds for display. */
    static int unitsToSeconds(int units) {
        return Math.max(1, Math.round(units / (float) UNITS_PER_SECOND));
    }

    // ---- Lifecycle ----------------------------------------------------------

    boolean isAdvertising() {
        return advertising;
    }

    /**
     * True when the radio is already transmitting at this interval, or is on
     * its way there. Starting is asynchronous, so callers that only checked
     * {@link #isAdvertising()} could re-issue a start into the gap and race
     * their own callback.
     */
    boolean isRunningAt(int intervalSeconds) {
        return (advertising || startPending) && requestedSeconds == Math.max(1, intervalSeconds);
    }

    /** Seconds the radio actually settled on, which may be clamped below the request. */
    int intervalSecondsInUse() {
        return intervalUnitsInUse > 0 ? unitsToSeconds(intervalUnitsInUse) : 0;
    }

    boolean isLegacyFallbackInUse() {
        return legacyFallbackInUse;
    }

    /**
     * Starts (or restarts) advertising at the given interval. Safe to call when
     * already advertising — the previous set is stopped first.
     */
    void start(int intervalSeconds) {
        stop();
        requestedSeconds = Math.max(1, intervalSeconds);
        attempt = ATTEMPT_REQUESTED;
        attemptStart();
    }

    void stop() {
        clearAttempt();
        requestedSeconds = 0;
    }

    /** Invalidate callbacks before asking the stack to stop the old advertisement. */
    private void clearAttempt() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        startTimeout = null;
        BluetoothLeAdvertiser oldAdvertiser = activeAdvertiser;
        AdvertisingSetCallback oldSetCallback = setCallback;
        AdvertiseCallback oldLegacyCallback = legacyCallback;
        activeAdvertiser = null;
        setCallback = null;
        legacyCallback = null;
        activeSet = null;
        advertising = false;
        startPending = false;
        intervalUnitsInUse = 0;
        legacyFallbackInUse = false;
        if (oldAdvertiser != null) {
            stopSet(oldAdvertiser, oldSetCallback);
            stopLegacy(oldAdvertiser, oldLegacyCallback);
        }
    }

    private void stopSet(BluetoothLeAdvertiser advertiser, AdvertisingSetCallback callback) {
        if (callback == null) {
            return;
        }
        try {
            advertiser.stopAdvertisingSet(callback);
        } catch (RuntimeException e) {
            LogStore.append(context, "beacon", "Stop advertising set failed: " + e.getMessage());
        }
    }

    private void stopLegacy(BluetoothLeAdvertiser advertiser, AdvertiseCallback callback) {
        if (callback == null) {
            return;
        }
        try {
            advertiser.stopAdvertising(callback);
        } catch (RuntimeException e) {
            LogStore.append(context, "beacon", "Stop legacy advertisement failed: " + e.getMessage());
        }
    }

    private BluetoothLeAdvertiser advertiserOrNull() {
        try {
            BluetoothAdapter adapter = adapter(context);
            return adapter == null || !adapter.isEnabled() ? null : adapter.getBluetoothLeAdvertiser();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void scheduleStartTimeout(final long attemptGeneration) {
        startTimeout = new Runnable() {
            @Override
            public void run() {
                if (generation != attemptGeneration || startTimeout != this || !startPending) {
                    return;
                }
                retryOrFail("Advertising start timed out after 10s");
            }
        };
        handler.postDelayed(startTimeout, START_TIMEOUT_MILLIS);
    }

    private void attemptStart() {
        clearAttempt();
        startPending = true;
        final long attemptGeneration = generation;
        final BluetoothLeAdvertiser advertiser = advertiserOrNull();
        activeAdvertiser = advertiser;
        BeaconStateStore.setState(context, BeaconStateStore.STATE_STARTING, "");
        if (advertiser == null) {
            fail("Bluetooth advertiser unavailable");
            return;
        }
        Config config = Config.get(context);
        AdvertiseData data;
        try {
            data = new AdvertiseData.Builder()
                    // Both must stay off: the device name or an appended TX-power
                    // field overflows the 31-byte legacy PDU and the start fails
                    // with ADVERTISE_FAILED_DATA_TOO_LARGE.
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addManufacturerData(MANUFACTURER_ID_APPLE, iBeaconPayload(
                            config.beaconUuid(),
                            config.beaconMajor(),
                            config.beaconMinor(),
                            config.beaconMeasuredPower()))
                    .build();
        } catch (RuntimeException e) {
            fail("Payload build failed: " + e.getMessage());
            return;
        }

        if (attempt == ATTEMPT_LEGACY) {
            startLegacy(advertiser, config, data, attemptGeneration);
            return;
        }

        int units = intervalUnits(requestedSeconds);
        if (attempt == ATTEMPT_CLAMPED) {
            units = Math.min(units, LEGACY_INTERVAL_UNITS_CAP);
        }
        final int attemptedUnits = units;
        try {
            AdvertisingSetParameters parameters = new AdvertisingSetParameters.Builder()
                    // Legacy PDUs (ADV_NONCONN_IND) so ordinary iBeacon scanners
                    // see us; the extended-advertising API is used only for its
                    // interval control.
                    .setLegacyMode(true)
                    .setConnectable(false)
                    .setScannable(false)
                    .setInterval(attemptedUnits)
                    .setTxPowerLevel(config.beaconTxPowerDbm())
                    .build();
            setCallback = new AdvertisingSetCallback() {
                @Override
                public void onAdvertisingSetStarted(AdvertisingSet set, int txPower, int status) {
                    if (generation != attemptGeneration || setCallback != this) {
                        // A timed-out start can still succeed after its replacement has begun.
                        if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                            stopSet(advertiser, this);
                        }
                        return;
                    }
                    if (status != AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                        retryOrFail("advertising set status " + setStatusText(status));
                        return;
                    }
                    if (set == null) {
                        retryOrFail("Advertising start returned no set");
                        return;
                    }
                    activeSet = set;
                    advertising = true;
                    intervalUnitsInUse = attemptedUnits;
                    legacyFallbackInUse = false;
                    succeed(txPower);
                }

                @Override
                public void onAdvertisingSetStopped(AdvertisingSet set) {
                    if (generation != attemptGeneration || setCallback != this) {
                        return;
                    }
                    fail("Advertising set stopped unexpectedly");
                }

                @Override
                public void onAdvertisingEnabled(AdvertisingSet set, boolean enable, int status) {
                    if (generation != attemptGeneration || setCallback != this) {
                        return;
                    }
                    LogStore.append(context, "beacon", "Advertising enabled=" + enable + " status=" + status);
                    if (!enable || status != AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                        String reason = status == AdvertisingSetCallback.ADVERTISE_SUCCESS
                                ? "Bluetooth disabled advertising"
                                : "Advertising update failed: " + setStatusText(status);
                        if (startPending) {
                            retryOrFail(reason);
                        } else {
                            fail(reason);
                        }
                    }
                }
            };
            scheduleStartTimeout(attemptGeneration);
            advertiser.startAdvertisingSet(parameters, data, null, null, null, setCallback);
        } catch (SecurityException e) {
            fail("Bluetooth advertise permission denied");
        } catch (RuntimeException e) {
            retryOrFail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void startLegacy(final BluetoothLeAdvertiser advertiser, Config config, AdvertiseData data,
                             final long attemptGeneration) {
        try {
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(legacyModeFor(requestedSeconds))
                    .setTxPowerLevel(legacyTxPowerFor(config.beaconTxPowerDbm()))
                    .setConnectable(false)
                    .setTimeout(0)
                    .build();
            legacyCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    if (generation != attemptGeneration || legacyCallback != this) {
                        stopLegacy(advertiser, this);
                        return;
                    }
                    advertising = true;
                    legacyFallbackInUse = true;
                    intervalUnitsInUse = legacyIntervalUnitsFor(requestedSeconds);
                    succeed(Integer.MIN_VALUE);
                }

                @Override
                public void onStartFailure(int errorCode) {
                    if (generation != attemptGeneration || legacyCallback != this) {
                        return;
                    }
                    fail("legacy advertising " + legacyErrorText(errorCode));
                }
            };
            scheduleStartTimeout(attemptGeneration);
            advertiser.startAdvertising(settings, data, legacyCallback);
        } catch (SecurityException e) {
            fail("Bluetooth advertise permission denied");
        } catch (RuntimeException e) {
            fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void retryOrFail(final String reason) {
        if (attempt >= ATTEMPT_LEGACY) {
            fail(reason);
            return;
        }
        final int nextAttempt = attempt + 1;
        LogStore.append(context, "beacon", "Advertise attempt " + attempt + " failed (" + reason
                + "); retrying with " + (nextAttempt == ATTEMPT_CLAMPED ? "clamped interval" : "legacy API"));
        clearAttempt();
        startPending = true;
        final long retryGeneration = generation;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (generation != retryGeneration || requestedSeconds <= 0) {
                    return;
                }
                attempt = nextAttempt;
                attemptStart();
            }
        });
    }

    private void succeed(int reportedTxPower) {
        startPending = false;
        if (startTimeout != null) {
            handler.removeCallbacks(startTimeout);
            startTimeout = null;
        }
        int seconds = intervalSecondsInUse();
        BeaconStateStore.setAdvertising(context, seconds, legacyFallbackInUse,
                reportedTxPower == Integer.MIN_VALUE ? Config.get(context).beaconTxPowerDbm() : reportedTxPower);
        LogStore.append(context, "beacon", "Advertising every " + seconds + "s"
                + (legacyFallbackInUse ? " (legacy API)" : "")
                + (seconds != requestedSeconds ? " (requested " + requestedSeconds + "s)" : ""));
        listener.onStarted();
    }

    private void fail(String reason) {
        clearAttempt();
        BeaconStateStore.setError(context, reason);
        LogStore.append(context, "beacon", "Advertising failed: " + reason);
        listener.onFailure(reason);
    }

    // ---- Legacy-API mapping -------------------------------------------------

    private static int legacyModeFor(int seconds) {
        if (seconds <= 1) {
            return AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY;
        }
        if (seconds <= 5) {
            return AdvertiseSettings.ADVERTISE_MODE_BALANCED;
        }
        return AdvertiseSettings.ADVERTISE_MODE_LOW_POWER;
    }

    /** Nominal interval of the legacy mode chosen for this request, in 0.625 ms units. */
    private static int legacyIntervalUnitsFor(int seconds) {
        if (seconds <= 1) {
            return 160;
        }
        if (seconds <= 5) {
            return 400;
        }
        return 1600;
    }

    private static int legacyTxPowerFor(int dbm) {
        if (dbm <= -21) {
            return AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW;
        }
        if (dbm <= -15) {
            return AdvertiseSettings.ADVERTISE_TX_POWER_LOW;
        }
        if (dbm <= -7) {
            return AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
        }
        return AdvertiseSettings.ADVERTISE_TX_POWER_HIGH;
    }

    private static String setStatusText(int status) {
        switch (status) {
            case AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                return "data too large";
            case AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                return "too many advertisers";
            case AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                return "already started";
            case AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                return "internal error";
            case AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return "feature unsupported";
            default:
                return "error " + status;
        }
    }

    private static String legacyErrorText(int errorCode) {
        switch (errorCode) {
            case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
                return "data too large";
            case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                return "too many advertisers";
            case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
                return "already started";
            case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
                return "internal error";
            case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                return "feature unsupported";
            default:
                return "error " + errorCode;
        }
    }
}
