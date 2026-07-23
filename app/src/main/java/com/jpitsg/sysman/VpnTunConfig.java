package com.jpitsg.sysman;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates the tun/tap parameters openvpn pushes over the management
 * interface (IFCONFIG/ROUTE/DNS/...) between session start and the OPENTUN
 * request that asks us to establish the interface.
 */
final class VpnTunConfig {
    String ip4;            // local address, dotted
    String netmask4;       // dotted netmask (subnet topology) or peer (net30/p2p)
    String topology = "";  // "subnet" | "net30" | "p2p" | ""
    int mtu = 1500;

    String ip6;            // "addr/prefix"

    final List<Route4> routes4 = new ArrayList<>();
    final List<Route6> routes6 = new ArrayList<>();
    final List<String> dns4 = new ArrayList<>();
    final List<String> dns6 = new ArrayList<>();
    final List<String> domains = new ArrayList<>();

    boolean hasIfconfig() {
        return ip4 != null && !ip4.isEmpty();
    }

    /** First on-subnet route gateway, used as the tap default gateway to ARP. */
    String firstGateway() {
        for (Route4 r : routes4) {
            if (r.gateway != null && !r.gateway.isEmpty()) {
                return r.gateway;
            }
        }
        return "";
    }

    /** A stable string identity of the applied config, for persist-tun comparison. */
    String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(ip4).append('/').append(netmask4).append('/').append(topology).append('/').append(mtu);
        sb.append('|').append(ip6);
        for (Route4 r : routes4) {
            sb.append("|r4:").append(r.network).append(' ').append(r.netmask).append(' ').append(r.gateway);
        }
        for (Route6 r : routes6) {
            sb.append("|r6:").append(r.destination);
        }
        for (String d : dns4) {
            sb.append("|d4:").append(d);
        }
        for (String d : dns6) {
            sb.append("|d6:").append(d);
        }
        for (String d : domains) {
            sb.append("|dom:").append(d);
        }
        return sb.toString();
    }

    static final class Route4 {
        final String network;
        final String netmask;
        final String gateway;

        Route4(String network, String netmask, String gateway) {
            this.network = network;
            this.netmask = netmask;
            this.gateway = gateway;
        }
    }

    static final class Route6 {
        final String destination; // "dest/prefix"

        Route6(String destination) {
            this.destination = destination;
        }
    }
}
