package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;

import java.io.FileDescriptor;
import java.io.File;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VpnService that runs the embedded openvpn binary, drives it over the
 * management interface, and establishes the tun (or a tap bridge). One session
 * at a time; a generation counter discards callbacks from a superseded session.
 */
public final class OpenVpnService extends VpnService implements OpenVpnManagementThread.Host {
    static final String ACTION_CONNECT = "com.jpitsg.sysman.action.VPN_CONNECT";
    static final String ACTION_DISCONNECT = "com.jpitsg.sysman.action.VPN_DISCONNECT";

    private static final String CHANNEL_ID = "system_manager_vpn";
    private static final int NOTIFICATION_ID = 0x5304;
    private static final long NOTIFICATION_MIN_INTERVAL_MS = 2_000L;
    private static final long ACCEPT_WATCHDOG_MS = 10_000L;
    private static final long CONNECT_TIMEOUT_MS = 60_000L;

    private static volatile OpenVpnService activeService;

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private volatile int sessionId;
    private volatile OpenVpnProcessThread processThread;
    private volatile OpenVpnManagementThread managementThread;
    private volatile TapBridge tapBridge;
    private volatile ParcelFileDescriptor tunPfd;
    private volatile FileDescriptor fdToCloseAfterSend;
    private volatile String remote = "";
    private volatile long rxBytes;
    private volatile long txBytes;
    private volatile long lastNotificationAt;
    private volatile boolean stopping;
    private volatile boolean reachedConnected;
    private volatile Network sessionNetwork;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    /** Re-resolved before every foreground start, so the toggle applies at once. */
    private String channelId = CHANNEL_ID;

