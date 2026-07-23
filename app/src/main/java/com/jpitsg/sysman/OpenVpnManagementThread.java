package com.jpitsg.sysman;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Speaks the openvpn management protocol over a unix-domain socket. openvpn is
 * launched with --management-client, so it connects to our listener; we drive
 * hold release, feed passwords, collect the pushed tun config, pass the tun fd
 * back, and mirror state/byte-count events to the service.
 *
 * See doc/management-notes.txt in the openvpn tree for the line protocol.
 */
final class OpenVpnManagementThread implements Runnable {

    interface Host {
        /** VpnService.protect on the raw socket fd; return false to reject. */
        boolean protectSocket(int fd);

        /**
         * Establish the VpnService interface from the accumulated config and
         * return the FileDescriptor to hand back to openvpn (the tun fd for
         * dev tun, or the openvpn end of the tap socketpair for dev tap).
         * Returns null on failure (openvpn will be told to cancel).
         */
        FileDescriptor establishAndBuildFd(VpnTunConfig config);

        /** Called right after the OPENTUN fd is delivered, so a socketpair end can be closed. */
        void afterTunFdSent();

        void onStateChanged(String detailedState, String remote);

        void onByteCount(long rxBytes, long txBytes);

        String vpnUsername();

        String vpnPassword();

        String vpnKeyPassphrase();

        void onFatal(String reason);
    }

    private final Context context;
    private final String socketPath;
    private final Host host;

    private final Object writeLock = new Object();
    private volatile LocalServerSocket serverSocket;
    private volatile LocalSocket socket;
    private volatile OutputStream out;
    private volatile boolean stopRequested;

    private VpnTunConfig pending = new VpnTunConfig();
    private volatile String appliedSignature = "";
    private volatile boolean tunAlive;

    OpenVpnManagementThread(Context context, String socketPath, Host host) {
        this.context = context.getApplicationContext();
        this.socketPath = socketPath;
        this.host = host;
    }

