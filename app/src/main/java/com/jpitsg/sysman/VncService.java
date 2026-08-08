package com.jpitsg.sysman;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hosts the VNC server.
 *
 * <p>The service runs for as long as the feature is armed, not only while it is
 * serving. That is what lets the availability conditions work at all: a
 * connection callback firing in the background cannot start a foreground
 * service on Android 12 and up, so something already foreground has to be
 * holding the watcher when the connection changes. Armed-but-not-serving is
 * the {@code WAITING} state, the same shape the beacon uses when no battery
 * rule matches.
 *
 * <p>Listening and connected states use separate notification channels. The
 * idle listener can be hidden by preference, while an attached remote-control
 * client is always announced and gets a one-tap Disconnect action.
 *
 * <p>The foreground type is {@code specialUse} rather than {@code dataSync} for
 * the reason recorded on {@link RemoteLinkService}: from Android 15 a dataSync
 * service is force-stopped after six hours in a day, which a server meant to
 * stay reachable cannot live with.
 */
public final class VncService extends Service
        implements VncNetworkWatcher.Listener, VncServer.Listener {
    /** Re-evaluate; leaves a manual hold in place. */
    static final String ACTION_SYNC = "com.jpitsg.sysman.action.VNC_SYNC";
    /** Explicit user start; clears a manual hold. */
    static final String ACTION_START = "com.jpitsg.sysman.action.VNC_START";
    /** Explicit user stop while still armed; sets a manual hold. */
    static final String ACTION_HOLD = "com.jpitsg.sysman.action.VNC_HOLD";
    /** Carries a fresh MediaProjection consent result in from the activity. */
    static final String ACTION_AUTHORIZE = "com.jpitsg.sysman.action.VNC_AUTHORIZE";
    /** Drops the attached client but leaves the VNC server listening. */
    static final String ACTION_DISCONNECT = "com.jpitsg.sysman.action.VNC_DISCONNECT";
    static final String EXTRA_REASON = "reason";
    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_RESULT_DATA = "result_data";

    private static final String CHANNEL_ID = "system_manager_vnc";
    private static final String LISTENING_CHANNEL_ID = "system_manager_vnc_listening";
    private static final String CONNECTED_CHANNEL_ID = "system_manager_vnc_connected";
    private static final int NOTIFICATION_ID = 0x5306;
    private static final int DISCONNECT_REQUEST_CODE = 0x5307;

    private static volatile boolean active;
    /**
     * Held here so a client can claim the authorised projection when it starts
     * capturing. Android 14 permits one virtual-display creation per projection;
     * the frame source therefore ends that projection when its capture session
     * ends instead of trying to reuse an already-spent token.
     */
    private static volatile MediaProjection projection;

    /** The live projection, or null when screen capture has not been authorised. */
    static MediaProjection activeProjection() {
        return projection;
    }

    /**
     * Set when the user stops the server by hand while it is still armed, so
     * the conditions do not immediately start it again. Cleared by the next real
     * connection transition, or by an explicit start.
     */
    private volatile boolean manualHold;
    /** Stops an evaluation that was already in flight from writing after teardown. */
    private volatile boolean destroyed;
    /** Set for the one startForeground that has to precede getMediaProjection. */
    private volatile boolean pendingProjectionType;

    private VncNetworkWatcher watcher;
    private VncServer server;
    private ExecutorService evaluationExecutor;
    private String listeningChannelId = LISTENING_CHANNEL_ID;
    private BroadcastReceiver vpnInterfaceReceiver;
    private String vpnInterfaceSignature = "";

    static boolean isActive() {
        return active;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        active = true;
        evaluationExecutor = Executors.newSingleThreadExecutor();
        watcher = new VncNetworkWatcher(this, this);
        registerVpnInterfaceReceiver();
        ensureNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SYNC : intent.getAction();
        String reason = intent == null ? "restart" : intent.getStringExtra(EXTRA_REASON);
        if (reason == null) {
            reason = "unknown";
        }

        if (ACTION_START.equals(action) && manualHold) {
            manualHold = false;
            LogStore.append(this, "vnc", "Manual hold cleared by start reason=" + reason);
        } else if (ACTION_HOLD.equals(action)) {
            manualHold = true;
            LogStore.append(this, "vnc", "Held by hand reason=" + reason);
        }

        boolean authorizing = ACTION_AUTHORIZE.equals(action) && intent != null;
        if (authorizing) {
            // The foreground service has to already be running with the
            // mediaProjection type before getMediaProjection is called, or
            // Android 14 and up refuses the token outright.
            pendingProjectionType = true;
        }
        if (!startForegroundServer()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (ACTION_DISCONNECT.equals(action)) {
            requestClientDisconnect(reason);
        }
        if (authorizing) {
            adoptProjection(intent.getIntExtra(EXTRA_RESULT_CODE, 0),
                    (Intent) intent.getParcelableExtra(EXTRA_RESULT_DATA));
        }
        requestEvaluation(reason);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        active = false;
        destroyed = true;
        unregisterVpnInterfaceReceiver();
        // No join here: onDestroy runs on the main thread and waiting on the
        // accept and frame threads would risk an ANR. Closing the sockets is
        // what ends them.
        VncServer closing = server;
        server = null;
        if (closing != null) {
            closing.stop(false);
        }
        releaseProjection();
        if (watcher != null) {
            watcher.stop();
        }
        if (evaluationExecutor != null) {
            evaluationExecutor.shutdownNow();
            evaluationExecutor = null;
        }
        VncStateStore.setListenAddress(this, "");
        if (Config.get(this).vncEnabled() && VncStateStore.isLiveState(VncStateStore.state(this))) {
            // Still armed and still claiming to be live, so nobody asked for
            // this: task killed, low memory, or the system reclaimed us. Leave
            // a truthful state behind rather than a stale "listening". A
            // deliberate stop has already settled the state to OFF.
            VncStateStore.setState(this, VncStateStore.STATE_ERROR, "Service stopped");
        } else if (!Config.get(this).vncEnabled()) {
            VncStateStore.setState(this, VncStateStore.STATE_OFF, "");
        }
        LogStore.append(this, "vnc", "Service stopped");
        super.onDestroy();
    }

    @Override
    public void onConnectionChanged(String reason, boolean transitioned,
                                    VncNetworkWatcher.NetworkSnapshot snapshot) {
        if (transitioned && manualHold) {
            manualHold = false;
            LogStore.append(this, "vnc", "Manual hold cleared by connection change reason=" + reason);
        }
        // Hand the already-read snapshot to the worker rather than evaluating on
        // the watcher's thread: every evaluation has to be serialised on one
        // thread, or a callback and a settings change can settle the state
        // against each other.
        postEvaluation("connection:" + reason, snapshot);
    }

    // ---- Condition evaluation ----------------------------------------------

    /**
     * Queues an evaluation onto the worker. Reading the Wi-Fi state can block
     * for over a second, so none of this may run on the main thread.
     */
    private void requestEvaluation(final String reason) {
        submit(new Runnable() {
            @Override
            public void run() {
                VncNetworkWatcher.NetworkSnapshot snapshot =
                        VncNetworkWatcher.rulesNeedState(VncService.this)
                                ? VncNetworkWatcher.readNetwork(VncService.this)
                                : null;
                applyEvaluation(reason, snapshot);
            }
        }, reason);
    }

    private void postEvaluation(final String reason, final VncNetworkWatcher.NetworkSnapshot snapshot) {
        submit(new Runnable() {
            @Override
            public void run() {
                applyEvaluation(reason, snapshot);
            }
        }, reason);
    }

    private void submit(Runnable task, String reason) {
        ExecutorService executor = evaluationExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }
        try {
            executor.execute(task);
        } catch (RuntimeException e) {
            LogStore.append(this, "vnc", "Could not queue evaluation reason=" + reason
                    + ": " + e.getClass().getSimpleName());
        }
    }

    /**
     * The state machine. Order matters: a missing precondition outranks the
     * conditions, and a manual hold outranks a condition that would otherwise
     * start us.
     */
    private void applyEvaluation(String reason, VncNetworkWatcher.NetworkSnapshot snapshot) {
        if (destroyed) {
            return;
        }
        if (!Config.get(this).vncEnabled()) {
            LogStore.append(this, "vnc", "Disabled; stopping reason=" + reason);
            stopServer();
            syncWatcher();
            stopSelf();
            return;
        }

        syncWatcher();
        if (watcher != null) {
            watcher.rememberSnapshot(snapshot);
        }

        String blocking = VncManager.blockingReason(this);
        if (blocking != null) {
            stopServer();
            settle(VncStateStore.STATE_BLOCKED, blocking, reason);
            return;
        }
        if (manualHold) {
            stopServer();
            settle(VncStateStore.STATE_OFF, VncNetworkWatcher.rulesNeedState(this)
                    ? "Stopped by hand; the next connection change re-arms it"
                    : "Stopped by hand; tap Start to re-arm it", reason);
            return;
        }
        boolean wantsProjection = Config.VNC_ENGINE_PROJECTION.equals(Config.get(this).vncEngine());
        if (!wantsProjection && projection != null) {
            // Switched back to Accessibility: drop the token so the system's
            // screen-recording indicator does not linger over nothing. The
            // connected session also owns that projection, so it must be
            // replaced rather than left frozen on its last frame.
            LogStore.append(this, "vnc", "Releasing screen capture; engine is now Accessibility");
            releaseProjection();
            stopServer();
        }
        if (wantsProjection && projection == null) {
            // Not BLOCKED: nothing is wrong, it just needs a tap that cannot be
            // automated because the token is single-use.
            stopServer();
            settle(VncStateStore.STATE_CONSENT, "Tap to authorise screen capture", reason);
            return;
        }

        VncNetworkWatcher.Verdict verdict = VncNetworkWatcher.evaluate(this, snapshot);
        if (!verdict.shouldRun) {
            stopServer();
            settle(VncStateStore.STATE_WAITING, verdict.reason, reason);
            return;
        }
        startServer(reason);
    }

    // ---- Screen capture consent ---------------------------------------------

    /** Turns a consent result into a live projection, once. */
    private void adoptProjection(int resultCode, Intent resultData) {
        pendingProjectionType = false;
        if (resultData == null) {
            LogStore.append(this, "vnc", "Screen capture consent arrived with no token");
            return;
        }
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            return;
        }
        releaseProjection();
        try {
            final MediaProjection adopted = manager.getMediaProjection(resultCode, resultData);
            if (adopted == null) {
                LogStore.append(this, "vnc", "Screen capture consent produced no projection");
                return;
            }
            // Publish before registering: if the system has already stopped the
            // session, an immediate callback must be able to invalidate it.
            projection = adopted;
            adopted.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    // The user revoked it from the system UI, or the platform
                    // took it back. Ignore a late callback from a projection
                    // that was replaced by a newer authorisation.
                    if (projection != adopted) {
                        return;
                    }
                    LogStore.append(VncService.this, "vnc", "Screen capture stopped by the system");
                    projection = null;
                    requestEvaluation("projection-stopped");
                }
            }, null);
            LogStore.append(this, "vnc", "Screen capture authorised");
        } catch (RuntimeException e) {
            releaseProjection();
            LogStore.append(this, "vnc", "Screen capture token rejected: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            VncStateStore.setState(this, VncStateStore.STATE_ERROR,
                    "Screen capture was not authorised: " + e.getMessage());
        }
    }

    private void releaseProjection() {
        MediaProjection current = projection;
        projection = null;
        if (current != null) {
            try {
                current.stop();
            } catch (RuntimeException ignored) {
            }
        }
    }

    // ---- Server lifecycle ---------------------------------------------------

    private void startServer(String reason) {
        int port = Config.get(this).vncPort();
        VncServer current = server;
        if (current != null && current.isRunning() && current.port() == port) {
            refreshListenAddress(VncServer.localAddress(), port, "interface-refresh");
            return;
        }
        stopServer();
        settle(VncStateStore.STATE_STARTING, "", reason);
        VncServer starting = new VncServer(this, this);
        if (!starting.start(port)) {
            // start() already reported the failure through onFailed.
            return;
        }
        server = starting;
    }

    private void stopServer() {
        VncServer current = server;
        server = null;
        if (current != null) {
            current.stop();
            VncStateStore.setListenAddress(this, "");
            VncStateStore.setClientAddress(this, "");
        }
    }

    private void requestClientDisconnect(final String reason) {
        submit(new Runnable() {
            @Override
            public void run() {
                VncServer current = server;
                boolean requested = current != null
                        && current.disconnectClient("disconnected by user");
                LogStore.append(VncService.this, "vnc", "Notification disconnect result="
                        + requested + " reason=" + reason);
            }
        }, "client-disconnect");
    }

    @Override
    public void onListening(final String address, final int port) {
        submit(new Runnable() {
            @Override
            public void run() {
                refreshListenAddress(address, port, "server-listening");
                settle(VncStateStore.STATE_LISTENING, "", "server-listening");
            }
        }, "server-listening");
    }

    private void refreshListenAddress(String address, int port, String reason) {
        String next = (address.isEmpty() ? "0.0.0.0" : address) + ":" + port;
        String previous = VncStateStore.listenAddress(this);
        VncStateStore.setListenAddress(this, next);
        if (!next.equals(previous)) {
            LogStore.append(this, "vnc", "Preferred listening endpoint changed to "
                    + next + " reason=" + reason + "; socket remains bound to all interfaces");
            updateNotification();
        }
    }

    @Override
    public void onClientConnected(final String address) {
        submit(new Runnable() {
            @Override
            public void run() {
                VncStateStore.setClientAddress(VncService.this, address);
                settle(VncStateStore.STATE_CONNECTED, "", "client-connected");
            }
        }, "client-connected");
    }

    @Override
    public void onClientDisconnected(final String address, final String reason) {
        submit(new Runnable() {
            @Override
            public void run() {
                VncStateStore.setClientAddress(VncService.this, "");
                if (server != null && server.isRunning()) {
                    settle(VncStateStore.STATE_LISTENING, "Last client: " + reason, "client-closed");
                }
            }
        }, "client-closed");
    }

    @Override
    public void onFailed(final String message) {
        submit(new Runnable() {
            @Override
            public void run() {
                settle(VncStateStore.STATE_ERROR, message, "server-failed");
            }
        }, "server-failed");
    }

    private void settle(String state, String detail, String reason) {
        String previous = VncStateStore.state(this);
        String previousDetail = VncStateStore.detail(this);
        VncStateStore.setState(this, state, detail);
        if (!state.equals(previous) || !detail.equals(previousDetail)) {
            LogStore.append(this, "vnc", VncStateStore.label(state) + ": " + detail
                    + " reason=" + reason);
            updateNotification();
        }
    }

    /** The watcher is only worth registering while a condition actually needs it. */
    private void syncWatcher() {
        VncNetworkWatcher current = watcher;
        if (current == null) {
            return;
        }
        boolean wanted = Config.get(this).vncEnabled() && VncNetworkWatcher.rulesNeedState(this);
        if (wanted) {
            // start() is also a configuration sync: it replaces registrations
            // when the selected condition types have changed.
            current.start();
        } else {
            current.stop();
        }
    }

    /**
     * Availability may not depend on VPN state, but the preferred endpoint
     * shown to the user still must change when the TUN interface appears or
     * disappears. The change marker filters high-frequency byte-count updates,
     * and the reachability signature avoids duplicate state evaluations.
     */
    private void registerVpnInterfaceReceiver() {
        if (vpnInterfaceReceiver != null) {
            return;
        }
        vpnInterfaceSignature = currentVpnInterfaceSignature();
        vpnInterfaceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && !OpenVpnStateStore.CHANGE_STATE.equals(
                        intent.getStringExtra(OpenVpnStateStore.EXTRA_CHANGE))) {
                    return;
                }
                String next = currentVpnInterfaceSignature();
                if (next.equals(vpnInterfaceSignature)) {
                    return;
                }
                vpnInterfaceSignature = next;
                if (Config.get(VncService.this).vncEnabled()) {
                    requestEvaluation("vpn-interface:" + next);
                }
            }
        };
        IntentFilter filter = new IntentFilter(OpenVpnStateStore.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnInterfaceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(vpnInterfaceReceiver, filter);
        }
    }

    private void unregisterVpnInterfaceReceiver() {
        BroadcastReceiver receiver = vpnInterfaceReceiver;
        vpnInterfaceReceiver = null;
        if (receiver == null) {
            return;
        }
        try {
            unregisterReceiver(receiver);
        } catch (RuntimeException ignored) {
        }
    }

    private String currentVpnInterfaceSignature() {
        boolean connected = OpenVpnService.isActive()
                && OpenVpnStateStore.STATE_CONNECTED.equals(OpenVpnStateStore.state(this));
        return connected ? "connected:" + VncServer.vpnAddress() : "disconnected";
    }

    // ---- Foreground plumbing ------------------------------------------------

    private boolean startForegroundServer() {
        ensureNotificationChannels();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                if (pendingProjectionType || projection != null) {
                    // Only claimed when there is or is about to be a projection:
                    // no reason to wear the screen-recording type otherwise.
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                }
                startForeground(NOTIFICATION_ID, buildNotification(), type);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // specialUse was added in API 34. dataSync has no duration cap
                // on these releases and is the compatible network-server type.
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                if (pendingProjectionType || projection != null) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                }
                startForeground(NOTIFICATION_ID, buildNotification(), type);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
            return true;
        } catch (RuntimeException e) {
            LogStore.append(this, "vnc", "Foreground start failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            VncStateStore.setState(this, VncStateStore.STATE_ERROR,
                    "Foreground service refused: " + e.getMessage());
            return false;
        }
    }

    private void updateNotification() {
        ensureNotificationChannels();
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            manager.notify(NOTIFICATION_ID, buildNotification());
        } catch (RuntimeException ignored) {
        }
    }

    private Notification buildNotification() {
        String state = VncStateStore.state(this);
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (VncStateStore.STATE_CONSENT.equals(state)) {
            // The only route back to a consent dialog is an activity, so the
            // notification is what an unattended start has to fall back to.
            open.putExtra(MainActivity.EXTRA_REQUEST_PROJECTION, true);
        }
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String channelId = CHANNEL_ID;
        if (VncStateStore.STATE_LISTENING.equals(state)) {
            channelId = listeningChannelId;
        } else if (VncStateStore.STATE_CONNECTED.equals(state)) {
            channelId = CONNECTED_CHANNEL_ID;
        }

        String text = notificationText(state);
        Notification.Builder builder = new Notification.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_stat_system_manager)
                .setContentTitle("VNC server")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pending)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setLocalOnly(true);
        boolean listeningShown = !VncStateStore.STATE_LISTENING.equals(state)
                || Config.get(this).vncShowListeningNotification();
        ServiceNotifications.applyBehavior(builder, listeningShown);
        if (VncStateStore.STATE_CONNECTED.equals(state)) {
            Intent disconnect = new Intent(this, VncService.class)
                    .setAction(ACTION_DISCONNECT)
                    .putExtra(EXTRA_REASON, "notification-action");
            PendingIntent disconnectPending = PendingIntent.getService(
                    this,
                    DISCONNECT_REQUEST_CODE,
                    disconnect,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Action disconnectAction = new Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat_system_manager),
                    "Disconnect",
                    disconnectPending)
                    .build();
            builder.addAction(disconnectAction);
        }
        return builder.build();
    }

    private String notificationText(String state) {
        String detail = VncStateStore.detail(this);
        if (VncStateStore.STATE_CONNECTED.equals(state)) {
            String client = VncStateStore.clientAddress(this);
            return client.isEmpty() ? "Client connected" : "Client connected from " + client;
        }
        if (VncStateStore.STATE_LISTENING.equals(state)) {
            String listen = VncStateStore.listenAddress(this);
            return listen.isEmpty() ? "Listening" : "Listening on " + listen;
        }
        String label = VncStateStore.label(state);
        return detail.isEmpty() ? label : label + " — " + detail;
    }

    private void ensureNotificationChannels() {
        listeningChannelId = ServiceNotifications.channel(
                this,
                LISTENING_CHANNEL_ID,
                "VNC server listening",
                "Shows the address while the VNC server is waiting for a client.",
                NotificationManager.IMPORTANCE_LOW,
                Config.get(this).vncShowListeningNotification());

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel statusChannel = new NotificationChannel(
                CHANNEL_ID,
                "VNC server status",
                NotificationManager.IMPORTANCE_LOW);
        statusChannel.setDescription("Shows VNC startup, waiting, consent and error states.");
        statusChannel.setShowBadge(false);
        statusChannel.setSound(null, null);

        NotificationChannel connectedChannel = new NotificationChannel(
                CONNECTED_CHANNEL_ID,
                "VNC client connected",
                NotificationManager.IMPORTANCE_LOW);
        connectedChannel.setDescription(
                "Shows when a remote-control client is attached to the device.");
        connectedChannel.setShowBadge(false);
        connectedChannel.setSound(null, null);
        try {
            manager.createNotificationChannel(statusChannel);
            manager.createNotificationChannel(connectedChannel);
        } catch (RuntimeException e) {
            LogStore.append(this, "vnc", "Channel setup failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
