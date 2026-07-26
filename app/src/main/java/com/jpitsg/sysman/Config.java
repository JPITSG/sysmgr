package com.jpitsg.sysman;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

final class Config {
    private static final String PREFS = "system_manager_config";

    private static final Object INSTALL_UUID_LOCK = new Object();
    private static final String KEY_INSTALL_UUID = "install_uuid";
    private static final String KEY_TRACKING_ENABLED = "tracking_enabled";
    private static final String KEY_SERVER_BASE_URL = "server_base_url";
    private static final String KEY_TRACK_PATH = "track_path";
    private static final String KEY_SSID_PATTERN = "ssid_pattern";
    private static final String KEY_INTERVAL_MINUTES = "interval_minutes";
    private static final String KEY_HIGH_BATTERY_INTERVAL_MINUTES = "high_battery_interval_minutes";
    private static final String KEY_LOW_BATTERY_INTERVAL_MINUTES = "low_battery_interval_minutes";
    private static final String KEY_BATTERY_THRESHOLD_PERCENT = "battery_threshold_percent";
    private static final String KEY_LOCATION_TIMEOUT_SECONDS = "location_timeout_seconds";
    private static final String KEY_DESIRED_ACCURACY_METERS = "desired_accuracy_meters";
    private static final String KEY_MAX_CACHED_LOCATION_MINUTES = "max_cached_location_minutes";
    private static final String KEY_HTTP_TIMEOUT_SECONDS = "http_timeout_seconds";
    private static final String KEY_GPS_USE_REMOTE_LINK = "gps_use_remote_link";
    private static final String KEY_FALLBACK_LATITUDE = "fallback_latitude";
    private static final String KEY_FALLBACK_LONGITUDE = "fallback_longitude";
    private static final String KEY_USE_EXACT_ALARMS = "use_exact_alarms";
    private static final String KEY_ALLOW_IDLE_ALARMS = "allow_idle_alarms";
    private static final String KEY_POST_ON_STARTUP = "post_on_startup";
    private static final String KEY_POST_ON_WIFI_CHANGE = "post_on_wifi_change";
    private static final String KEY_SHOW_WIFI_MONITOR_NOTIFICATION = "show_wifi_monitor_notification";
    private static final String KEY_USE_GPS_PROVIDER = "use_gps_provider";
    private static final String KEY_USE_NETWORK_PROVIDER = "use_network_provider";
    // Legacy preference key strings are kept so existing installs preserve their settings.
    private static final String KEY_REQUEST_GPS_ON_SSID_MISMATCH = "gps_only_on_matching_wifi";
    private static final String KEY_USE_FALLBACK_ON_SSID_MATCH = "use_fallback_on_wifi_mismatch";
    private static final String KEY_USE_CACHED_BEFORE_FRESH = "use_cached_before_fresh";
    private static final String KEY_INCLUDE_EXTENDED_FIELDS = "include_extended_fields";
    private static final String KEY_CASE_SENSITIVE_SSID = "case_sensitive_ssid";
    private static final String KEY_LOG_MAX_LINES = "log_max_lines";
    private static final String KEY_LOG_ENABLED = "log_enabled";
    private static final String KEY_CLEAR_NOTIFICATIONS_ON_OPEN = "clear_notifications_on_open";
    private static final String KEY_NOTIFICATION_BACKUP_ENABLED = "notification_backup_enabled";
    private static final String KEY_NOTIFICATION_BACKUP_INCLUDE_SYSMGR = "notification_backup_include_sysmgr";
    private static final String KEY_HIGH_PRIORITY_ENABLED = "high_priority_enabled";
    private static final String KEY_HIGH_PRIORITY_PACKAGE = "high_priority_package";
    private static final String KEY_HIGH_PRIORITY_TEXT_FILTER = "high_priority_text_filter";
    private static final String KEY_HIGH_PRIORITY_REMOTE_ENABLED = "high_priority_remote_enabled";
    private static final String KEY_HIGH_PRIORITY_REMOTE_TEXT_FILTER = "high_priority_remote_text_filter";
    private static final String KEY_HIGH_PRIORITY_REMOTE_DEDUPE_SECONDS = "high_priority_remote_dedupe_seconds";
    private static final String KEY_HIGH_PRIORITY_TONE_TITLE = "high_priority_tone_title";
    private static final String KEY_HIGH_PRIORITY_PLAY_SECONDS = "high_priority_play_seconds";
    private static final String KEY_HIGH_PRIORITY_DEDUPE_SECONDS = "high_priority_dedupe_seconds";
    private static final String KEY_HIGH_PRIORITY_RAISE_ALARM_VOLUME = "high_priority_raise_alarm_volume";
    private static final String KEY_HIGH_PRIORITY_ALARM_VOLUME_PERCENT = "high_priority_alarm_volume_percent";
    private static final String KEY_BATTERY_ALERT_ENABLED = "battery_alert_enabled";
    private static final String KEY_BATTERY_ALERT_THRESHOLD_PERCENT = "battery_alert_threshold_percent";
    private static final String KEY_BATTERY_ALERT_CHECK_INTERVAL_MINUTES = "battery_alert_check_interval_minutes";
    private static final String KEY_BATTERY_ALERT_VIBRATE_SECONDS = "battery_alert_vibrate_seconds";
    private static final String KEY_BATTERY_ALERT_USE_EXACT_ALARMS = "battery_alert_use_exact_alarms";
    private static final String KEY_BATTERY_ALERT_ALLOW_IDLE_ALARMS = "battery_alert_allow_idle_alarms";
    private static final String KEY_VOLUME_RULES = "volume_rules";
    private static final String KEY_REBOOT_AUTOMATION_ENABLED = "reboot_automation_enabled";
    private static final String KEY_REBOOT_NOTIFICATION_TRIGGER_ENABLED = "reboot_notification_trigger_enabled";
    private static final String KEY_REBOOT_REMOTE_TRIGGER_ENABLED = "reboot_remote_trigger_enabled";
    private static final String KEY_REBOOT_SCHEDULE_ENABLED = "reboot_schedule_enabled";
    private static final String KEY_REBOOT_TRIGGER_PACKAGE = "reboot_trigger_package";
    private static final String KEY_REBOOT_TRIGGER_TITLE = "reboot_trigger_title";
    private static final String KEY_REBOOT_TRIGGER_TEXT = "reboot_trigger_text";
    private static final String KEY_REBOOT_SCHEDULE_HOUR = "reboot_schedule_hour";
    private static final String KEY_REBOOT_SCHEDULE_MINUTE = "reboot_schedule_minute";
    private static final String KEY_REBOOT_WIFI_PATTERN = "reboot_wifi_pattern";
    private static final String KEY_REBOOT_ONLY_WHEN_WIFI_NOT_MATCHING = "reboot_only_when_wifi_not_matching";
    private static final String KEY_REBOOT_PIN_SEQUENCE = "reboot_pin_sequence";
    private static final String KEY_REBOOT_DELAYED_TEST_SECONDS = "reboot_delayed_test_seconds";
    private static final String KEY_REBOOT_POWER_DIALOG_WAIT_MS = "reboot_power_dialog_wait_ms";
    private static final String KEY_REBOOT_STEP_WAIT_MS = "reboot_step_wait_ms";
    private static final String KEY_REMOTE_LINK_ENABLED = "remote_link_enabled";
    private static final String KEY_REMOTE_LINK_ENDPOINT = "remote_link_endpoint";
    private static final String KEY_REMOTE_LINK_USERNAME = "remote_link_username";
    private static final String KEY_REMOTE_LINK_PASSWORD = "remote_link_password";
    private static final String KEY_REMOTE_LINK_HEARTBEAT_SECONDS = "remote_link_heartbeat_seconds";
    private static final String KEY_REMOTE_LINK_ACCEPT_ANY_SSL_CERT = "remote_link_accept_any_ssl_cert";
    private static final String KEY_REMOTE_LINK_SHOW_NOTIFICATION = "remote_link_show_notification";
    private static final String KEY_SETTINGS_LAST_EXPORT_MILLIS = "settings_last_export_millis";
    private static final String KEY_VPN_USERNAME = "vpn_username";
    private static final String KEY_VPN_PASSWORD = "vpn_password";
    private static final String KEY_VPN_KEY_PASSPHRASE = "vpn_key_passphrase";
    private static final String KEY_VPN_TAP_STATIC_IP = "vpn_tap_static_ip";
    private static final String KEY_VPN_TAP_NETMASK = "vpn_tap_netmask";
    private static final String KEY_VPN_TAP_GATEWAY = "vpn_tap_gateway";
    private static final String KEY_VPN_REMOTE_COMMAND_ENABLED = "vpn_remote_command_enabled";
    private static final String KEY_BEACON_ENABLED = "beacon_enabled";
    private static final String KEY_BEACON_UUID = "beacon_uuid";
    private static final String KEY_BEACON_MAJOR = "beacon_major";
    private static final String KEY_BEACON_MINOR = "beacon_minor";
    private static final String KEY_BEACON_MEASURED_POWER = "beacon_measured_power";
    private static final String KEY_BEACON_TX_POWER_DBM = "beacon_tx_power_dbm";
    private static final String KEY_BEACON_RULES = "beacon_rules";
    private static final String KEY_BEACON_RULES_SEEDED = "beacon_rules_seeded";

