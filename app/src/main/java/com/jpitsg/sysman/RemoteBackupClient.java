package com.jpitsg.sysman;

import android.content.Context;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Backup transfer plus shared authenticated HTTP setup for Remote Link files. */
final class RemoteBackupClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int TRANSFER_TIMEOUT_MILLIS = 5 * 60_000;
    private static final int BUFFER_SIZE = 64 * 1024;

    private RemoteBackupClient() {
    }

    static void upload(Context context, File archive) throws Exception {
        if (archive == null || !archive.isFile() || archive.length() < 1L) {
            throw new IOException("Backup archive is empty");
        }
        Context app = context.getApplicationContext();
        HttpURLConnection connection = open(app, "PUT");
        try {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/gzip");
            connection.setRequestProperty("X-Backup-Sha256", sha256(archive));
            connection.setFixedLengthStreamingMode(archive.length());
            try (InputStream input = new FileInputStream(archive);
                 OutputStream output = connection.getOutputStream()) {
                copy(input, output);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                if (status == HttpURLConnection.HTTP_UNAVAILABLE) {
                    SystemBackupStateStore.setServerState(app, false, false, 0L);
                }
                throw responseError(connection, status, "Backup failed");
            }
            SystemBackupStateStore.setServerState(app, true, true,
                    System.currentTimeMillis());
            consume(connection);
        } finally {
            connection.disconnect();
        }
    }

    static File download(Context context) throws Exception {
        Context app = context.getApplicationContext();
        HttpURLConnection connection = open(app, "GET");
        File target = new File(app.getCacheDir(),
                "system-manager-restore-" + UUID.randomUUID().toString() + ".tar.gz");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                    SystemBackupStateStore.setServerState(app, true, false, 0L);
                } else if (status == HttpURLConnection.HTTP_UNAVAILABLE) {
                    SystemBackupStateStore.setServerState(app, false, false, 0L);
                }
                throw responseError(connection, status,
                        status == HttpURLConnection.HTTP_NOT_FOUND
                                ? "No backup is available" : "Restore failed");
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(target, false)) {
                copy(input, output);
                output.flush();
                output.getFD().sync();
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                throw e;
            }
            if (target.length() < 1L) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                throw new IOException("Downloaded backup archive is empty");
            }
            long modifiedAt = connection.getLastModified();
            SystemBackupStateStore.setServerState(app, true, true,
                    modifiedAt > 0L ? modifiedAt : System.currentTimeMillis());
            return target;
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(Context context, String method) throws Exception {
        return open(context, method, "/backup", "application/gzip, application/json");
    }

    static HttpURLConnection open(Context context, String method, String endpointPath,
                                  String accept) throws Exception {
        Config config = Config.get(context);
        URL url = endpointUrl(config.remoteLinkEndpoint(), endpointPath);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(TRANSFER_TIMEOUT_MILLIS);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("Cache-Control", "no-store");
        String credentials = config.remoteLinkUsername() + ":" + config.remoteLinkPassword();
        connection.setRequestProperty("Authorization", "Basic "
                + Base64.encodeToString(credentials.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        if (connection instanceof HttpsURLConnection && config.remoteLinkAcceptAnySslCert()) {
            HttpsURLConnection https = (HttpsURLConnection) connection;
            https.setSSLSocketFactory(trustAnySslSocketFactory());
            https.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        }
        return connection;
    }

    private static URL endpointUrl(String endpoint, String endpointPath) throws Exception {
        String value = endpoint == null ? "" : endpoint.trim();
        if (value.isEmpty()) {
            throw new IOException("Remote Link endpoint is missing");
        }
        URI original = URI.create(value);
        String scheme = original.getScheme() == null ? "" : original.getScheme().toLowerCase(Locale.US);
        if ("wss".equals(scheme)) {
            scheme = "https";
        } else if ("ws".equals(scheme)) {
            scheme = "http";
        }
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IOException("Remote Link endpoint must use HTTP or WebSocket transport");
        }
        if (original.getHost() == null || original.getHost().trim().isEmpty()) {
            throw new IOException("Remote Link endpoint is missing a host");
        }
        return new URI(scheme, null, original.getHost(), original.getPort(),
                endpointPath, null, null).toURL();
    }

    private static SSLSocketFactory trustAnySslSocketFactory() throws Exception {
        TrustManager[] managers = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, managers, new SecureRandom());
        return ssl.getSocketFactory();
    }

    static IOException responseError(HttpURLConnection connection, int status,
                                     String fallback) {
        String detail = readResponseBody(connection.getErrorStream());
        if (detail.isEmpty()) {
            detail = fallback + " (HTTP " + status + ")";
        }
        return new IOException(detail);
    }

    private static void consume(HttpURLConnection connection) {
        try (InputStream input = connection.getInputStream()) {
            byte[] buffer = new byte[4096];
            while (input.read(buffer) != -1) {
                // Drain the small response so the TLS connection closes cleanly.
            }
        } catch (Exception ignored) {
        }
    }

    private static String readResponseBody(InputStream input) {
        if (input == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while (text.length() < 4096 && (line = reader.readLine()) != null) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(line.trim());
            }
        } catch (Exception ignored) {
        }
        return text.toString().trim();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }
}
