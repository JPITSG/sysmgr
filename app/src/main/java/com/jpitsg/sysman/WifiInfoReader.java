package com.jpitsg.sysman;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class WifiInfoReader {
    private WifiInfoReader() {
    }

    static WifiSnapshot read(Context context) {
        Context app = context.getApplicationContext();
        WifiSnapshot connectivity = fromInfo(readFromConnectivityManager(app), "connectivity");
        WifiSnapshot wifiManager = fromInfo(readFromWifiManager(app), "wifi-manager");
        WifiSnapshot best = best(connectivity, wifiManager);

        if (needsBetterWifiIdentity(best) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            best = best(best, readFromLocationAwareCallback(app));
        }

        if (best == null) {
            return new WifiSnapshot(false, false, false, "", "", "wifi info unavailable");
        }
        return best;
    }

    private static WifiSnapshot fromInfo(WifiInfo info, String source) {
        if (info == null) {
            return null;
        }
        String rawSsid = info.getSSID();
        String rawBssid = info.getBSSID();
        boolean ssidRedacted = isUnknownSsid(rawSsid);
        boolean bssidRedacted = isRedactedBssid(rawBssid);
        String ssid = ssidRedacted ? "" : cleanQuoted(rawSsid);
        String bssid = bssidRedacted ? "" : clean(rawBssid);
        boolean connected = !ssid.isEmpty() || !bssid.isEmpty();
        String detail = source
                + " rssi=" + info.getRssi()
                + " rawSsid=" + safeRaw(rawSsid)
                + " rawBssid=" + safeRaw(rawBssid)
                + " ssidRedacted=" + ssidRedacted
                + " bssidRedacted=" + bssidRedacted;
        return new WifiSnapshot(connected, ssidRedacted, bssidRedacted, ssid, bssid, detail);
    }

    private static WifiSnapshot readFromLocationAwareCallback(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<WifiSnapshot> snapshot = new AtomicReference<>();
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return;
                }
                TransportInfo transportInfo = capabilities.getTransportInfo();
                if (transportInfo instanceof WifiInfo) {
                    snapshot.set(fromInfo((WifiInfo) transportInfo, "callback-location-info"));
                    latch.countDown();
                }
            }
        };

        try {
            manager.registerNetworkCallback(request, callback);
            latch.await(1200L, TimeUnit.MILLISECONDS);
        } catch (SecurityException e) {
            return new WifiSnapshot(false, true, true, "", "", "callback-location-info security-error=" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
        } finally {
            try {
                manager.unregisterNetworkCallback(callback);
            } catch (RuntimeException ignored) {
            }
        }
        return snapshot.get();
    }

    private static WifiInfo readFromConnectivityManager(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) {
                return null;
            }
            Network network = manager.getActiveNetwork();
            if (network == null) {
                return null;
            }
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return null;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TransportInfo transportInfo = capabilities.getTransportInfo();
                if (transportInfo instanceof WifiInfo) {
                    return (WifiInfo) transportInfo;
                }
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    private static WifiInfo readFromWifiManager(Context context) {
        try {
            WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return manager == null ? null : manager.getConnectionInfo();
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private static boolean isUnknownSsid(String value) {
        if (value == null) {
            return true;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() || "<unknown ssid>".equalsIgnoreCase(cleaned);
    }

    private static boolean isRedactedBssid(String value) {
        if (value == null) {
            return false;
        }
        return "02:00:00:00:00:00".equals(value.trim());
    }

    private static String cleanQuoted(String value) {
        String cleaned = clean(value);
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeRaw(String value) {
        if (value == null) {
            return "<null>";
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? "<empty>" : cleaned;
    }

    private static boolean needsBetterWifiIdentity(WifiSnapshot snapshot) {
        return snapshot == null || snapshot.ssid.isEmpty() || snapshot.bssid.isEmpty();
    }

    private static WifiSnapshot best(WifiSnapshot left, WifiSnapshot right) {
        if (score(right) > score(left)) {
            return right;
        }
        return left;
    }

    private static int score(WifiSnapshot snapshot) {
        if (snapshot == null) {
            return -1;
        }
        int score = 0;
        if (snapshot.connected) {
            score += 1;
        }
        if (!snapshot.ssid.isEmpty()) {
            score += 8;
        }
        if (!snapshot.bssid.isEmpty()) {
            score += 4;
        }
        if (!snapshot.ssidRedacted) {
            score += 2;
        }
        if (!snapshot.bssidRedacted) {
            score += 1;
        }
        return score;
    }
}
