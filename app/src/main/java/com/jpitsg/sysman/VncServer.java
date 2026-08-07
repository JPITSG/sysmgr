package com.jpitsg.sysman;

import android.content.Context;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Accepts VNC clients: one at a time, from allowed addresses, with a brake on
 * repeated authentication failures.
 *
 * <p>The throttle is not optional decoration. VNC authentication is eight
 * characters of DES, so an unthrottled server is a few hours of guessing away
 * from a stranger holding the screen and the touchscreen.
 */
final class VncServer implements VncSession.Listener {
    private static final int AUTH_FAILURE_LIMIT = 3;
    private static final long AUTH_BLOCK_MILLIS = 60_000L;
    private static final int ACCEPT_BACKLOG = 2;

    interface Listener {
        void onListening(String address, int port);

        void onClientConnected(String address);

        void onClientDisconnected(String address, String reason);

        void onFailed(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Map<String, AuthFailures> authFailures = new HashMap<>();
    private final AtomicReference<VncSession> session = new AtomicReference<>();

    private volatile boolean running;
    private volatile int port;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    private static final class AuthFailures {
        int count;
        long blockedUntilMillis;
    }

    VncServer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    boolean isRunning() {
        return running;
    }

    int port() {
        return port;
    }

    boolean start(int requestedPort) {
        if (running) {
            return true;
        }
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new java.net.InetSocketAddress(requestedPort), ACCEPT_BACKLOG);
        } catch (IOException e) {
            listener.onFailed("Could not listen on port " + requestedPort + ": " + e.getMessage());
            closeServerSocket();
            return false;
        }

