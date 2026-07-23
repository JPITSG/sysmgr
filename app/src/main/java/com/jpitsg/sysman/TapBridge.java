package com.jpitsg.sysman;

import android.content.Context;
import android.system.Os;

import java.io.FileDescriptor;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Userspace TAP (layer-2) emulation for an unrooted VpnService.
 *
 * Android's VpnService gives us only an L3 tun fd, but openvpn in tap mode
 * frames Ethernet. We hand openvpn one end of a SOCK_DGRAM socketpair (so each
 * datagram is exactly one Ethernet frame) and bridge between that and the tun
 * fd: stripping/adding 14-byte Ethernet headers, answering ARP for our address,
 * and resolving destination MACs via ARP. IPv4 only in v1.
 */
final class TapBridge {
    private static final int ETH_HEADER = 14;
    private static final short ETH_TYPE_IPV4 = 0x0800;
    private static final short ETH_TYPE_ARP = 0x0806;
    private static final short ETH_TYPE_IPV6 = (short) 0x86DD;

    private static final long ARP_SOFT_TTL_MS = 120_000L;
    private static final long ARP_HARD_TTL_MS = 600_000L;
    private static final long ARP_MIN_REQUEST_INTERVAL_MS = 300L;
    private static final long PARK_MAX_AGE_MS = 1_000L;

    private final Context context;
    private final FileDescriptor appEnd;   // our end of the openvpn socketpair
    private final FileDescriptor tunFd;    // VpnService tun
    private final byte[] ourMac;
    private final int ourIp;               // network-order int
    private final int netmask;
    private final int gatewayIp;           // 0 if none
    private final int mtu;

    private final ConcurrentHashMap<Integer, ArpEntry> arpCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, PendingPacket> parked = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> lastArpRequestAt = new ConcurrentHashMap<>();

    private volatile boolean running;
    private volatile boolean loggedIpv6;
    private volatile boolean loggedNoGateway;
    private Thread rxThread;
    private Thread txThread;

    TapBridge(Context context, FileDescriptor appEnd, FileDescriptor tunFd, byte[] ourMac,
              int ourIp, int netmask, int gatewayIp, int mtu) {
        this.context = context.getApplicationContext();
        this.appEnd = appEnd;
        this.tunFd = tunFd;
        this.ourMac = ourMac;
        this.ourIp = ourIp;
        this.netmask = netmask;
        this.gatewayIp = gatewayIp;
        this.mtu = mtu;
    }

