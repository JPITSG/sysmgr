package com.jpitsg.sysman;

import android.content.Context;
import android.os.Build;

import java.net.URI;
import java.util.List;

/**
 * What each switchable feature needs before it can sensibly be turned on, judged from what is
 * saved and from the state of the phone. The overview switches use it to refuse a feature that is
 * not set up yet, and to say why. It never looks at the screen, so the answer is the same wherever
 * it is asked.
 *
 * <p>It answers "can this be switched on now", not "is it working": a server that is down, or a
 * link that is still connecting, are no reason to refuse.
 */
final class Readiness {
    /** No prompt would help; the user has to set something up. */
    static final int FIX_NONE = 0;
    static final int FIX_LOCATION = 1;
    static final int FIX_BLUETOOTH = 2;
    static final int FIX_NOTIFICATIONS = 3;

    /** Why a feature cannot be switched on yet. */
    static final class Block {
        /** A clause that reads after "<feature> isn't ready — ". */
        final String reason;
        /** The permission prompt that would fix it, or {@link #FIX_NONE}. */
        final int fix;

        Block(String reason) {
            this(reason, FIX_NONE);
        }

        Block(String reason, int fix) {
            this.reason = reason;
            this.fix = fix;
        }
    }

    private Readiness() {
    }

    /**
     * Null when the feature behind the overview tile {@code id} can be switched on; otherwise the
     * first thing in the way.
     */
    static Block check(Context context, String id) {
        Context app = context.getApplicationContext();
        Config config = Config.get(app);
        switch (id) {
            case "remote_link":
                return remoteLinkAddress(config);
            case "vpn":
                return vpn(app, config);
            case "vnc":
                return vnc(app, config);
            case "beacon":
                return beacon(app, config);
            case "gps":
                return gps(app, config);
            case "notification_backup":
                return notificationBackup(app, config);
            case "alerts":
                return alerts(app, config);
            case "battery":
                return battery(app);
            case "reboot":
                return reboot(app, config);
            case "upgrade":
                return remoteLink(config);
            default:
                // Logging needs nothing; neither does anything without a switch.
                return null;
        }
    }

    /** Whether alerts for notifications from the chosen app could be switched on. */
    static Block appAlerts(Context context) {
        Context app = context.getApplicationContext();
        if (Config.get(app).highPriorityPackage().trim().isEmpty()) {
            return new Block("choose the app to watch first");
        }
        if (!PermissionState.notificationListenerEnabled(app)) {
            return new Block("allow Notification access first");
        }
        return null;
    }

    /** Whether alerts that arrive over the Remote Link could be switched on. */
    static Block remoteAlerts(Context context) {
        return remoteLink(Config.get(context.getApplicationContext()));
    }

    /**
     * True for a complete server address: an allowed scheme, a host, and a sensible port if one is
     * given. Says nothing about whether anything answers there.
     */
    static boolean completeEndpoint(String value, String... schemes) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme();
            boolean allowed = false;
            if (scheme != null) {
                for (String candidate : schemes) {
                    if (candidate.equalsIgnoreCase(scheme)) {
                        allowed = true;
                        break;
                    }
                }
            }
            int port = uri.getPort();
            return allowed
                    && uri.getHost() != null
                    && !uri.getHost().trim().isEmpty()
                    && port != 0
                    && port <= 65535;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A server address the user has really entered: complete, and not the one a fresh install ships with. */
    private static boolean filledIn(String endpoint, String... schemes) {
        return !Config.isPlaceholderEndpoint(endpoint) && completeEndpoint(endpoint, schemes);
    }

    private static Block remoteLinkAddress(Config config) {
        if (!filledIn(config.remoteLinkEndpoint(), "http", "https", "ws", "wss")) {
            return new Block("enter your server address first");
        }
        return null;
    }

    /** What several features need of the Remote Link: it is on, and it has somewhere to connect. */
    private static Block remoteLink(Config config) {
        if (!config.remoteLinkEnabled()) {
            return new Block("turn on Remote Link first");
        }
        if (remoteLinkAddress(config) != null) {
            return new Block("set up Remote Link first");
        }
        return null;
    }