    static boolean isActive() {
        return activeService != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeService = this;
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        ensureNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_CONNECT : intent.getAction();
        if (action == null) {
            action = ACTION_CONNECT;
        }
        if (ACTION_DISCONNECT.equals(action)) {
            LogStore.append(this, "vpn", "VPN disconnect requested");
            teardown(OpenVpnStateStore.STATE_DISCONNECTED, null);
            return START_NOT_STICKY;
        }

        // ACTION_CONNECT (also the null-intent / always-on path). We may have
        // been launched via startForegroundService, so satisfy the
        // startForeground contract before any early return.
        startForegroundVpn(OpenVpnStateStore.STATE_STARTING);
        if (!OpenVpnProfileStore.hasProfile(this)) {
            LogStore.append(this, "vpn", "VPN start ignored; no profile configured");
            stopForegroundCompat();
            OpenVpnStateStore.setState(this, OpenVpnStateStore.STATE_DISCONNECTED, null);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (VpnService.prepare(this) != null) {
            LogStore.append(this, "vpn", "VPN start aborted; consent missing");
            OpenVpnStateStore.setState(this, OpenVpnStateStore.STATE_ERROR, "VPN consent missing");
            teardown(OpenVpnStateStore.STATE_ERROR, "VPN consent missing");
            return START_NOT_STICKY;
        }
        startSession();
        return START_NOT_STICKY;
    }

    @Override
    public void onRevoke() {
        LogStore.append(this, "vpn", "VPN revoked by system");
        teardown(OpenVpnStateStore.STATE_DISCONNECTED, "revoked by system");
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        unregisterNetworkCallback();
        if (activeService == this) {
            activeService = null;
        }
        super.onDestroy();
    }

    // ---- session lifecycle -------------------------------------------------

    private void startSession() {
        final int generation = ++sessionId;
        stopping = false;
        reachedConnected = false;
        sessionNetwork = null;
        rxBytes = 0L;
        txBytes = 0L;
        remote = OpenVpnProfileStore.readMeta(this).remoteSummary();
        OpenVpnStateStore.setRemote(this, remote);
        OpenVpnStateStore.setState(this, OpenVpnStateStore.STATE_CONNECTING, "");
        registerNetworkCallback();

        File socketFile = new File(getCacheDir(), "vpn-mgmt.sock");
        final OpenVpnManagementThread mgmt = new OpenVpnManagementThread(this, socketFile.getAbsolutePath(), this);
        try {
            mgmt.bind();
        } catch (Exception e) {
            LogStore.append(this, "vpn", "management bind failed: " + e.getMessage());
            teardown(OpenVpnStateStore.STATE_ERROR, "management socket bind failed");
            return;
        }
        managementThread = mgmt;
        new Thread(mgmt, "SystemManagerVpnMgmt").start();

        String binary = new File(getApplicationInfo().nativeLibraryDir, "libopenvpn.so").getAbsolutePath();
        File profileConf = OpenVpnProfileStore.profileConf(this);
        // hold=true so openvpn parks at >HOLD, letting us enable state/bytecount
        // reporting before releasing it (see OpenVpnManagementThread hold handler).
        List<String> argv = OpenVpnProcessThread.buildArgv(binary, profileConf, socketFile, true, 3);
        OpenVpnProcessThread proc = new OpenVpnProcessThread(this, argv, OpenVpnProfileStore.dir(this),
                getApplicationInfo().nativeLibraryDir, false, new OpenVpnProcessThread.Listener() {
                    @Override
                    public void onProcessExited(final int exitCode, final boolean requested, final String lastFatal) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                handleProcessExit(generation, exitCode, requested, lastFatal);
                            }
                        });
                    }
                });
        processThread = proc;
        new Thread(proc, "SystemManagerVpnProcess").start();

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (generation == sessionId && !stopping
                        && !OpenVpnStateStore.STATE_CONNECTED.equals(OpenVpnStateStore.state(OpenVpnService.this))
                        && (processThread == null || !processThread.isAlive())) {
                    LogStore.append(OpenVpnService.this, "vpn", "openvpn did not connect within watchdog window");
                    teardown(OpenVpnStateStore.STATE_ERROR, "openvpn failed to start");
                }
            }
        }, ACCEPT_WATCHDOG_MS);

        // Connect deadline: if the tunnel never reaches CONNECTED (unreachable
        // or unresponsive server), stop instead of spinning on connect-retry.
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (generation == sessionId && !stopping && !reachedConnected) {
                    LogStore.append(OpenVpnService.this, "vpn", "VPN connect timed out after "
                            + (CONNECT_TIMEOUT_MS / 1000L) + "s");
                    teardown(OpenVpnStateStore.STATE_ERROR, "connect timed out");
                }
            }
        }, CONNECT_TIMEOUT_MS);
    }

    private void handleProcessExit(int generation, int exitCode, boolean requested, String lastFatal) {
        if (generation != sessionId) {
            return;
        }
        if (requested || stopping) {
            teardown(OpenVpnStateStore.STATE_DISCONNECTED, null);
            return;
        }
        String detail = lastFatal == null || lastFatal.isEmpty()
                ? "openvpn exited code=" + exitCode : lastFatal;
        LogStore.append(this, "vpn", "openvpn exited unexpectedly code=" + exitCode);
        teardown(OpenVpnStateStore.STATE_ERROR, detail);
    }

    private void teardown(String finalState, String error) {
        if (stopping) {
            return;
        }
        stopping = true;
        sessionId++; // invalidate any in-flight callbacks
        unregisterNetworkCallback();
        OpenVpnManagementThread mgmt = managementThread;
        final OpenVpnProcessThread proc = processThread;
        managementThread = null;
        processThread = null;

        if (mgmt != null) {
            mgmt.markTunDead();
            mgmt.sendSigint();
            mgmt.requestStop();
        }
        final OpenVpnManagementThread mgmtRef = mgmt;
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (proc != null) {
                    proc.markStopRequested();
                    proc.stop();
                }
                if (mgmtRef != null) {
                    mgmtRef.close();
                }
                // Close the tun first so the tap TX thread's blocking read on it
                // unblocks; then stop the bridge (which closes the socketpair end
                // to unblock the RX thread) and join cleanly.
                closeTun();
                TapBridge bridge = tapBridge;
                if (bridge != null) {
                    bridge.stop();
                    tapBridge = null;
                }
            }
        }, "SystemManagerVpnStop").start();

        OpenVpnStateStore.setState(this, finalState, error);
        stopForegroundCompat();
        stopSelf();
    }

    private void closeTun() {
        ParcelFileDescriptor pfd = tunPfd;
        tunPfd = null;
        if (pfd != null) {
            try {
                pfd.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ---- network-change reconnect ------------------------------------------

    private void registerNetworkCallback() {
        if (connectivityManager == null || networkCallback != null) {
            return;
        }
        // Watch the best UNDERLYING (non-VPN) network. A plain default-network
        // callback would report our own tun once connected and reconnect in an
        // endless loop; requiring NOT_VPN excludes it, and best-matching tracks
        // the single network openvpn's protected socket actually transits.
        // (registerBestMatchingNetworkCallback is API 31+; below that we rely on
        // openvpn's own keepalive/ping-restart to recover.)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            LogStore.append(this, "vpn", "underlying-network monitoring unavailable (<API 31); relying on keepalive");
            return;
        }
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(final Network network) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleUnderlyingNetwork(network);
                    }
                });
            }
        };
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        try {
            connectivityManager.registerBestMatchingNetworkCallback(request, networkCallback, mainHandler);
        } catch (RuntimeException e) {
            networkCallback = null;
            LogStore.append(this, "vpn", "network callback registration failed: " + e.getMessage());
        }
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager == null || networkCallback == null) {
            return;
        }
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) {
        }
        networkCallback = null;
    }

    /**
     * The best underlying (non-VPN) network changed — nudge openvpn to rebind
     * its protected socket. The first callback after a connect only records the
     * baseline, so establishing the tunnel never self-triggers a reconnect.
     */
    private void handleUnderlyingNetwork(Network network) {
        if (network == null) {
            return;
        }
        OpenVpnManagementThread mgmt = managementThread;
        Network previous = sessionNetwork;
        sessionNetwork = network;
        if (mgmt == null || stopping || !reachedConnected || previous == null || previous.equals(network)) {
            return; // not connected yet, tearing down, first baseline, or unchanged
        }
        LogStore.append(this, "vpn", "underlying network changed; reconnecting VPN");
        OpenVpnStateStore.setState(this, OpenVpnStateStore.STATE_RECONNECTING, null);
        mgmt.sendNetworkChange();
    }

    // ---- Host callbacks (management thread) --------------------------------

    @Override
    public boolean protectSocket(int fd) {
        return protect(fd);
    }

    @Override
    public FileDescriptor establishAndBuildFd(VpnTunConfig config) {
        try {
            boolean tap = OpenVpnProfileStore.readMeta(this).isTap();
            ParcelFileDescriptor pfd = buildAndEstablish(config, tap);
            if (pfd == null) {
                onFatal("VpnService.establish returned null");
                return null;
            }
            tunPfd = pfd;
            fdToCloseAfterSend = null;
            if (!tap) {
                return pfd.getFileDescriptor();
            }
            // tap: hand openvpn one end of a datagram socketpair; bridge the other to the tun.
            FileDescriptor ovpnEnd = new FileDescriptor();
            FileDescriptor appEnd = new FileDescriptor();
            Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, ovpnEnd, appEnd);
            startTapBridge(config, pfd.getFileDescriptor(), appEnd);
            fdToCloseAfterSend = ovpnEnd;
            return ovpnEnd;
        } catch (Exception e) {
            LogStore.append(this, "vpn", "establish failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            onFatal("failed to establish tun");
            return null;
        }
    }

    @Override
    public void afterTunFdSent() {
        FileDescriptor fd = fdToCloseAfterSend;
        fdToCloseAfterSend = null;
        if (fd != null) {
            try {
                Os.close(fd);
            } catch (Exception ignored) {
            }
        }
    }

    private ParcelFileDescriptor buildAndEstablish(VpnTunConfig config, boolean tap) {
        VpnService.Builder builder = new VpnService.Builder();
        OpenVpnProfileStore.Meta meta = OpenVpnProfileStore.readMeta(this);
        builder.setSession(meta.remoteHost.isEmpty() ? "System Manager VPN" : meta.remoteHost);
        builder.setMtu(config.mtu > 0 ? config.mtu : 1500);

        String ip4 = config.ip4;
        String netmask = config.netmask4;
        if ((ip4 == null || ip4.isEmpty())) {
            // tap static override
            Config cfg = Config.get(this);
            ip4 = cfg.vpnTapStaticIp().trim();
            netmask = cfg.vpnTapNetmask().trim();
        }
        if (ip4 == null || ip4.isEmpty()) {
            onFatal(tap ? "tap: no IP configuration (set a static IP in the profile)" : "server pushed no IP config");
            return null;
        }
        int prefix = prefixFor(config.topology, netmask);
        builder.addAddress(ip4, prefix);

        if (config.ip6 != null && !config.ip6.isEmpty()) {
            String[] p = config.ip6.split("/");
            if (p.length == 2) {
                builder.addAddress(p[0], parseIntSafe(p[1], 64));
            }
        }

        Set<String> installedRoutes = new HashSet<>();
        // A wildcard server socket already accepts packets addressed to the VPN
        // interface. Always install the interface's connected route as well so
        // replies to a VPN-side client return through the tunnel even when the
        // server also pushed unrelated routes (for example, a remote LAN).
        addInterfaceRoutes(builder, installedRoutes, config, ip4, prefix);

        for (VpnTunConfig.Route4 r : config.routes4) {
            int net = TapBridge.parseIp(r.network) & TapBridge.parseIp(r.netmask);
            int rprefix = maskToPrefix(TapBridge.parseIp(r.netmask));
            addVpnRoute(builder, installedRoutes,
                    TapBridge.ipToString(net), rprefix, r.network + "/" + rprefix);
        }
        for (VpnTunConfig.Route6 r : config.routes6) {
            String[] p = r.destination.split("/");
            if (p.length == 2) {
                addVpnRoute(builder, installedRoutes,
                        p[0], parseIntSafe(p[1], 128), r.destination);
            }
        }

        for (String dns : config.dns4) {
            safeDns(builder, dns);
        }
        for (String dns : config.dns6) {
            safeDns(builder, dns);
        }
        for (String domain : config.domains) {
            try {
                builder.addSearchDomain(domain);
            } catch (RuntimeException ignored) {
            }
        }
        if (config.dns4.isEmpty() && config.dns6.isEmpty()) {
            LogStore.append(this, "vpn", "no DNS pushed; name resolution uses the underlying network");
        }

        builder.setBlocking(true);
        builder.setUnderlyingNetworks(null);
        builder.setConfigureIntent(PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                pendingIntentFlags()));
        return builder.establish();
    }

    private void startTapBridge(VpnTunConfig config, FileDescriptor tunFd, FileDescriptor appEnd) {
        Config cfg = Config.get(this);
        String ipStr = config.hasIfconfig() ? config.ip4 : cfg.vpnTapStaticIp().trim();
        String maskStr = config.hasIfconfig() ? config.netmask4 : cfg.vpnTapNetmask().trim();
        int ourIp = TapBridge.parseIp(ipStr);
        int netmask = TapBridge.parseIp(maskStr);
        int gateway = config.firstGateway().isEmpty()
                ? TapBridge.parseIp(cfg.vpnTapGateway().trim())
                : TapBridge.parseIp(config.firstGateway());
        byte[] mac = OpenVpnProfileStore.tapMac(this);
        TapBridge bridge = new TapBridge(this, appEnd, tunFd, mac, ourIp, netmask, gateway,
                config.mtu > 0 ? config.mtu : 1500);
        tapBridge = bridge;
        bridge.start();
    }

    @Override
    public void onStateChanged(String detailedState, String stateRemote) {
        if (stateRemote != null && !stateRemote.isEmpty()) {
            remote = stateRemote;
            OpenVpnStateStore.setRemote(this, remote);
        }
        if (OpenVpnStateStore.STATE_CONNECTED.equals(detailedState)) {
            reachedConnected = true;
        }
        OpenVpnStateStore.setState(this, detailedState, null);
        updateNotification(false);
    }

    @Override
    public void onByteCount(long rx, long tx) {
        rxBytes = rx;
        txBytes = tx;
        // Persist and notify the open activity; the notification uses the
        // in-memory values directly and keeps its own update throttle.
        OpenVpnStateStore.setByteCounts(this, rx, tx);
        updateNotification(true);
    }

    @Override
    public String vpnUsername() {
        return Config.get(this).vpnUsername();
    }

    @Override
    public String vpnPassword() {
        return Config.get(this).vpnPassword();
    }

    @Override
    public String vpnKeyPassphrase() {
        return Config.get(this).vpnKeyPassphrase();
    }

    @Override
    public void onFatal(final String reason) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                OpenVpnStateStore.setState(OpenVpnService.this, OpenVpnStateStore.STATE_ERROR, reason);
            }
        });
    }

    // ---- notification ------------------------------------------------------

    private void startForegroundVpn(String state) {
        OpenVpnStateStore.setState(this, state, "");
        ensureNotificationChannel();
        Notification notification = buildNotification();
        // The specialUse FGS type (and its manifest value) exist from API 34;
        // on older releases the 2-arg overload is the safe path.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    /**
     * Moves a live session's notification to the channel the user just chose.
     * A tunnel is not worth tearing down for a cosmetic setting, so this
     * re-posts in place; when nothing is connected there is nothing to move.
     */
    static void refreshNotification(Context context) {
        OpenVpnService service = activeService;
        if (service == null) {
            return;
        }
        service.ensureNotificationChannel();
        service.updateNotification(false);
    }

    private void updateNotification(boolean throttled) {
        long now = SystemClock.elapsedRealtime();
        if (throttled && now - lastNotificationAt < NOTIFICATION_MIN_INTERVAL_MS) {
            return;
        }
        lastNotificationAt = now;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        String state = OpenVpnStateStore.state(this);
        StringBuilder text = new StringBuilder(OpenVpnStateStore.label(state));
        if (!remote.isEmpty()) {
            text.append(" · ").append(remote);
        }
        if (OpenVpnStateStore.STATE_CONNECTED.equals(state)) {
            text.append(" · ↓").append(formatBytes(rxBytes)).append(" ↑").append(formatBytes(txBytes));
        }
        Intent disconnect = new Intent(this, OpenVpnService.class).setAction(ACTION_DISCONNECT);
        PendingIntent disconnectIntent = PendingIntent.getService(this, 0, disconnect, pendingIntentFlags());
        Notification.Builder builder = new Notification.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_stat_system_manager)
                .setContentTitle("System Manager VPN")
                .setContentText(text.toString())
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), pendingIntentFlags()))
                .addAction(R.drawable.ic_stat_system_manager, "Disconnect", disconnectIntent);
        ServiceNotifications.applyBehavior(builder,
                ServiceNotifications.shown(this, ServiceNotifications.VPN));
        return builder.build();
    }

    private void stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void ensureNotificationChannel() {
        channelId = ServiceNotifications.channel(
                this,
                CHANNEL_ID,
                "VPN",
                "Shows the embedded OpenVPN connection state.",
                NotificationManager.IMPORTANCE_LOW,
                ServiceNotifications.shown(this, ServiceNotifications.VPN));
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    // ---- helpers -----------------------------------------------------------

    private void safeDns(VpnService.Builder builder, String dns) {
        try {
            builder.addDnsServer(dns);
        } catch (RuntimeException e) {
            LogStore.append(this, "vpn", "skipped invalid DNS " + dns);
        }
    }

    private void addInterfaceRoutes(VpnService.Builder builder, Set<String> installedRoutes,
                                    VpnTunConfig config, String ip4, int prefix4) {
        int local = TapBridge.parseIp(ip4);
        if ("p2p".equals(config.topology)) {
            // In p2p topology OpenVPN reports the peer in the IFCONFIG
            // "netmask" position. The local address is /32, so the peer needs
            // its own host route for replies to enter the tunnel.
            int peer = TapBridge.parseIp(config.netmask4);
            if (peer != 0) {
                addVpnRoute(builder, installedRoutes,
                        TapBridge.ipToString(peer), 32, "VPN peer");
            }
        } else if (local != 0) {
            // subnet and net30 both have a real connected prefix. For net30 the
            // prefix is fixed at /30 by prefixFor(), regardless of the peer
            // address carried in netmask4.
            int mask = prefix4 == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefix4));
            addVpnRoute(builder, installedRoutes,
                    TapBridge.ipToString(local & mask), prefix4, "VPN interface subnet");
        }

        if (config.ip6 != null && !config.ip6.isEmpty()) {
            addIpv6InterfaceRoute(builder, installedRoutes, config.ip6);
        }
    }

    private void addIpv6InterfaceRoute(VpnService.Builder builder, Set<String> installedRoutes,
                                       String addressWithPrefix) {
        String[] parts = addressWithPrefix.split("/");
        if (parts.length != 2) {
            return;
        }
        int prefix = parseIntSafe(parts[1], -1);
        if (prefix < 0 || prefix > 128) {
            return;
        }
        try {
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            if (network.length != 16) {
                return;
            }
            for (int bit = prefix; bit < 128; bit++) {
                network[bit / 8] &= (byte) ~(1 << (7 - (bit % 8)));
            }
            addVpnRoute(builder, installedRoutes,
                    InetAddress.getByAddress(network).getHostAddress(), prefix,
                    "VPN IPv6 interface subnet");
        } catch (Exception e) {
            LogStore.append(this, "vpn", "skipped invalid interface route "
                    + addressWithPrefix);
        }
    }

    private void addVpnRoute(VpnService.Builder builder, Set<String> installedRoutes,
                             String address, int prefix, String label) {
        String key = address + "/" + prefix;
        if (!installedRoutes.add(key)) {
            return;
        }
        try {
            builder.addRoute(address, prefix);
        } catch (RuntimeException e) {
            LogStore.append(this, "vpn", "skipped invalid route " + label);
        }
    }

    private static int prefixFor(String topology, String netmask) {
        if ("net30".equals(topology)) {
            return 30;
        }
        if ("p2p".equals(topology)) {
            return 32;
        }
        int mask = TapBridge.parseIp(netmask);
        return mask == 0 ? 24 : maskToPrefix(mask);
    }

    private static int maskToPrefix(int mask) {
        return Integer.bitCount(mask);
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