    /** Binds the listener; call before launching openvpn so accept() is armed. */
    void bind() throws IOException {
        // A stale socket file from a crash would give EADDRINUSE.
        java.io.File stale = new java.io.File(socketPath);
        if (stale.exists()) {
            //noinspection ResultOfMethodCallIgnored
            stale.delete();
        }
        LocalSocket ls = new LocalSocket();
        ls.bind(new LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM));
        serverSocket = new LocalServerSocket(ls.getFileDescriptor());
    }

    @Override
    public void run() {
        try {
            LocalServerSocket server = serverSocket;
            if (server == null) {
                LogStore.append(context, "vpn", "management listener not bound");
                return;
            }
            LocalSocket accepted = server.accept();
            socket = accepted;
            out = accepted.getOutputStream();
            LogStore.append(context, "vpn", "openvpn connected to management socket");
            managementCommand("version 3");
            pumpLoop(accepted);
        } catch (Exception e) {
            if (!stopRequested) {
                LogStore.append(context, "vpn", "management loop error: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } finally {
            closeQuietly();
        }
    }

    private void pumpLoop(LocalSocket accepted) throws IOException {
        InputStream in = accepted.getInputStream();
        byte[] buffer = new byte[8192];
        StringBuilder acc = new StringBuilder();
        FileDescriptor[] pendingFds = null;
        while (!stopRequested) {
            int n = in.read(buffer);
            if (n < 0) {
                break;
            }
            FileDescriptor[] ancillary = accepted.getAncillaryFileDescriptors();
            if (ancillary != null && ancillary.length > 0) {
                pendingFds = ancillary;
            }
            acc.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            int newline;
            while ((newline = indexOfNewline(acc)) >= 0) {
                String line = acc.substring(0, newline);
                acc.delete(0, newline + 1);
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                pendingFds = handleLine(line, pendingFds);
            }
        }
    }

    private static int indexOfNewline(StringBuilder sb) {
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }

    private FileDescriptor[] handleLine(String line, FileDescriptor[] pendingFds) {
        if (line.isEmpty()) {
            return pendingFds;
        }
        if (line.startsWith(">HOLD:")) {
            managementCommand("hold release");
            managementCommand("bytecount 2");
            managementCommand("state on");
            return pendingFds;
        }
        if (line.startsWith(">STATE:")) {
            handleState(line.substring(">STATE:".length()));
            return pendingFds;
        }
        if (line.startsWith(">BYTECOUNT:")) {
            handleByteCount(line.substring(">BYTECOUNT:".length()));
            return pendingFds;
        }
        if (line.startsWith(">PASSWORD:")) {
            handlePassword(line.substring(">PASSWORD:".length()));
            return pendingFds;
        }
        if (line.startsWith(">NEED-OK:")) {
            return handleNeedOk(line.substring(">NEED-OK:".length()), pendingFds);
        }
        if (line.startsWith(">FATAL:")) {
            String reason = line.substring(">FATAL:".length()).trim();
            LogStore.append(context, "vpn", "openvpn FATAL: " + reason);
            host.onFatal(reason);
            return pendingFds;
        }
        if (line.startsWith(">LOG:") || line.startsWith(">INFO:")) {
            return pendingFds; // stdout already carries the log at verb level
        }
        if (line.startsWith("SUCCESS:") || line.startsWith(">") || line.startsWith("ERROR:")) {
            if (line.startsWith("ERROR:")) {
                LogStore.append(context, "vpn", "management " + line);
            }
            return pendingFds;
        }
        return pendingFds;
    }

    private void handleState(String payload) {
        // ts,NAME,description,tunip,remip,remport,localip,localport[,ip6]
        String[] parts = payload.split(",", -1);
        if (parts.length < 2) {
            return;
        }
        String name = parts[1];
        String remote = "";
        if (parts.length >= 6 && !parts[4].isEmpty()) {
            remote = parts[4] + (parts[5].isEmpty() ? "" : ":" + parts[5]);
        }
        String mapped = mapState(name);
        if (OpenVpnStateStore.STATE_RECONNECTING.equals(mapped)) {
            // openvpn re-pushes the whole config before the next OPENTUN.
            pending = new VpnTunConfig();
        }
        if (mapped != null) {
            host.onStateChanged(mapped, remote);
        }
    }

    private static String mapState(String name) {
        switch (name) {
            case "CONNECTING":
            case "WAIT":
            case "RESOLVE":
            case "TCP_CONNECT":
                return OpenVpnStateStore.STATE_CONNECTING;
            case "AUTH":
            case "AUTH_PENDING":
                return OpenVpnStateStore.STATE_AUTH;
            case "GET_CONFIG":
                return OpenVpnStateStore.STATE_GET_CONFIG;
            case "ASSIGN_IP":
            case "ADD_ROUTES":
                return OpenVpnStateStore.STATE_ASSIGN_IP;
            case "CONNECTED":
                return OpenVpnStateStore.STATE_CONNECTED;
            case "RECONNECTING":
                return OpenVpnStateStore.STATE_RECONNECTING;
            case "EXITING":
                return OpenVpnStateStore.STATE_EXITING;
            default:
                return null;
        }
    }

    private void handleByteCount(String payload) {
        int comma = payload.indexOf(',');
        if (comma < 0) {
            return;
        }
        try {
            long rx = Long.parseLong(payload.substring(0, comma).trim());
            long tx = Long.parseLong(payload.substring(comma + 1).trim());
            host.onByteCount(rx, tx);
        } catch (NumberFormatException ignored) {
        }
    }

    private void handlePassword(String payload) {
        if (payload.startsWith("Verification Failed:")) {
            LogStore.append(context, "vpn", "openvpn authentication failed");
            host.onFatal("authentication failed");
            return;
        }
        // Need 'Auth' username/password  |  Need 'Private Key' password
        String type = extractQuoted(payload);
        if ("Auth".equals(type)) {
            managementCommand("username 'Auth' " + escape(host.vpnUsername()));
            managementCommand("password 'Auth' " + escape(host.vpnPassword()));
        } else if ("Private Key".equals(type)) {
            managementCommand("password 'Private Key' " + escape(host.vpnKeyPassphrase()));
        } else {
            LogStore.append(context, "vpn", "unhandled password request: " + payload);
        }
    }

    private FileDescriptor[] handleNeedOk(String payload, FileDescriptor[] pendingFds) {
        // Need '<type>' ... MSG:<arg>
        String type = extractQuoted(payload);
        int msgIdx = payload.indexOf("MSG:");
        String arg = msgIdx >= 0 ? payload.substring(msgIdx + 4).trim() : "";
        if (type == null) {
            return pendingFds;
        }
        switch (type) {
            case "PROTECTFD": {
                boolean ok = false;
                if (pendingFds != null && pendingFds.length > 0) {
                    ok = protectAndClose(pendingFds[0]);
                }
                managementCommand("needok 'PROTECTFD' " + (ok ? "ok" : "cancel"));
                return null; // fd consumed
            }
            case "IFCONFIG": {
                // local netmask mtu topology
                String[] a = arg.split("\\s+");
                if (a.length >= 1) pending.ip4 = a[0];
                if (a.length >= 2) pending.netmask4 = a[1];
                if (a.length >= 3) pending.mtu = parseInt(a[2], pending.mtu);
                if (a.length >= 4) pending.topology = a[3];
                managementCommand("needok 'IFCONFIG' ok");
                return pendingFds;
            }
            case "IFCONFIG6": {
                String[] a = arg.split("\\s+");
                if (a.length >= 1) pending.ip6 = a[0];
                managementCommand("needok 'IFCONFIG6' ok");
                return pendingFds;
            }
            case "ROUTE": {
                String[] a = arg.split("\\s+");
                if (a.length >= 3) {
                    pending.routes4.add(new VpnTunConfig.Route4(a[0], a[1], a[2]));
                }
                managementCommand("needok 'ROUTE' ok");
                return pendingFds;
            }
            case "ROUTE6": {
                String[] a = arg.split("\\s+");
                if (a.length >= 1) {
                    pending.routes6.add(new VpnTunConfig.Route6(a[0]));
                }
                managementCommand("needok 'ROUTE6' ok");
                return pendingFds;
            }
            case "DNSSERVER":
                if (!arg.isEmpty()) pending.dns4.add(arg.split("\\s+")[0]);
                managementCommand("needok 'DNSSERVER' ok");
                return pendingFds;
            case "DNS6SERVER":
                if (!arg.isEmpty()) pending.dns6.add(arg.split("\\s+")[0]);
                managementCommand("needok 'DNS6SERVER' ok");
                return pendingFds;
            case "DNSDOMAIN":
                if (!arg.isEmpty()) pending.domains.add(arg.split("\\s+")[0]);
                managementCommand("needok 'DNSDOMAIN' ok");
                return pendingFds;
            case "PERSIST_TUN_ACTION":
                // Keep the live interface (and TapBridge) across soft restarts
                // when we still hold it; otherwise force a fresh OPENTUN.
                managementCommand("needok 'PERSIST_TUN_ACTION' " + (tunAlive ? "NOACTION" : "OPEN_BEFORE_CLOSE"));
                return pendingFds;
            case "OPENTUN":
                handleOpenTun();
                return pendingFds;
            case "HTTPPROXY":
                LogStore.append(context, "vpn", "pushed http-proxy ignored");
                managementCommand("needok 'HTTPPROXY' ok");
                return pendingFds;
            default:
                LogStore.append(context, "vpn", "unknown NEED-OK '" + type + "'; replying ok");
                managementCommand("needok '" + type + "' ok");
                return pendingFds;
        }
    }

    private void handleOpenTun() {
        if (!pending.hasIfconfig() && !hasStaticOverride()) {
            host.onFatal("server pushed no IP configuration");
            managementCommand("needok 'OPENTUN' cancel");
            return;
        }
        FileDescriptor fd = host.establishAndBuildFd(pending);
        if (fd == null) {
            managementCommand("needok 'OPENTUN' cancel");
            return;
        }
        synchronized (writeLock) {
            try {
                socket.setFileDescriptorsForSend(new FileDescriptor[]{fd});
                writeLocked("needok 'OPENTUN' ok");
                socket.setFileDescriptorsForSend(null);
            } catch (Exception e) {
                LogStore.append(context, "vpn", "OPENTUN fd send failed: " + e.getMessage());
                host.onFatal("failed to hand tun fd to openvpn");
                return;
            }
        }
        host.afterTunFdSent();
        appliedSignature = pending.signature();
        tunAlive = true;
        pending = new VpnTunConfig();
    }

    private boolean hasStaticOverride() {
        Config config = Config.get(context);
        return !config.vpnTapStaticIp().trim().isEmpty();
    }

    private boolean protectAndClose(FileDescriptor fd) {
        try {
            android.os.ParcelFileDescriptor dup = android.os.ParcelFileDescriptor.dup(fd);
            boolean ok = host.protectSocket(dup.getFd());
            dup.close();
            try {
                android.system.Os.close(fd);
            } catch (Exception ignored) {
            }
            return ok;
        } catch (Exception e) {
            LogStore.append(context, "vpn", "protect fd failed: " + e.getMessage());
            return false;
        }
    }

    /** Ask openvpn to stop cleanly. */
    void sendSigint() {
        managementCommand("signal SIGINT");
    }

    void requestStop() {
        stopRequested = true;
    }

    void close() {
        stopRequested = true;
        closeQuietly();
    }

    void markTunDead() {
        tunAlive = false;
    }

    private void managementCommand(String command) {
        synchronized (writeLock) {
            writeLocked(command);
        }
    }

    private void writeLocked(String command) {
        OutputStream o = out;
        if (o == null) {
            return;
        }
        try {
            o.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            o.flush();
        } catch (IOException e) {
            if (!stopRequested) {
                LogStore.append(context, "vpn", "management write failed: " + e.getMessage());
            }
        }
    }

    private void closeQuietly() {
        LocalSocket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        LocalServerSocket ss = serverSocket;
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
        java.io.File file = new java.io.File(socketPath);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static String extractQuoted(String s) {
        int first = s.indexOf('\'');
        if (first < 0) {
            return null;
        }
        int second = s.indexOf('\'', first + 1);
        if (second < 0) {
            return null;
        }
        return s.substring(first + 1, second);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Escapes a management value per doc/management-notes.txt: backslash and
     * double-quote get backslash-escaped; if the value contains whitespace or
     * shell-ish characters (or is empty) it is wrapped in double quotes.
     */
    static String escape(String value) {
        if (value == null) {
            value = "";
        }
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\').append(c);
                changed = true;
            } else if (c == '\n') {
                sb.append("\\n");
                changed = true;
            } else {
                sb.append(c);
            }
        }
        String escaped = sb.toString();
        boolean needsQuotes = changed || value.isEmpty()
                || value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0
                || value.indexOf('#') >= 0 || value.indexOf(';') >= 0 || value.indexOf('\'') >= 0;
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }
}
