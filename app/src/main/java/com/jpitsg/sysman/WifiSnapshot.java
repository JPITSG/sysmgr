package com.jpitsg.sysman;

final class WifiSnapshot {
    final boolean connected;
    final boolean ssidRedacted;
    final boolean bssidRedacted;
    final String ssid;
    final String bssid;
    final String displaySsid;
    final String displayBssid;
    final String detail;

    WifiSnapshot(boolean connected, boolean ssidRedacted, boolean bssidRedacted, String ssid, String bssid, String detail) {
        this.connected = connected;
        this.ssidRedacted = ssidRedacted;
        this.bssidRedacted = bssidRedacted;
        this.ssid = ssid == null ? "" : ssid;
        this.bssid = bssid == null ? "" : bssid;
        this.displaySsid = this.ssid.isEmpty() ? (ssidRedacted ? "<redacted/unknown>" : "<none>") : this.ssid;
        this.displayBssid = this.bssid.isEmpty() ? (bssidRedacted ? "<redacted>" : "<none>") : this.bssid;
        this.detail = detail == null ? "" : detail;
    }

    static WifiSnapshot disconnected(String detail) {
        return new WifiSnapshot(false, false, false, "", "", detail);
    }
}
