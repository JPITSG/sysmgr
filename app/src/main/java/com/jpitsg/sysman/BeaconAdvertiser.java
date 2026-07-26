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
 * transmitting on its own clock, so the interval survives Doze and needs no
 * wake lock — the app process only has to stay alive (hence {@link
 * BeaconService} being a foreground service).
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

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private AdvertisingSetCallback setCallback;
    private AdvertiseCallback legacyCallback;
    private AdvertisingSet activeSet;
    private boolean advertising;
    private boolean startPending;
    private int attempt;
    private int requestedSeconds;
    private int intervalUnitsInUse;
    private boolean legacyFallbackInUse;

    BeaconAdvertiser(Context context) {
        this.context = context.getApplicationContext();
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
        startPending = true;
        attemptStart();
    }

    void stop() {
        BluetoothLeAdvertiser advertiser = advertiserOrNull();
        if (advertiser != null) {
            try {
                if (setCallback != null) {
                    advertiser.stopAdvertisingSet(setCallback);
                }
                if (legacyCallback != null) {
                    advertiser.stopAdvertising(legacyCallback);
                }
            } catch (RuntimeException e) {
                LogStore.append(context, "beacon", "Stop failed: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        setCallback = null;
        legacyCallback = null;
        activeSet = null;
        advertising = false;
        startPending = false;
        requestedSeconds = 0;
        intervalUnitsInUse = 0;
        legacyFallbackInUse = false;
    }

    private BluetoothLeAdvertiser advertiserOrNull() {
        BluetoothAdapter adapter = adapter(context);
        if (adapter == null || !adapter.isEnabled()) {
            return null;
        }
        try {
            return adapter.getBluetoothLeAdvertiser();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void attemptStart() {
        BluetoothLeAdvertiser advertiser = advertiserOrNull();
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
            startLegacy(advertiser, config, data);
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
                    if (status != AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                        retryOrFail("advertising set status " + setStatusText(status));
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
                    activeSet = null;
                    advertising = false;
                }
            };
            advertiser.startAdvertisingSet(parameters, data, null, null, null, setCallback);
        } catch (SecurityException e) {
            fail("Bluetooth advertise permission denied");
        } catch (RuntimeException e) {
            retryOrFail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void startLegacy(BluetoothLeAdvertiser advertiser, Config config, AdvertiseData data) {
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
                    advertising = true;
                    legacyFallbackInUse = true;
                    intervalUnitsInUse = legacyIntervalUnitsFor(requestedSeconds);
                    succeed(Integer.MIN_VALUE);
                }

                @Override
                public void onStartFailure(int errorCode) {
                    fail("legacy advertising " + legacyErrorText(errorCode));
                }
            };
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
        handler.post(new Runnable() {
            @Override
            public void run() {
                attempt = nextAttempt;
                attemptStart();
            }
        });
    }

    private void succeed(int reportedTxPower) {
        startPending = false;
        int seconds = intervalSecondsInUse();
        BeaconStateStore.setAdvertising(context, seconds, legacyFallbackInUse,
                reportedTxPower == Integer.MIN_VALUE ? Config.get(context).beaconTxPowerDbm() : reportedTxPower);
        LogStore.append(context, "beacon", "Advertising every " + seconds + "s"
                + (legacyFallbackInUse ? " (legacy API)" : "")
                + (seconds != requestedSeconds ? " (requested " + requestedSeconds + "s)" : ""));
    }

    private void fail(String reason) {
        advertising = false;
        // Cleared so a later trigger (battery tick, Bluetooth toggle) is free
        // to retry rather than believing a start is still in flight.
        startPending = false;
        intervalUnitsInUse = 0;
        BeaconStateStore.setError(context, reason);
        LogStore.append(context, "beacon", "Advertising failed: " + reason);
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