    private static Block vpn(Context app, Config config) {
        if (!OpenVpnProfileStore.hasProfile(app)) {
            return new Block("import a VPN profile first");
        }
        OpenVpnProfileStore.Meta meta = OpenVpnProfileStore.readMeta(app);
        if (!meta.hasProfile()) {
            return new Block("import a VPN profile first");
        }
        if (!meta.allSlotsSatisfied()) {
            return new Block("import the missing certificate files first");
        }
        if (meta.keyEncrypted && config.vpnKeyPassphrase().isEmpty()) {
            return new Block("enter the private key passphrase first");
        }
        if (meta.authUserPass && (config.vpnUsername().isEmpty() || config.vpnPassword().isEmpty())) {
            return new Block("enter the VPN username and password first");
        }
        return null;
    }

    private static Block vnc(Context app, Config config) {
        if (!VncSecretStore.hasPassword(app)) {
            return new Block("set a VNC password first");
        }
        String blocking = VncManager.blockingReason(app);
        if (blocking != null) {
            return new Block(lowerFirst(blocking));
        }
        if (config.vncEnabledOnMatchingWifi() && config.vncMatchingWifiSsid().trim().isEmpty()) {
            // With nothing to match, the server would wait for a network that cannot exist.
            return new Block("enter the Wi-Fi name for the VNC Wi-Fi condition first");
        }
        return null;
    }

    private static Block beacon(Context app, Config config) {
        String state = BeaconManager.blockingState(app);
        if (BeaconStateStore.STATE_UNSUPPORTED.equals(state)) {
            return new Block("this phone can't broadcast a Bluetooth beacon");
        }
        if (BeaconStateStore.STATE_NO_PERMISSION.equals(state)) {
            return new Block("allow Bluetooth access first", FIX_BLUETOOTH);
        }
        if (BeaconStateStore.STATE_BLUETOOTH_OFF.equals(state)) {
            return new Block("turn Bluetooth on first");
        }
        boolean broadcasts = false;
        List<Config.BeaconRule> rules = config.beaconRules();
        for (Config.BeaconRule rule : rules) {
            if (rule.broadcasts()) {
                broadcasts = true;
                break;
            }
        }
        if (!broadcasts) {
            return new Block("add a battery rule that broadcasts first");
        }
        return null;
    }

    private static Block gps(Context app, Config config) {
        boolean overLink = config.gpsUseRemoteLink() && remoteLink(config) == null;
        boolean overHttp = filledIn(config.serverBaseUrl(), "http", "https")
                && !config.trackPath().trim().isEmpty();
        if (!overLink && !overHttp) {
            return new Block(config.gpsUseRemoteLink()
                    ? "set up Remote Link or a GPS server address first"
                    : "enter the GPS server address first");
        }
        if (!config.useGpsProvider() && !config.useNetworkProvider()) {
            return new Block("turn on a location provider first");
        }
        if (!PermissionState.hasFineLocation(app) && !PermissionState.hasCoarseLocation(app)) {
            return new Block("allow location access first", FIX_LOCATION);
        }
        if (!PermissionState.locationServicesEnabled(app)) {
            return new Block("turn on device location first");
        }
        return null;
    }

    private static Block notificationBackup(Context app, Config config) {
        Block link = remoteLink(config);
        if (link != null) {
            return link;
        }
        if (!PermissionState.notificationListenerEnabled(app)) {
            return new Block("allow Notification access first");
        }
        return null;
    }

    private static Block alerts(Context app, Config config) {
        Block fromApp = appAlerts(app);
        Block fromLink = remoteLink(config);
        if (fromApp == null || fromLink == null) {
            return null;
        }
        return new Block(fromApp.reason + ", or " + fromLink.reason);
    }

    private static Block battery(Context app) {
        if (!PermissionState.notificationsEnabled(app)) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? new Block("allow notifications first", FIX_NOTIFICATIONS)
                    : new Block("turn on notifications for System Manager first");
        }
        return null;
    }

    private static Block reboot(Context app, Config config) {
        if (!PermissionState.accessibilityServiceEnabled(app)) {
            return new Block("turn on the Accessibility service first");
        }
        boolean notification = config.rebootNotificationTriggerEnabled();
        boolean remote = config.rebootRemoteTriggerEnabled();
        boolean schedule = config.rebootScheduleEnabled();
        if (!notification && !remote && !schedule) {
            return new Block("turn on a trigger first");
        }
        if (notification && config.rebootTriggerPackage().trim().isEmpty()) {
            return new Block("choose the notification app for the reboot trigger first");
        }
        if (remote) {
            Block link = remoteLink(config);
            if (link != null) {
                return link;
            }
        }
        return null;
    }

    private static String lowerFirst(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