    void start() {
        running = true;
        rxThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runRx();
            }
        }, "SystemManagerVpnTapRx");
        txThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runTx();
            }
        }, "SystemManagerVpnTapTx");
        rxThread.start();
        txThread.start();
        sendGratuitousArp();
        if (gatewayIp != 0) {
            sendArpRequest(gatewayIp);
        }
        LogStore.append(context, "vpn", "tap bridge started ip=" + ipToString(ourIp)
                + " gw=" + (gatewayIp == 0 ? "none" : ipToString(gatewayIp)) + " mtu=" + mtu);
    }

    void stop() {
        running = false;
        closeQuietly(appEnd);
        if (rxThread != null) {
            join(rxThread);
        }
        if (txThread != null) {
            join(txThread);
        }
    }

    // ---- socketpair -> tun (frames from the L2 network) --------------------

    private void runRx() {
        byte[] frame = new byte[mtu + ETH_HEADER + 64];
        while (running) {
            int n;
            try {
                n = Os.read(appEnd, frame, 0, frame.length);
            } catch (Exception e) {
                break;
            }
            if (n <= 0) {
                break;
            }
            if (n < ETH_HEADER) {
                continue;
            }
            short etherType = readShort(frame, 12);
            if (etherType == ETH_TYPE_ARP) {
                handleArpFrame(frame, n);
            } else if (etherType == ETH_TYPE_IPV4) {
                if (!isForUs(frame)) {
                    continue;
                }
                writeTun(frame, ETH_HEADER, n - ETH_HEADER);
            } else if (etherType == ETH_TYPE_IPV6) {
                if (!loggedIpv6) {
                    loggedIpv6 = true;
                    LogStore.append(context, "vpn", "tap: IPv6 frame dropped (IPv4-only in v1)");
                }
            }
        }
        if (running) {
            LogStore.append(context, "vpn", "tap bridge rx ended");
        }
    }

    private boolean isForUs(byte[] frame) {
        if (macEquals(frame, 0, ourMac)) {
            return true;
        }
        // broadcast
        boolean broadcast = true;
        for (int i = 0; i < 6; i++) {
            if (frame[i] != (byte) 0xFF) {
                broadcast = false;
                break;
            }
        }
        if (broadcast) {
            return true;
        }
        // IPv4 multicast 01:00:5e
        return (frame[0] & 0xFF) == 0x01 && (frame[1] & 0xFF) == 0x00 && (frame[2] & 0xFF) == 0x5E;
    }

    // ---- tun -> socketpair (packets from Android apps) ---------------------

    private void runTx() {
        byte[] packet = new byte[mtu + 64];
        while (running) {
            int n;
            try {
                n = Os.read(tunFd, packet, 0, packet.length);
            } catch (Exception e) {
                break;
            }
            if (n <= 0) {
                break;
            }
            if (n < 20) {
                continue; // not a full IPv4 header
            }
            int version = (packet[0] & 0xF0) >> 4;
            if (version != 4) {
                continue; // IPv6 tun packets dropped in v1
            }
            int dstIp = readInt(packet, 16);
            byte[] dstMac = resolveDstMac(dstIp);
            if (dstMac == null) {
                parkPacket(dstIp, packet, n);
                continue;
            }
            writeFrame(dstMac, ETH_TYPE_IPV4, packet, 0, n);
        }
        if (running) {
            LogStore.append(context, "vpn", "tap bridge tx ended");
        }
    }

    private byte[] resolveDstMac(int dstIp) {
        if (dstIp == 0xFFFFFFFF || dstIp == subnetBroadcast()) {
            return broadcastMac();
        }
        if ((dstIp >>> 28) == 0xE) { // 224.0.0.0/4 multicast
            return multicastMac(dstIp);
        }
        int target = isOnSubnet(dstIp) ? dstIp : gatewayIp;
        if (target == 0) {
            if (!loggedNoGateway) {
                loggedNoGateway = true;
                LogStore.append(context, "vpn", "tap: no gateway for off-subnet traffic; dropping");
            }
            return null;
        }
        ArpEntry entry = arpCache.get(target);
        long now = elapsed();
        if (entry != null) {
            if (now - entry.learnedAt > ARP_HARD_TTL_MS) {
                arpCache.remove(target);
            } else {
                if (now - entry.learnedAt > ARP_SOFT_TTL_MS) {
                    sendArpRequest(target); // refresh in the background, keep using it
                }
                return entry.mac;
            }
        }
        sendArpRequest(target);
        return null;
    }

    private void parkPacket(int dstIp, byte[] packet, int length) {
        int target = isOnSubnet(dstIp) ? dstIp : gatewayIp;
        if (target == 0) {
            return;
        }
        byte[] copy = new byte[length];
        System.arraycopy(packet, 0, copy, 0, length);
        parked.put(dstIp, new PendingPacket(copy, elapsed()));
    }

    // ---- ARP ---------------------------------------------------------------

    private void handleArpFrame(byte[] frame, int length) {
        if (length < ETH_HEADER + 28) {
            return;
        }
        int base = ETH_HEADER;
        int oper = readShort(frame, base + 6) & 0xFFFF;
        int spa = readInt(frame, base + 14);
        int tpa = readInt(frame, base + 24);
        byte[] sha = new byte[6];
        System.arraycopy(frame, base + 8, sha, 0, 6);

        if (oper == 1 && tpa == ourIp) {
            sendArpReply(sha, spa);
        } else if (oper == 2) {
            learnArp(spa, sha);
        }
    }

    private void learnArp(int ip, byte[] mac) {
        arpCache.put(ip, new ArpEntry(mac.clone(), elapsed()));
        // Flush any packets parked for hosts this MAC now serves.
        for (java.util.Map.Entry<Integer, PendingPacket> e : parked.entrySet()) {
            int dstIp = e.getKey();
            int target = isOnSubnet(dstIp) ? dstIp : gatewayIp;
            if (target == ip) {
                PendingPacket p = parked.remove(dstIp);
                if (p != null && elapsed() - p.parkedAt <= PARK_MAX_AGE_MS) {
                    writeFrame(mac, ETH_TYPE_IPV4, p.data, 0, p.data.length);
                }
            }
        }
    }

    private void sendArpRequest(int targetIp) {
        long now = elapsed();
        Long last = lastArpRequestAt.get(targetIp);
        if (last != null && now - last < ARP_MIN_REQUEST_INTERVAL_MS) {
            return;
        }
        lastArpRequestAt.put(targetIp, now);
        byte[] frame = new byte[ETH_HEADER + 28];
        fillEthHeader(frame, broadcastMac(), ETH_TYPE_ARP);
        fillArpCommon(frame, 1);
        System.arraycopy(ourMac, 0, frame, ETH_HEADER + 8, 6);
        writeInt(frame, ETH_HEADER + 14, ourIp);
        // tha zeros, tpa = target
        writeInt(frame, ETH_HEADER + 24, targetIp);
        writeRaw(frame);
    }

    private void sendArpReply(byte[] requesterMac, int requesterIp) {
        byte[] frame = new byte[ETH_HEADER + 28];
        fillEthHeader(frame, requesterMac, ETH_TYPE_ARP);
        fillArpCommon(frame, 2);
        System.arraycopy(ourMac, 0, frame, ETH_HEADER + 8, 6);
        writeInt(frame, ETH_HEADER + 14, ourIp);
        System.arraycopy(requesterMac, 0, frame, ETH_HEADER + 18, 6);
        writeInt(frame, ETH_HEADER + 24, requesterIp);
        writeRaw(frame);
    }

    private void sendGratuitousArp() {
        byte[] frame = new byte[ETH_HEADER + 28];
        fillEthHeader(frame, broadcastMac(), ETH_TYPE_ARP);
        fillArpCommon(frame, 2);
        System.arraycopy(ourMac, 0, frame, ETH_HEADER + 8, 6);
        writeInt(frame, ETH_HEADER + 14, ourIp);
        writeInt(frame, ETH_HEADER + 24, ourIp);
        writeRaw(frame);
    }

    private void fillArpCommon(byte[] frame, int oper) {
        int base = ETH_HEADER;
        writeShort(frame, base, (short) 0x0001);     // htype ethernet
        writeShort(frame, base + 2, ETH_TYPE_IPV4);  // ptype IPv4
        frame[base + 4] = 6;                         // hlen
        frame[base + 5] = 4;                         // plen
        writeShort(frame, base + 6, (short) oper);
    }

    // ---- frame writers -----------------------------------------------------

    private void writeFrame(byte[] dstMac, short etherType, byte[] payload, int offset, int length) {
        byte[] frame = new byte[ETH_HEADER + length];
        System.arraycopy(dstMac, 0, frame, 0, 6);
        System.arraycopy(ourMac, 0, frame, 6, 6);
        writeShort(frame, 12, etherType);
        System.arraycopy(payload, offset, frame, ETH_HEADER, length);
        writeRaw(frame);
    }

    private void writeRaw(byte[] frame) {
        try {
            Os.write(appEnd, frame, 0, frame.length);
        } catch (Exception e) {
            if (running) {
                LogStore.append(context, "vpn", "tap frame write failed: " + e.getMessage());
            }
        }
    }

    private void writeTun(byte[] buffer, int offset, int length) {
        try {
            Os.write(tunFd, buffer, offset, length);
        } catch (Exception e) {
            if (running) {
                LogStore.append(context, "vpn", "tap tun write failed: " + e.getMessage());
            }
        }
    }

    // ---- helpers -----------------------------------------------------------

    private void fillEthHeader(byte[] frame, byte[] dstMac, short etherType) {
        System.arraycopy(dstMac, 0, frame, 0, 6);
        System.arraycopy(ourMac, 0, frame, 6, 6);
        writeShort(frame, 12, etherType);
    }

    private boolean isOnSubnet(int ip) {
        return (ip & netmask) == (ourIp & netmask);
    }

    private int subnetBroadcast() {
        return (ourIp & netmask) | ~netmask;
    }

    private static byte[] broadcastMac() {
        return new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    }

    private static byte[] multicastMac(int ip) {
        byte[] mac = new byte[6];
        mac[0] = 0x01;
        mac[1] = 0x00;
        mac[2] = 0x5E;
        mac[3] = (byte) ((ip >>> 16) & 0x7F);
        mac[4] = (byte) ((ip >>> 8) & 0xFF);
        mac[5] = (byte) (ip & 0xFF);
        return mac;
    }

    private static boolean macEquals(byte[] frame, int offset, byte[] mac) {
        for (int i = 0; i < 6; i++) {
            if (frame[offset + i] != mac[i]) {
                return false;
            }
        }
        return true;
    }

    private static short readShort(byte[] b, int off) {
        return (short) (((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF));
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static void writeShort(byte[] b, int off, short v) {
        b[off] = (byte) ((v >> 8) & 0xFF);
        b[off + 1] = (byte) (v & 0xFF);
    }

    private static void writeInt(byte[] b, int off, int v) {
        b[off] = (byte) ((v >>> 24) & 0xFF);
        b[off + 1] = (byte) ((v >>> 16) & 0xFF);
        b[off + 2] = (byte) ((v >>> 8) & 0xFF);
        b[off + 3] = (byte) (v & 0xFF);
    }

    private static long elapsed() {
        return android.os.SystemClock.elapsedRealtime();
    }

    private static void join(Thread t) {
        try {
            t.join(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(FileDescriptor fd) {
        try {
            Os.close(fd);
        } catch (Exception ignored) {
        }
    }

    static int parseIp(String dotted) {
        if (dotted == null) {
            return 0;
        }
        String[] parts = dotted.trim().split("\\.");
        if (parts.length != 4) {
            return 0;
        }
        int result = 0;
        try {
            for (int i = 0; i < 4; i++) {
                int octet = Integer.parseInt(parts[i]);
                if (octet < 0 || octet > 255) {
                    return 0;
                }
                result = (result << 8) | octet;
            }
        } catch (NumberFormatException e) {
            return 0;
        }
        return result;
    }

    static String ipToString(int ip) {
        return String.format(Locale.US, "%d.%d.%d.%d",
                (ip >>> 24) & 0xFF, (ip >>> 16) & 0xFF, (ip >>> 8) & 0xFF, ip & 0xFF);
    }

    /** Parses a locally-administered MAC from "aa:bb:cc:dd:ee:ff"; null on error. */
    static byte[] parseMac(String mac) {
        if (mac == null) {
            return null;
        }
        String[] parts = mac.trim().split(":");
        if (parts.length != 6) {
            return null;
        }
        byte[] out = new byte[6];
        try {
            for (int i = 0; i < 6; i++) {
                out[i] = (byte) Integer.parseInt(parts[i], 16);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    private static final class ArpEntry {
        final byte[] mac;
        final long learnedAt;

        ArpEntry(byte[] mac, long learnedAt) {
            this.mac = mac;
            this.learnedAt = learnedAt;
        }
    }

    private static final class PendingPacket {
        final byte[] data;
        final long parkedAt;

        PendingPacket(byte[] data, long parkedAt) {
            this.data = data;
            this.parkedAt = parkedAt;
        }
    }
}
