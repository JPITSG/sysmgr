package com.jpitsg.sysman;

import android.content.Context;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class GpsPostTask implements SystemTask {
    @Override
    public String id() {
        return TaskIds.GPS_POST;
    }

    @Override
    public TaskResult run(Context context, String reason) {
        Config config = Config.get(context);
        WifiSnapshot wifi = WifiInfoReader.read(context);
        if (isForcedWifiLoss(reason)) {
            wifi = WifiSnapshot.disconnected("forced disconnected state for " + reason);
        }
        boolean matches = PatternMatcher.simpleMatch(config.ssidPattern(), wifi.ssid, config.caseSensitiveSsid());
        LogStore.append(context, "gps", "Wi-Fi ssid=" + wifi.displaySsid + " bssid=" + wifi.displayBssid + " matches=" + matches + " detail=" + wifi.detail);

        LocationData location = null;
        if (matches && config.useFallbackOnSsidMatch()) {
            location = new LocationData(
                    config.fallbackLatitude(),
                    config.fallbackLongitude(),
                    -1f,
                    System.currentTimeMillis(),
                    "fallback",
                    "fallback-ssid-match");
            LogStore.append(context, "gps", "Using fallback location because SSID matches pattern: " + location.summary());
        }

        boolean shouldRequestFresh = location == null && !matches && config.requestGpsOnSsidMismatch();
        if (shouldRequestFresh) {
            location = LocationHelper.acquireBest(context, config);
        } else {
            LogStore.append(context, "gps", "Skipping fresh GPS because fallback/location policy already handled this state");
        }

        if (location == null) {
            return TaskResult.failure("No location available");
        }

        int battery = BatteryReader.batteryPercent(context);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("lat", formatDouble(location.latitude));
        params.put("lon", formatDouble(location.longitude));
        params.put("batt", battery >= 0 ? Integer.toString(battery) : "");
        params.put("bssid", wifi.bssid);

        if (config.includeExtendedFields()) {
            params.put("ssid", wifi.ssid);
            params.put("accuracy", location.accuracyMeters >= 0 ? Float.toString(location.accuracyMeters) : "");
            params.put("provider", location.provider);
            params.put("source", location.source);
            params.put("reason", reason == null ? "" : reason);
            params.put("task", id());
            params.put("time", Long.toString(System.currentTimeMillis()));
        }

        if (config.gpsUseRemoteLink()) {
            try {
                JSONObject payload = gpsPayload(params);
                if (RemoteLinkService.sendGpsIfRunning(context, payload)) {
                    LogStore.append(context, "gps", "Sent GPS payload over Remote Link " + params);
                    return TaskResult.success("Remote Link GPS sent");
                }
                LogStore.append(context, "gps", "Remote Link unavailable; falling back to HTTP GET");
            } catch (Exception e) {
                LogStore.append(context, "gps", "Remote Link GPS payload failed; falling back to HTTP GET: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        try {
            LogStore.append(context, "gps", "GET " + params + " to " + config.serverBaseUrl() + config.trackPath());
            HttpResult result = HttpPoster.getForm(config.serverBaseUrl(), config.trackPath(), params, config.httpTimeoutSeconds());
            String body = result.body.isEmpty() ? "" : " body=" + result.body.replace('\n', ' ');
            if (result.success()) {
                return TaskResult.success("HTTP " + result.code + body);
            }
            return TaskResult.failure("HTTP " + result.code + body);
        } catch (Exception e) {
            return TaskResult.failure("Post failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static JSONObject gpsPayload(Map<String, String> params) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("type", "gps");
        payload.put("ts", System.currentTimeMillis() / 1000L);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            payload.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return payload;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.7f", value);
    }

    private static boolean isForcedWifiLoss(String reason) {
        return reason != null && reason.contains("wifi-lost");
    }
}