    private static final Object BEACON_UUID_LOCK = new Object();

    private final SharedPreferences prefs;

    static final int VOLUME_UNCHANGED = -1;
    static final int DND_UNCHANGED = 0;
    static final int DND_ENABLE = 1;
    static final int DND_DISABLE = 2;

    /** A beacon rule interval of 0 means "matched, but stay silent". */
    static final int BEACON_INTERVAL_OFF = 0;
    static final int BEACON_TX_POWER_ULTRA_LOW = -21;
    static final int BEACON_TX_POWER_LOW = -15;
    static final int BEACON_TX_POWER_MEDIUM = -7;
    static final int BEACON_TX_POWER_HIGH = 1;

    /**
     * "At or above this battery level, broadcast every N seconds." Rules are
     * held sorted by threshold descending; the first one the current battery
     * level reaches wins, so overlapping rules resolve to the most specific.
     */
    static final class BeaconRule {
        final String id;
        final int minBatteryPercent;
        final int intervalSeconds;

        BeaconRule(String id, int minBatteryPercent, int intervalSeconds) {
            this.id = id;
            this.minBatteryPercent = minBatteryPercent;
            this.intervalSeconds = intervalSeconds;
        }

        boolean matches(int batteryPercent) {
            return batteryPercent >= minBatteryPercent;
        }

        boolean broadcasts() {
            return intervalSeconds > BEACON_INTERVAL_OFF;
        }

        String displayThreshold() {
            return "Battery ≥ " + minBatteryPercent + "%";
        }

        String displayInterval() {
            return beaconIntervalDisplay(intervalSeconds);
        }
    }

    static final class VolumeRule {
        final String id;
        final int hour;
        final int minute;
        final int mediaPercent;
        final int ringPercent;
        final int notificationPercent;
        final int alarmPercent;
        final int dndMode;

        VolumeRule(
                String id,
                int hour,
                int minute,
                int mediaPercent,
                int ringPercent,
                int notificationPercent,
                int alarmPercent,
                int dndMode) {
            this.id = id;
            this.hour = hour;
            this.minute = minute;
            this.mediaPercent = mediaPercent;
            this.ringPercent = ringPercent;
            this.notificationPercent = notificationPercent;
            this.alarmPercent = alarmPercent;
            this.dndMode = dndMode;
        }

        int minuteOfDay() {
            return hour * 60 + minute;
        }

        String displayTime() {
            return String.format(Locale.US, "%02d:%02d", hour, minute);
        }
    }

    private Config(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    static Config get(Context context) {
        return new Config(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    boolean isTrackingEnabled() {
        return prefs.getBoolean(KEY_TRACKING_ENABLED, false);
    }

    void setTrackingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRACKING_ENABLED, enabled).apply();
    }

    String installUuid() {
        synchronized (INSTALL_UUID_LOCK) {
            String existing = clean(prefs.getString(KEY_INSTALL_UUID, ""), "");
            if (isUuid(existing)) {
                return existing;
            }
            String generated = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALL_UUID, generated).commit();
            return generated;
        }
    }

    String serverBaseUrl() {
        return string(KEY_SERVER_BASE_URL, "https://server:1234");
    }

    String trackPath() {
        return string(KEY_TRACK_PATH, "/track.php");
    }

    String ssidPattern() {
        return string(KEY_SSID_PATTERN, "SSID*");
    }

    int intervalMinutes() {
        return lowBatteryIntervalMinutes();
    }

    int highBatteryIntervalMinutes() {
        return intValue(KEY_HIGH_BATTERY_INTERVAL_MINUTES, 3, 1, 1440);
    }

    int lowBatteryIntervalMinutes() {
        return intValue(KEY_LOW_BATTERY_INTERVAL_MINUTES, 6, 1, 1440);
    }

    int batteryThresholdPercent() {
        return intValue(KEY_BATTERY_THRESHOLD_PERCENT, 66, 1, 100);
    }

    int intervalMinutesForBattery(int batteryPercent) {
        if (batteryPercent >= batteryThresholdPercent()) {
            return highBatteryIntervalMinutes();
        }
        return lowBatteryIntervalMinutes();
    }

    int locationTimeoutSeconds() {
        return intValue(KEY_LOCATION_TIMEOUT_SECONDS, 60, 5, 300);
    }