        port = requestedPort;
        running = true;
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "SystemManagerVncAccept");
        acceptThread.start();
        listener.onListening(localAddress(), port);
        LogStore.append(context, "vnc", "Listening on " + localAddress() + ":" + port);
        return true;
    }

    void stop() {
        stop(true);
    }

    /**
     * @param await whether to wait for the accept thread to unwind. Never wait
     *              from the main thread — teardown would block it for seconds.
     *              Closing the socket is what actually ends the loop; the join
     *              only makes a restart deterministic.
     */
    void stop(boolean await) {
        running = false;
        VncSession current = session.getAndSet(null);
        if (current != null) {
            current.stop("server stopped");
        }
        closeServerSocket();
        if (await && acceptThread != null && acceptThread != Thread.currentThread()) {
            try {
                acceptThread.join(2_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        acceptThread = null;
    }

    private void acceptLoop() {
        while (running) {
            Socket client;
            try {
                client = serverSocket.accept();
            } catch (SocketException e) {
                return;
            } catch (IOException e) {
                if (running) {
                    listener.onFailed("Accept failed: " + e.getMessage());
                }
                return;
            }
            handleAccept(client);
        }
    }

    private void handleAccept(Socket client) {
        InetAddress address = client.getInetAddress();
        String host = address == null ? "unknown" : address.getHostAddress();

        String refusal = refusalReason(host);
        if (refusal != null) {
            LogStore.append(context, "vnc", "Refused " + host + ": " + refusal);
            closeQuietly(client);
            return;
        }

        VncSession newSession = new VncSession(context, client, this);
        if (!session.compareAndSet(null, newSession)) {
            LogStore.append(context, "vnc", "Refused " + host + ": another client is connected");
            closeQuietly(client);
            return;
        }
        new Thread(newSession, "SystemManagerVncSession").start();
    }

    /** Why this client may not connect, or null when it may. */
    private String refusalReason(String host) {
        if (isAuthBlocked(host)) {
            return "too many failed authentication attempts";
        }
        if (!allowed(Config.get(context).vncAllowedClients(), host)) {
            return "not in the allowed client list";
        }
        return null;
    }

    // ---- Session callbacks --------------------------------------------------

    @Override
    public void onAuthenticated(VncSession authenticated, String clientAddress) {
        synchronized (authFailures) {
            authFailures.remove(hostOf(clientAddress));
        }
        if (Config.get(context).vncWakeOnConnect()) {
            SystemManagerAccessibilityService.wakeScreenForVnc();
        }
        LogStore.append(context, "vnc", "Client authenticated: " + clientAddress);
        listener.onClientConnected(clientAddress);
    }

    @Override
    public void onClosed(VncSession closed, String clientAddress, String reason) {
        session.compareAndSet(closed, null);
        LogStore.append(context, "vnc", "Client " + clientAddress + " closed: " + reason);
        listener.onClientDisconnected(clientAddress, reason);
    }

    @Override
    public void onAuthFailed(String clientAddress) {
        String host = hostOf(clientAddress);
        synchronized (authFailures) {
            AuthFailures record = authFailures.get(host);
            if (record == null) {
                record = new AuthFailures();
                authFailures.put(host, record);
            }
            record.count++;
            if (record.count >= AUTH_FAILURE_LIMIT) {
                record.blockedUntilMillis = System.currentTimeMillis() + AUTH_BLOCK_MILLIS;
                record.count = 0;
                LogStore.append(context, "vnc", "Blocking " + host + " for "
                        + (AUTH_BLOCK_MILLIS / 1000) + "s after repeated authentication failures");
            }
        }
    }

    private boolean isAuthBlocked(String host) {
        synchronized (authFailures) {
            AuthFailures record = authFailures.get(host);
            return record != null && record.blockedUntilMillis > System.currentTimeMillis();
        }
    }

    private static String hostOf(String clientAddress) {
        int colon = clientAddress.lastIndexOf(':');
        return colon > 0 ? clientAddress.substring(0, colon) : clientAddress;
    }

    // ---- Allow-list ---------------------------------------------------------

    /** Blank list means any client; entries are plain addresses or IPv4 CIDR. */
    static boolean allowed(String allowList, String host) {
        if (allowList == null || allowList.trim().isEmpty()) {
            return true;
        }
        if (host == null) {
            return false;
        }
        for (String rawEntry : allowList.split(",")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int slash = entry.indexOf('/');
            if (slash < 0) {
                if (entry.equalsIgnoreCase(host)) {
                    return true;
                }
                continue;
            }
            if (matchesCidr(entry.substring(0, slash), entry.substring(slash + 1), host)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCidr(String network, String prefixText, String host) {
        Integer networkBits = parseIpv4(network);
        Integer hostBits = parseIpv4(host);
        if (networkBits == null || hostBits == null) {
            return false;
        }
        int prefix;
        try {
            prefix = Integer.parseInt(prefixText.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (prefix < 0 || prefix > 32) {
            return false;
        }
        if (prefix == 0) {
            return true;
        }
        int mask = (int) (0xFFFFFFFFL << (32 - prefix));
        return (networkBits & mask) == (hostBits & mask);
    }

    /** Dotted-quad only, and deliberately never resolves: this runs on the accept path. */
    private static Integer parseIpv4(String text) {
        String[] parts = text.trim().split("\\.");
        if (parts.length != 4) {
            return null;
        }
        int value = 0;
        for (String part : parts) {
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }
            if (octet < 0 || octet > 255) {
                return null;
            }
            value = (value << 8) | octet;
        }
        return value;
    }

    // ---- Addressing ---------------------------------------------------------

    /** Best guess at the address a client should point at, for the panel. */
    static String localAddress() {
        String fallback = "";
        try {
            List<NetworkInterface> interfaces =
                    Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : interfaces) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address.isLoopbackAddress() || address.getHostAddress() == null
                            || address.getHostAddress().contains(":")) {
                        continue;
                    }
                    String name = networkInterface.getName();
                    if (name != null && name.startsWith("wlan")) {
                        return address.getHostAddress();
                    }
                    if (fallback.isEmpty()) {
                        fallback = address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return fallback;
    }

    private void closeServerSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
