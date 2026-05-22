package com.jpitsg.sysman;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class HttpPoster {
    private HttpPoster() {
    }

    static HttpResult getForm(String baseUrl, String path, Map<String, String> params, int timeoutSeconds) throws Exception {
        URL url = new URL(joinWithQuery(baseUrl, path, params));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(timeoutSeconds * 1000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "SystemManager/1.0");
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String response = readShort(stream);
            return new HttpResult(code, response);
        } finally {
            connection.disconnect();
        }
    }

    private static String joinWithQuery(String baseUrl, String path, Map<String, String> params) throws Exception {
        String joined = join(baseUrl, path);
        String query = encode(params);
        if (query.isEmpty()) {
            return joined;
        }
        return joined + (joined.contains("?") ? "&" : "?") + query;
    }

    private static String join(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        String suffix = path == null ? "" : path.trim();
        if (base.endsWith("/") && suffix.startsWith("/")) {
            return base.substring(0, base.length() - 1) + suffix;
        }
        if (!base.endsWith("/") && !suffix.startsWith("/")) {
            return base + "/" + suffix;
        }
        return base + suffix;
    }

    private static String encode(Map<String, String> params) throws Exception {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), "UTF-8"));
        }
        return builder.toString();
    }

    private static String readShort(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[256];
            int read;
            while ((read = reader.read(buffer)) != -1 && builder.length() < 1024) {
                builder.append(buffer, 0, read);
            }
        }
        if (builder.length() > 500) {
            return builder.substring(0, 500);
        }
        return builder.toString();
    }
}