    int desiredAccuracyMeters() {
        return intValue(KEY_DESIRED_ACCURACY_METERS, 50, 1, 10000);
    }

    int maxCachedLocationMinutes() {
        return intValue(KEY_MAX_CACHED_LOCATION_MINUTES, 5, 0, 1440);
    }

    int httpTimeoutSeconds() {
        return intValue(KEY_HTTP_TIMEOUT_SECONDS, 10, 1, 120);
    }

    boolean gpsUseRemoteLink() {
        return prefs.getBoolean(KEY_GPS_USE_REMOTE_LINK, true);
    }

    double fallbackLatitude() {
        return doubleValue(KEY_FALLBACK_LATITUDE, 52.520008d);
    }

    double fallbackLongitude() {
        return doubleValue(KEY_FALLBACK_LONGITUDE, 13.404954d);
    }

    boolean useExactAlarms() {
        return prefs.getBoolean(KEY_USE_EXACT_ALARMS, true);
    }

    boolean allowIdleAlarms() {
        return prefs.getBoolean(KEY_ALLOW_IDLE_ALARMS, true);
    }

    boolean postOnStartup() {
        return prefs.getBoolean(KEY_POST_ON_STARTUP, true);
    }

    boolean postOnWifiChange() {
        return prefs.getBoolean(KEY_POST_ON_WIFI_CHANGE, true);
    }

    boolean showWifiMonitorNotification() {
        return prefs.getBoolean(KEY_SHOW_WIFI_MONITOR_NOTIFICATION, false);
    }

    boolean useGpsProvider() {
        return prefs.getBoolean(KEY_USE_GPS_PROVIDER, true);
    }

    boolean useNetworkProvider() {
        return prefs.getBoolean(KEY_USE_NETWORK_PROVIDER, true);
    }

    boolean requestGpsOnSsidMismatch() {
        return prefs.getBoolean(KEY_REQUEST_GPS_ON_SSID_MISMATCH, true);
    }

    boolean useFallbackOnSsidMatch() {
        return prefs.getBoolean(KEY_USE_FALLBACK_ON_SSID_MATCH, true);
    }

    boolean useCachedBeforeFresh() {
        return prefs.getBoolean(KEY_USE_CACHED_BEFORE_FRESH, false);
    }

    boolean includeExtendedFields() {
        return prefs.getBoolean(KEY_INCLUDE_EXTENDED_FIELDS, false);
    }

    boolean caseSensitiveSsid() {
        return prefs.getBoolean(KEY_CASE_SENSITIVE_SSID, false);
    }

    int logMaxLines() {
        return intValue(KEY_LOG_MAX_LINES, 500, 50, 5000);
    }

    boolean logEnabled() {
        return prefs.getBoolean(KEY_LOG_ENABLED, true);
    }

    boolean clearNotificationsOnOpen() {
        return prefs.getBoolean(KEY_CLEAR_NOTIFICATIONS_ON_OPEN, true);
    }

    boolean notificationBackupEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATION_BACKUP_ENABLED, false);
    }

    boolean notificationBackupIncludeSysmgr() {
        return prefs.getBoolean(KEY_NOTIFICATION_BACKUP_INCLUDE_SYSMGR, false);
    }

    boolean highPriorityEnabled() {
        return prefs.getBoolean(KEY_HIGH_PRIORITY_ENABLED, true);
    }

    String highPriorityPackage() {
        return string(KEY_HIGH_PRIORITY_PACKAGE, "");
    }

    String highPriorityTextFilter() {
        return string(KEY_HIGH_PRIORITY_TEXT_FILTER, "!");
    }

    boolean highPriorityRemoteEnabled() {
        return prefs.getBoolean(KEY_HIGH_PRIORITY_REMOTE_ENABLED, false);
    }

    String highPriorityRemoteTextFilter() {
        return string(KEY_HIGH_PRIORITY_REMOTE_TEXT_FILTER, "!");
    }

    int highPriorityRemoteDedupeSeconds() {
        return intValue(KEY_HIGH_PRIORITY_REMOTE_DEDUPE_SECONDS, 60, 0, 3600);
    }

    String highPriorityToneTitle() {
        return string(KEY_HIGH_PRIORITY_TONE_TITLE, "");
    }

    int highPriorityPlaySeconds() {
        return intValue(KEY_HIGH_PRIORITY_PLAY_SECONDS, 15, 1, 300);
    }

    int highPriorityDedupeSeconds() {
        return intValue(KEY_HIGH_PRIORITY_DEDUPE_SECONDS, 60, 0, 3600);
    }

    boolean highPriorityRaiseAlarmVolume() {
        return prefs.getBoolean(KEY_HIGH_PRIORITY_RAISE_ALARM_VOLUME, true);
    }

    int highPriorityAlarmVolumePercent() {
        return intValue(KEY_HIGH_PRIORITY_ALARM_VOLUME_PERCENT, 100, 1, 100);
    }

    boolean batteryAlertEnabled() {
        return prefs.getBoolean(KEY_BATTERY_ALERT_ENABLED, true);
    }

    int batteryAlertThresholdPercent() {
        return intValue(KEY_BATTERY_ALERT_THRESHOLD_PERCENT, 20, 1, 100);
    }

    int batteryAlertCheckIntervalMinutes() {
        return intValue(KEY_BATTERY_ALERT_CHECK_INTERVAL_MINUTES, 5, 1, 1440);
    }

    int batteryAlertVibrateSeconds() {
        return intValue(KEY_BATTERY_ALERT_VIBRATE_SECONDS, 10, 0, 60);
    }

    boolean batteryAlertUseExactAlarms() {
        return prefs.getBoolean(KEY_BATTERY_ALERT_USE_EXACT_ALARMS, false);
    }

    boolean batteryAlertAllowIdleAlarms() {
        return prefs.getBoolean(KEY_BATTERY_ALERT_ALLOW_IDLE_ALARMS, false);
    }

    List<VolumeRule> volumeRules() {
        List<VolumeRule> rules = new ArrayList<>();
        String raw = prefs.getString(KEY_VOLUME_RULES, "[]");
        if (raw == null || raw.trim().isEmpty()) {
            return rules;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String id = clean(object.optString("id", ""), "");
                if (id.isEmpty()) {
                    id = "rule-" + i;
                }
                int hour = clamp(object.optInt("hour", 0), 0, 23);
                int minute = clamp(object.optInt("minute", 0), 0, 59);
                rules.add(new VolumeRule(
                        id,
                        hour,
                        minute,
                        clampVolume(object.optInt("media", VOLUME_UNCHANGED)),
                        clampVolume(object.optInt("ring", VOLUME_UNCHANGED)),
                        clampVolume(object.optInt("notification", VOLUME_UNCHANGED)),
                        clampVolume(object.optInt("alarm", VOLUME_UNCHANGED)),
                        clampDndMode(object.optInt("dnd", DND_UNCHANGED))));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        sortVolumeRules(rules);
        return rules;
    }

    VolumeRule addVolumeRule(
            String time,
            String mediaPercent,
            String ringPercent,
            String notificationPercent,
            String alarmPercent) {
        int[] parsedTime = parseTime(time);
        return addVolumeRule(
                parsedTime[0],
                parsedTime[1],
                parseVolumePercent(mediaPercent),
                parseVolumePercent(ringPercent),
                parseVolumePercent(notificationPercent),
                parseVolumePercent(alarmPercent),
                DND_UNCHANGED);
    }

    VolumeRule addVolumeRule(
            int hour,
            int minute,
            int mediaPercent,
            int ringPercent,
            int notificationPercent,
            int alarmPercent,
            int dndMode) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Pick a valid 24-hour time");
        }
        VolumeRule rule = new VolumeRule(
                UUID.randomUUID().toString(),
                hour,
                minute,
                clampVolume(mediaPercent),
                clampVolume(ringPercent),
                clampVolume(notificationPercent),
                clampVolume(alarmPercent),
                clampDndMode(dndMode));
        if (rule.mediaPercent == VOLUME_UNCHANGED
                && rule.ringPercent == VOLUME_UNCHANGED
                && rule.notificationPercent == VOLUME_UNCHANGED
                && rule.alarmPercent == VOLUME_UNCHANGED
                && rule.dndMode == DND_UNCHANGED) {
            throw new IllegalArgumentException("Set at least one volume level or DND action");
        }
        List<VolumeRule> rules = volumeRules();
        rules.add(rule);
        saveVolumeRules(rules);
        return rule;
    }

    void removeVolumeRule(String id) {
        String cleanId = id == null ? "" : id.trim();
        if (cleanId.isEmpty()) {
            return;
        }
        List<VolumeRule> kept = new ArrayList<>();
        for (VolumeRule rule : volumeRules()) {
            if (!cleanId.equals(rule.id)) {
                kept.add(rule);
            }
        }
        saveVolumeRules(kept);
    }

    boolean rebootAutomationEnabled() {
        return prefs.getBoolean(KEY_REBOOT_AUTOMATION_ENABLED, false);
    }

    boolean rebootNotificationTriggerEnabled() {
        return prefs.getBoolean(KEY_REBOOT_NOTIFICATION_TRIGGER_ENABLED, false);
    }

    boolean rebootRemoteTriggerEnabled() {
        return prefs.getBoolean(KEY_REBOOT_REMOTE_TRIGGER_ENABLED, false);
    }

    boolean rebootScheduleEnabled() {
        return prefs.getBoolean(KEY_REBOOT_SCHEDULE_ENABLED, false);
    }

    String rebootTriggerPackage() {
        return string(KEY_REBOOT_TRIGGER_PACKAGE, "");
    }

    String rebootTriggerTitle() {
        return string(KEY_REBOOT_TRIGGER_TITLE, "Automation");
    }

    String rebootTriggerText() {
        return string(KEY_REBOOT_TRIGGER_TEXT, "Reboot");
    }

    int rebootScheduleHour() {
        return intValue(KEY_REBOOT_SCHEDULE_HOUR, 4, 0, 23);
    }

    int rebootScheduleMinute() {
        return intValue(KEY_REBOOT_SCHEDULE_MINUTE, 0, 0, 59);
    }

    String rebootWifiPattern() {
        return string(KEY_REBOOT_WIFI_PATTERN, "SSID");
    }

    boolean rebootOnlyWhenWifiNotMatching() {
        return prefs.getBoolean(KEY_REBOOT_ONLY_WHEN_WIFI_NOT_MATCHING, true);
    }

    String rebootPinSequence() {
        return string(KEY_REBOOT_PIN_SEQUENCE, "");
    }

    int rebootDelayedTestSeconds() {
        return intValue(KEY_REBOOT_DELAYED_TEST_SECONDS, 20, 5, 300);
    }

    int rebootPowerDialogWaitMs() {
        return intValue(KEY_REBOOT_POWER_DIALOG_WAIT_MS, 1200, 250, 10000);
    }

    int rebootStepWaitMs() {
        return intValue(KEY_REBOOT_STEP_WAIT_MS, 900, 250, 10000);
    }

    boolean remoteLinkEnabled() {
        return prefs.getBoolean(KEY_REMOTE_LINK_ENABLED, true);
    }

    String remoteLinkEndpoint() {
        return string(KEY_REMOTE_LINK_ENDPOINT, "https://server:1234");
    }

    String remoteLinkUsername() {
        return string(KEY_REMOTE_LINK_USERNAME, "");
    }

    String remoteLinkPassword() {
        return string(KEY_REMOTE_LINK_PASSWORD, "");
    }

    int remoteLinkHeartbeatSeconds() {
        return intValue(KEY_REMOTE_LINK_HEARTBEAT_SECONDS, 60, 10, 3600);
    }

    boolean remoteLinkAcceptAnySslCert() {
        // Secure by default: require a valid certificate. Users on a trusted LAN
        // with a self-signed server can opt in to accepting any certificate.
        return prefs.getBoolean(KEY_REMOTE_LINK_ACCEPT_ANY_SSL_CERT, false);
    }

    boolean remoteLinkShowNotification() {
        return prefs.getBoolean(KEY_REMOTE_LINK_SHOW_NOTIFICATION, false);
    }

    int remoteLinkReconnectSeconds() {
        return 60;
    }

    String vpnUsername() {
        return string(KEY_VPN_USERNAME, "");
    }

    String vpnPassword() {
        return string(KEY_VPN_PASSWORD, "");
    }

    String vpnKeyPassphrase() {
        return string(KEY_VPN_KEY_PASSPHRASE, "");
    }

    String vpnTapStaticIp() {
        return string(KEY_VPN_TAP_STATIC_IP, "");
    }

    String vpnTapNetmask() {
        return string(KEY_VPN_TAP_NETMASK, "255.255.255.0");
    }

    String vpnTapGateway() {
        return string(KEY_VPN_TAP_GATEWAY, "");
    }

    boolean vpnRemoteCommandEnabled() {
        return prefs.getBoolean(KEY_VPN_REMOTE_COMMAND_ENABLED, false);
    }

    // ---- BLE beacon ---------------------------------------------------------

    boolean beaconEnabled() {
        return prefs.getBoolean(KEY_BEACON_ENABLED, false);
    }

    void setBeaconEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BEACON_ENABLED, enabled).apply();
    }

    /**
     * The proximity UUID receivers match on. Generated once per install and
     * kept stable: Android rotates the on-air Bluetooth address, so this is the
     * only durable identity the beacon has.
     */
    UUID beaconUuid() {
        synchronized (BEACON_UUID_LOCK) {
            String existing = clean(prefs.getString(KEY_BEACON_UUID, ""), "");
            if (isUuid(existing)) {
                return UUID.fromString(existing);
            }
            UUID generated = UUID.randomUUID();
            prefs.edit().putString(KEY_BEACON_UUID, generated.toString()).commit();
            return generated;
        }
    }

    UUID regenerateBeaconUuid() {
        synchronized (BEACON_UUID_LOCK) {
            UUID generated = UUID.randomUUID();
            prefs.edit().putString(KEY_BEACON_UUID, generated.toString()).commit();
            return generated;
        }
    }

    int beaconMajor() {
        return intValue(KEY_BEACON_MAJOR, 1, 0, 65535);
    }

    int beaconMinor() {
        return intValue(KEY_BEACON_MINOR, 1, 0, 65535);
    }

    /** Calibration byte: the RSSI a receiver should see at 1 m. */
    int beaconMeasuredPower() {
        return intValue(KEY_BEACON_MEASURED_POWER, -59, -127, 0);
    }

    int beaconTxPowerDbm() {
        return intValue(KEY_BEACON_TX_POWER_DBM, BEACON_TX_POWER_HIGH, -127, 1);
    }

    List<BeaconRule> beaconRules() {
        List<BeaconRule> rules = new ArrayList<>();
        String raw = prefs.getString(KEY_BEACON_RULES, null);
        if (raw == null) {
            if (prefs.getBoolean(KEY_BEACON_RULES_SEEDED, false)) {
                return rules;
            }
            return seedBeaconRules();
        }
        if (raw.trim().isEmpty()) {
            return rules;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String id = clean(object.optString("id", ""), "");
                if (id.isEmpty()) {
                    id = "beacon-rule-" + i;
                }
                rules.add(new BeaconRule(
                        id,
                        clamp(object.optInt("min_battery", 0), 0, 100),
                        clampBeaconInterval(object.optInt("interval", BEACON_INTERVAL_OFF))));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        sortBeaconRules(rules);
        return rules;
    }

    /** The rule that governs this battery level, or null when none reaches it. */
    BeaconRule beaconRuleFor(int batteryPercent) {
        if (batteryPercent < 0) {
            return null;
        }
        for (BeaconRule rule : beaconRules()) {
            if (rule.matches(batteryPercent)) {
                return rule;
            }
        }
        return null;
    }

    BeaconRule beaconRuleById(String id) {
        String cleanId = id == null ? "" : id.trim();
        if (cleanId.isEmpty()) {
            return null;
        }
        for (BeaconRule rule : beaconRules()) {
            if (cleanId.equals(rule.id)) {
                return rule;
            }
        }
        return null;
    }

    BeaconRule addBeaconRule(String minBatteryPercent, String intervalSeconds) {
        int threshold = parseRequiredInt(minBatteryPercent, 0, 100,
                "Use a battery threshold between 0 and 100");
        int interval = parseRequiredInt(intervalSeconds, 0, 3600,
                "Use an interval between 0 and 3600 seconds (0 = don't broadcast)");
        List<BeaconRule> rules = beaconRules();
        for (BeaconRule existing : rules) {
            if (existing.minBatteryPercent == threshold) {
                throw new IllegalArgumentException("A rule for " + threshold + "% already exists");
            }
        }
        BeaconRule rule = new BeaconRule(UUID.randomUUID().toString(), threshold, interval);
        rules.add(rule);
        saveBeaconRules(rules);
        return rule;
    }

    void removeBeaconRule(String id) {
        String cleanId = id == null ? "" : id.trim();
        if (cleanId.isEmpty()) {
            return;
        }
        List<BeaconRule> kept = new ArrayList<>();
        for (BeaconRule rule : beaconRules()) {
            if (!cleanId.equals(rule.id)) {
                kept.add(rule);
            }
        }
        saveBeaconRules(kept);
    }

    void saveBeaconConfig(
            boolean enabled,
            String major,
            String minor,
            String measuredPower,
            int txPowerDbm) {
        prefs.edit()
                .putBoolean(KEY_BEACON_ENABLED, enabled)
                .putInt(KEY_BEACON_MAJOR, parseInt(major, 1, 0, 65535))
                .putInt(KEY_BEACON_MINOR, parseInt(minor, 1, 0, 65535))
                .putInt(KEY_BEACON_MEASURED_POWER, parseInt(measuredPower, -59, -127, 0))
                .putInt(KEY_BEACON_TX_POWER_DBM, clamp(txPowerDbm, -127, 1))
                .apply();
    }

    /**
     * First run gets the worked example from the feature's design: full-rate
     * above 66%, half-rate above 33%, silent below that. The seeded flag means
     * deleting every rule leaves the list empty instead of resurrecting them.
     */
    private List<BeaconRule> seedBeaconRules() {
        List<BeaconRule> rules = new ArrayList<>();
        rules.add(new BeaconRule(UUID.randomUUID().toString(), 66, 10));
        rules.add(new BeaconRule(UUID.randomUUID().toString(), 33, 30));
        rules.add(new BeaconRule(UUID.randomUUID().toString(), 0, BEACON_INTERVAL_OFF));
        saveBeaconRules(rules);
        return rules;
    }

    private void saveBeaconRules(List<BeaconRule> rules) {
        sortBeaconRules(rules);
        JSONArray array = new JSONArray();
        for (BeaconRule rule : rules) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", rule.id);
                object.put("min_battery", rule.minBatteryPercent);
                object.put("interval", rule.intervalSeconds);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        prefs.edit()
                .putString(KEY_BEACON_RULES, array.toString())
                .putBoolean(KEY_BEACON_RULES_SEEDED, true)
                .apply();
    }

    static String beaconIntervalDisplay(int intervalSeconds) {
        if (intervalSeconds <= BEACON_INTERVAL_OFF) {
            return "Don't broadcast";
        }
        if (intervalSeconds == 1) {
            return "Every second";
        }
        if (intervalSeconds % 60 == 0 && intervalSeconds >= 120) {
            return "Every " + (intervalSeconds / 60) + " minutes";
        }
        return "Every " + intervalSeconds + "s";
    }

    static String beaconTxPowerDisplay(int dbm) {
        if (dbm <= BEACON_TX_POWER_ULTRA_LOW) {
            return "Ultra low (" + dbm + " dBm)";
        }
        if (dbm <= BEACON_TX_POWER_LOW) {
            return "Low (" + dbm + " dBm)";
        }
        if (dbm <= BEACON_TX_POWER_MEDIUM) {
            return "Medium (" + dbm + " dBm)";
        }
        return "High (+" + dbm + " dBm)";
    }

    private static int clampBeaconInterval(int seconds) {
        return clamp(seconds, 0, 3600);
    }

    private static void sortBeaconRules(List<BeaconRule> rules) {
        Collections.sort(rules, new Comparator<BeaconRule>() {
            @Override
            public int compare(BeaconRule left, BeaconRule right) {
                return right.minBatteryPercent - left.minBatteryPercent;
            }
        });
    }

    private static int parseRequiredInt(String value, int min, int max, String message) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(message);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    long settingsLastExportMillis() {
        return prefs.getLong(KEY_SETTINGS_LAST_EXPORT_MILLIS, 0L);
    }

    void setSettingsLastExportMillis(long timestampMillis) {
        prefs.edit().putLong(KEY_SETTINGS_LAST_EXPORT_MILLIS, Math.max(0L, timestampMillis)).apply();
    }

    void saveGpsConfig(
            String serverBaseUrl,
            String trackPath,
            String ssidPattern,
            String highBatteryIntervalMinutes,
            String lowBatteryIntervalMinutes,
            String batteryThresholdPercent,
            String locationTimeoutSeconds,
            String desiredAccuracyMeters,
            String maxCachedLocationMinutes,
            String httpTimeoutSeconds,
            String fallbackLatitude,
            String fallbackLongitude,
            boolean gpsUseRemoteLink,
            boolean useExactAlarms,
            boolean allowIdleAlarms,
            boolean postOnStartup,
            boolean postOnWifiChange,
            boolean showWifiMonitorNotification,
            boolean useGpsProvider,
            boolean useNetworkProvider,
            boolean requestGpsOnSsidMismatch,
            boolean useFallbackOnSsidMatch,
            boolean useCachedBeforeFresh,
            boolean includeExtendedFields,
            boolean caseSensitiveSsid,
            String logMaxLines) {
        prefs.edit()
                .putString(KEY_SERVER_BASE_URL, clean(serverBaseUrl, "https://server:1234"))
                .putString(KEY_TRACK_PATH, clean(trackPath, "/track.php"))
                .putString(KEY_SSID_PATTERN, clean(ssidPattern, "SSID*"))
                .putInt(KEY_INTERVAL_MINUTES, parseInt(lowBatteryIntervalMinutes, 6, 1, 1440))
                .putInt(KEY_HIGH_BATTERY_INTERVAL_MINUTES, parseInt(highBatteryIntervalMinutes, 3, 1, 1440))
                .putInt(KEY_LOW_BATTERY_INTERVAL_MINUTES, parseInt(lowBatteryIntervalMinutes, 6, 1, 1440))
                .putInt(KEY_BATTERY_THRESHOLD_PERCENT, parseInt(batteryThresholdPercent, 66, 1, 100))
                .putInt(KEY_LOCATION_TIMEOUT_SECONDS, parseInt(locationTimeoutSeconds, 60, 5, 300))
                .putInt(KEY_DESIRED_ACCURACY_METERS, parseInt(desiredAccuracyMeters, 50, 1, 10000))
                .putInt(KEY_MAX_CACHED_LOCATION_MINUTES, parseInt(maxCachedLocationMinutes, 5, 0, 1440))
                .putInt(KEY_HTTP_TIMEOUT_SECONDS, parseInt(httpTimeoutSeconds, 10, 1, 120))
                .putString(KEY_FALLBACK_LATITUDE, clean(fallbackLatitude, "52.520008"))
                .putString(KEY_FALLBACK_LONGITUDE, clean(fallbackLongitude, "13.404954"))
                .putBoolean(KEY_GPS_USE_REMOTE_LINK, gpsUseRemoteLink)
                .putBoolean(KEY_USE_EXACT_ALARMS, useExactAlarms)
                .putBoolean(KEY_ALLOW_IDLE_ALARMS, allowIdleAlarms)
                .putBoolean(KEY_POST_ON_STARTUP, postOnStartup)
                .putBoolean(KEY_POST_ON_WIFI_CHANGE, postOnWifiChange)
                .putBoolean(KEY_SHOW_WIFI_MONITOR_NOTIFICATION, showWifiMonitorNotification)
                .putBoolean(KEY_USE_GPS_PROVIDER, useGpsProvider)
                .putBoolean(KEY_USE_NETWORK_PROVIDER, useNetworkProvider)
                .putBoolean(KEY_REQUEST_GPS_ON_SSID_MISMATCH, requestGpsOnSsidMismatch)
                .putBoolean(KEY_USE_FALLBACK_ON_SSID_MATCH, useFallbackOnSsidMatch)
                .putBoolean(KEY_USE_CACHED_BEFORE_FRESH, useCachedBeforeFresh)
                .putBoolean(KEY_INCLUDE_EXTENDED_FIELDS, includeExtendedFields)
                .putBoolean(KEY_CASE_SENSITIVE_SSID, caseSensitiveSsid)
                .putInt(KEY_LOG_MAX_LINES, parseInt(logMaxLines, 500, 50, 5000))
                .apply();
    }

    void saveHighPriorityConfig(
            boolean enabled,
            String packageName,
            String textFilter,
            boolean remoteEnabled,
            String remoteTextFilter,
            String remoteDedupeSeconds,
            String toneTitle,
            String playSeconds,
            String dedupeSeconds,
            boolean raiseAlarmVolume,
            String alarmVolumePercent) {
        prefs.edit()
                .putBoolean(KEY_HIGH_PRIORITY_ENABLED, enabled)
                .putString(KEY_HIGH_PRIORITY_PACKAGE, clean(packageName, ""))
                .putString(KEY_HIGH_PRIORITY_TEXT_FILTER, clean(textFilter, "!"))
                .putBoolean(KEY_HIGH_PRIORITY_REMOTE_ENABLED, remoteEnabled)
                .putString(KEY_HIGH_PRIORITY_REMOTE_TEXT_FILTER, clean(remoteTextFilter, "!"))
                .putInt(KEY_HIGH_PRIORITY_REMOTE_DEDUPE_SECONDS, parseInt(remoteDedupeSeconds, 60, 0, 3600))
                .putString(KEY_HIGH_PRIORITY_TONE_TITLE, clean(toneTitle, ""))
                .putInt(KEY_HIGH_PRIORITY_PLAY_SECONDS, parseInt(playSeconds, 15, 1, 300))
                .putInt(KEY_HIGH_PRIORITY_DEDUPE_SECONDS, parseInt(dedupeSeconds, 60, 0, 3600))
                .putBoolean(KEY_HIGH_PRIORITY_RAISE_ALARM_VOLUME, raiseAlarmVolume)
                .putInt(KEY_HIGH_PRIORITY_ALARM_VOLUME_PERCENT, parseInt(alarmVolumePercent, 100, 1, 100))
                .apply();
    }

    void saveBatteryAlertConfig(
            boolean enabled,
            String thresholdPercent,
            String checkIntervalMinutes,
            String vibrateSeconds,
            boolean useExactAlarms,
            boolean allowIdleAlarms) {
        prefs.edit()
                .putBoolean(KEY_BATTERY_ALERT_ENABLED, enabled)
                .putInt(KEY_BATTERY_ALERT_THRESHOLD_PERCENT, parseInt(thresholdPercent, 20, 1, 100))
                .putInt(KEY_BATTERY_ALERT_CHECK_INTERVAL_MINUTES, parseInt(checkIntervalMinutes, 5, 1, 1440))
                .putInt(KEY_BATTERY_ALERT_VIBRATE_SECONDS, parseInt(vibrateSeconds, 10, 0, 60))
                .putBoolean(KEY_BATTERY_ALERT_USE_EXACT_ALARMS, useExactAlarms)
                .putBoolean(KEY_BATTERY_ALERT_ALLOW_IDLE_ALARMS, allowIdleAlarms)
                .apply();
    }

    private void saveVolumeRules(List<VolumeRule> rules) {
        sortVolumeRules(rules);
        JSONArray array = new JSONArray();
        for (VolumeRule rule : rules) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", rule.id);
                object.put("hour", rule.hour);
                object.put("minute", rule.minute);
                object.put("media", rule.mediaPercent);
                object.put("ring", rule.ringPercent);
                object.put("notification", rule.notificationPercent);
                object.put("alarm", rule.alarmPercent);
                object.put("dnd", rule.dndMode);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(KEY_VOLUME_RULES, array.toString()).apply();
    }

    void saveRemoteLinkConfig(
            boolean enabled,
            String endpoint,
            String username,
            String password,
            String heartbeatSeconds,
            boolean acceptAnySslCert,
            boolean showNotification) {
        prefs.edit()
                .putBoolean(KEY_REMOTE_LINK_ENABLED, enabled)
                .putString(KEY_REMOTE_LINK_ENDPOINT, clean(endpoint, "https://server:1234"))
                .putString(KEY_REMOTE_LINK_USERNAME, clean(username, ""))
                .putString(KEY_REMOTE_LINK_PASSWORD, clean(password, ""))
                .putInt(KEY_REMOTE_LINK_HEARTBEAT_SECONDS, parseInt(heartbeatSeconds, 60, 10, 3600))
                .putBoolean(KEY_REMOTE_LINK_ACCEPT_ANY_SSL_CERT, acceptAnySslCert)
                .putBoolean(KEY_REMOTE_LINK_SHOW_NOTIFICATION, showNotification)
                .apply();
    }

    void saveVpnConfig(
            String username,
            String password,
            String keyPassphrase,
            String tapStaticIp,
            String tapNetmask,
            String tapGateway,
            boolean remoteCommandEnabled) {
        prefs.edit()
                .putString(KEY_VPN_USERNAME, clean(username, ""))
                .putString(KEY_VPN_PASSWORD, clean(password, ""))
                .putString(KEY_VPN_KEY_PASSPHRASE, clean(keyPassphrase, ""))
                .putString(KEY_VPN_TAP_STATIC_IP, clean(tapStaticIp, ""))
                .putString(KEY_VPN_TAP_NETMASK, clean(tapNetmask, "255.255.255.0"))
                .putString(KEY_VPN_TAP_GATEWAY, clean(tapGateway, ""))
                .putBoolean(KEY_VPN_REMOTE_COMMAND_ENABLED, remoteCommandEnabled)
                .apply();
    }

    void saveLogConfig(boolean enabled, String logMaxLines) {
        prefs.edit()
                .putBoolean(KEY_LOG_ENABLED, enabled)
                .putInt(KEY_LOG_MAX_LINES, parseInt(logMaxLines, 500, 50, 5000))
                .apply();
    }

    void saveNotificationHistoryConfig(boolean clearNotificationsOnOpen) {
        prefs.edit()
                .putBoolean(KEY_CLEAR_NOTIFICATIONS_ON_OPEN, clearNotificationsOnOpen)
                .apply();
    }

    void saveNotificationBackupConfig(boolean enabled, boolean includeSysmgr) {
        prefs.edit()
                .putBoolean(KEY_NOTIFICATION_BACKUP_ENABLED, enabled)
                .putBoolean(KEY_NOTIFICATION_BACKUP_INCLUDE_SYSMGR, includeSysmgr)
                .apply();
    }

    void saveRebootConfig(
            boolean automationEnabled,
            boolean notificationTriggerEnabled,
            boolean remoteTriggerEnabled,
            boolean scheduleEnabled,
            String triggerPackage,
            String triggerTitle,
            String triggerText,
            String scheduleHour,
            String scheduleMinute,
            String wifiPattern,
            boolean onlyWhenWifiNotMatching,
            String pinSequence,
            String delayedTestSeconds,
            String powerDialogWaitMs,
            String stepWaitMs) {
        prefs.edit()
                .putBoolean(KEY_REBOOT_AUTOMATION_ENABLED, automationEnabled)
                .putBoolean(KEY_REBOOT_NOTIFICATION_TRIGGER_ENABLED, notificationTriggerEnabled)
                .putBoolean(KEY_REBOOT_REMOTE_TRIGGER_ENABLED, remoteTriggerEnabled)
                .putBoolean(KEY_REBOOT_SCHEDULE_ENABLED, scheduleEnabled)
                .putString(KEY_REBOOT_TRIGGER_PACKAGE, clean(triggerPackage, ""))
                .putString(KEY_REBOOT_TRIGGER_TITLE, clean(triggerTitle, "Automation"))
                .putString(KEY_REBOOT_TRIGGER_TEXT, clean(triggerText, "Reboot"))
                .putInt(KEY_REBOOT_SCHEDULE_HOUR, parseInt(scheduleHour, 4, 0, 23))
                .putInt(KEY_REBOOT_SCHEDULE_MINUTE, parseInt(scheduleMinute, 0, 0, 59))
                .putString(KEY_REBOOT_WIFI_PATTERN, clean(wifiPattern, "SSID"))
                .putBoolean(KEY_REBOOT_ONLY_WHEN_WIFI_NOT_MATCHING, onlyWhenWifiNotMatching)
                .putString(KEY_REBOOT_PIN_SEQUENCE, clean(pinSequence, ""))
                .putInt(KEY_REBOOT_DELAYED_TEST_SECONDS, parseInt(delayedTestSeconds, 20, 5, 300))
                .putInt(KEY_REBOOT_POWER_DIALOG_WAIT_MS, parseInt(powerDialogWaitMs, 1200, 250, 10000))
                .putInt(KEY_REBOOT_STEP_WAIT_MS, parseInt(stepWaitMs, 900, 250, 10000))
                .apply();
    }

    int exportSettingsXml(OutputStream output) throws Exception {
        Map<String, ?> values = new TreeMap<>(prefs.getAll());
        XmlSerializer serializer = Xml.newSerializer();
        serializer.setOutput(output, "UTF-8");
        serializer.startDocument("UTF-8", true);
        serializer.startTag(null, "system-manager-settings");
        serializer.attribute(null, "version", "1");
        serializer.attribute(null, "prefs", PREFS);
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            writeSetting(serializer, entry.getKey(), entry.getValue());
        }
        serializer.endTag(null, "system-manager-settings");
        serializer.endDocument();
        serializer.flush();
        return values.size();
    }

    int importSettingsXml(InputStream input) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(input, "UTF-8");

        int event;
        do {
            event = parser.next();
        } while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT);
        if (event != XmlPullParser.START_TAG || !"system-manager-settings".equals(parser.getName())) {
            throw new IllegalArgumentException("Not a System Manager settings XML file");
        }

        int rootDepth = parser.getDepth();
        Map<String, Object> values = new TreeMap<>();
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == rootDepth
                    && "system-manager-settings".equals(parser.getName())) {
                break;
            }
            if (event != XmlPullParser.START_TAG) {
                continue;
            }
            if (!"setting".equals(parser.getName())) {
                skipTag(parser);
                continue;
            }
            String key = parser.getAttributeValue(null, "key");
            String type = parser.getAttributeValue(null, "type");
            if (key == null || key.trim().isEmpty()) {
                skipTag(parser);
                continue;
            }
            values.put(key.trim(), readSettingValue(parser, type));
        }

        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            putValue(editor, entry.getKey(), entry.getValue());
        }
        if (!editor.commit()) {
            throw new IllegalStateException("Could not save imported settings");
        }
        return values.size();
    }

    private String string(String key, String fallback) {
        return clean(prefs.getString(key, fallback), fallback);
    }

    private static void writeSetting(XmlSerializer serializer, String key, Object value) throws Exception {
        if (key == null || value == null) {
            return;
        }
        serializer.startTag(null, "setting");
        serializer.attribute(null, "key", key);
        serializer.attribute(null, "type", settingType(value));
        if (value instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<String> values = (Set<String>) value;
            for (String item : new TreeSet<>(values)) {
                serializer.startTag(null, "item");
                serializer.text(item == null ? "" : item);
                serializer.endTag(null, "item");
            }
        } else {
            serializer.text(String.valueOf(value));
        }
        serializer.endTag(null, "setting");
    }

    private static String settingType(Object value) {
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Integer) {
            return "int";
        }
        if (value instanceof Long) {
            return "long";
        }
        if (value instanceof Float) {
            return "float";
        }
        if (value instanceof Set) {
            return "string-set";
        }
        return "string";
    }

    private static Object readSettingValue(XmlPullParser parser, String type) throws Exception {
        String cleanType = type == null ? "string" : type.trim();
        if ("string-set".equals(cleanType)) {
            return readStringSet(parser);
        }

        String text = parser.nextText();
        if ("boolean".equals(cleanType)) {
            return Boolean.valueOf(text.trim());
        }
        if ("int".equals(cleanType)) {
            return Integer.valueOf(text.trim());
        }
        if ("long".equals(cleanType)) {
            return Long.valueOf(text.trim());
        }
        if ("float".equals(cleanType)) {
            return Float.valueOf(text.trim());
        }
        return text;
    }

    private static Set<String> readStringSet(XmlPullParser parser) throws Exception {
        int settingDepth = parser.getDepth();
        Set<String> values = new HashSet<>();
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.END_TAG
                    && parser.getDepth() == settingDepth
                    && "setting".equals(parser.getName())) {
                break;
            }
            if (event != XmlPullParser.START_TAG) {
                continue;
            }
            if ("item".equals(parser.getName())) {
                values.add(parser.nextText());
            } else {
                skipTag(parser);
            }
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static void putValue(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Set) {
            editor.putStringSet(key, (Set<String>) value);
        } else {
            editor.putString(key, value == null ? "" : String.valueOf(value));
        }
    }

    private static void skipTag(XmlPullParser parser) throws Exception {
        int depth = 1;
        while (depth > 0) {
            int event = parser.next();
            if (event == XmlPullParser.START_TAG) {
                depth++;
            } else if (event == XmlPullParser.END_TAG) {
                depth--;
            } else if (event == XmlPullParser.END_DOCUMENT) {
                break;
            }
        }
    }

    private int intValue(String key, int fallback, int min, int max) {
        return clamp(prefs.getInt(key, fallback), min, max);
    }

    private double doubleValue(String key, double fallback) {
        try {
            String value = prefs.getString(key, Double.toString(fallback));
            if (value == null) {
                return fallback;
            }
            return Double.parseDouble(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String clean(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static int parseInt(String value, int fallback, int min, int max) {
        try {
            return clamp(Integer.parseInt(value.trim()), min, max);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int[] parseTime(String value) {
        String cleanValue = value == null ? "" : value.trim();
        String[] parts = cleanValue.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Use 24-hour time like 17:00");
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                throw new IllegalArgumentException("Use 24-hour time like 17:00");
            }
            return new int[]{hour, minute};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Use 24-hour time like 17:00");
        }
    }

    static int parseVolumePercent(String value) {
        String cleanValue = value == null ? "" : value.trim();
        if (cleanValue.isEmpty()
                || "unchanged".equalsIgnoreCase(cleanValue)
                || "-".equals(cleanValue)) {
            return VOLUME_UNCHANGED;
        }
        if (cleanValue.endsWith("%")) {
            cleanValue = cleanValue.substring(0, cleanValue.length() - 1).trim();
        }
        try {
            return clamp(Integer.parseInt(cleanValue), 0, 100);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Use 0-100 or Unchanged for volume levels");
        }
    }

    static String volumeDisplay(int percent) {
        return percent == VOLUME_UNCHANGED ? "Unchanged" : percent + "%";
    }

    static String dndDisplay(int mode) {
        if (mode == DND_ENABLE) {
            return "Enable";
        }
        if (mode == DND_DISABLE) {
            return "Disable";
        }
        return "Unchanged";
    }

    private static int clampVolume(int percent) {
        if (percent == VOLUME_UNCHANGED) {
            return VOLUME_UNCHANGED;
        }
        return clamp(percent, 0, 100);
    }

    private static int clampDndMode(int mode) {
        if (mode == DND_ENABLE || mode == DND_DISABLE) {
            return mode;
        }
        return DND_UNCHANGED;
    }

    private static void sortVolumeRules(List<VolumeRule> rules) {
        Collections.sort(rules, new Comparator<VolumeRule>() {
            @Override
            public int compare(VolumeRule left, VolumeRule right) {
                return left.minuteOfDay() - right.minuteOfDay();
            }
        });
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
