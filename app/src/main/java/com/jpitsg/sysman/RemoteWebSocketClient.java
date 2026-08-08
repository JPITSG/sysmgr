package com.jpitsg.sysman;

import android.os.SystemClock;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

final class RemoteWebSocketClient implements Closeable {
    // A bare new Socket(host, port) blocks until the OS gives up (minutes on a
    // flapping network) and cannot be interrupted by the reconnect wake, so
    // every connect and the TLS handshake are bounded explicitly.
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final String ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int OPCODE_TEXT = 0x1;
    private static final int OPCODE_CLOSE = 0x8;
    private static final int OPCODE_PING = 0x9;
    private static final int OPCODE_PONG = 0xA;

    private final String endpoint;
    private final String username;
    private final String password;
    private final boolean acceptAnySslCert;
    private final SecureRandom random = new SecureRandom();

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private volatile long lastInboundAtMillis;

    RemoteWebSocketClient(String endpoint, String username, String password, boolean acceptAnySslCert) {
        this.endpoint = endpoint;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.acceptAnySslCert = acceptAnySslCert;
    }

    void connect() throws Exception {
        URI uri = normalizeEndpoint(endpoint);
        String scheme = uri.getScheme() == null ? "wss" : uri.getScheme().toLowerCase(Locale.US);
        boolean tls = "wss".equals(scheme);
        int port = uri.getPort() > 0 ? uri.getPort() : (tls ? 443 : 80);
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new IOException("missing host in endpoint");
        }

        socket = tls ? createTlsSocket(host, port) : createPlainSocket(host, port);
        socket.setSoTimeout(10_000);
        in = socket.getInputStream();
        out = socket.getOutputStream();

        byte[] keyBytes = new byte[16];
        random.nextBytes(keyBytes);
        String key = Base64.encodeToString(keyBytes, Base64.NO_WRAP);
        writeHandshake(uri, host, port, tls, key);
        readHandshakeResponse(key);
        socket.setSoTimeout(1000);
    }

    boolean isOpen() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    synchronized void sendText(String text) throws IOException {
        sendFrame(OPCODE_TEXT, text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }

    long lastInboundAtMillis() {
        return lastInboundAtMillis;
    }

    String readTextFrame() throws IOException {
        int first;
        try {
            first = in.read();
        } catch (SocketTimeoutException e) {
            return null;
        }
        if (first < 0) {
            throw new EOFException("websocket closed");
        }
        lastInboundAtMillis = SystemClock.elapsedRealtime();

        int second = readByte();
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7f;
        if (length == 126) {
            length = ((long) readByte() << 8) | readByte();
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte();
            }
        }
        if (length > Integer.MAX_VALUE) {
            throw new IOException("frame too large");
        }

        byte[] mask = null;
        if (masked) {
            mask = readFully(4);
        }
        byte[] payload = readFully((int) length);
        if (mask != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }

        int opcode = first & 0x0f;
        if (opcode == OPCODE_TEXT) {
            return new String(payload, StandardCharsets.UTF_8);
        }
        if (opcode == OPCODE_PING) {
            sendFrame(OPCODE_PONG, payload);
            return null;
        }
        if (opcode == OPCODE_CLOSE) {
            throw new EOFException("server sent close frame");
        }
        return null;
    }

    @Override
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        } catch (RuntimeException ignored) {
        }
    }

    private void writeHandshake(URI uri, String host, int port, boolean tls, String key) throws IOException {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            path += "?" + uri.getRawQuery();
        }

        StringBuilder request = new StringBuilder();
        request.append("GET ").append(path).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(host);
        if ((tls && port != 443) || (!tls && port != 80)) {
            request.append(':').append(port);
        }
        request.append("\r\n");
        request.append("Upgrade: websocket\r\n");
        request.append("Connection: Upgrade\r\n");
        request.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
        request.append("Sec-WebSocket-Version: 13\r\n");
        request.append("User-Agent: SystemManager/1.0\r\n");
        if (!username.isEmpty() || !password.isEmpty()) {
            String value = username + ":" + password;
            request.append("Authorization: Basic ")
                    .append(Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP))
                    .append("\r\n");
        }
        request.append("\r\n");
        out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static Socket createPlainSocket(String host, int port) throws IOException {
        Socket plain = new Socket();
        try {
            plain.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        } catch (IOException e) {
            try {
                plain.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
        return plain;
    }

    private Socket createTlsSocket(String host, int port) throws Exception {
        SSLSocketFactory factory = acceptAnySslCert
                ? trustAnySslSocketFactory()
                : (SSLSocketFactory) SSLSocketFactory.getDefault();
        Socket plain = createPlainSocket(host, port);
        SSLSocket sslSocket;
        try {
            // Bound the handshake reads too; connect() relaxes this to the
            // regular frame-read timeout once the websocket upgrade is done.
            plain.setSoTimeout(CONNECT_TIMEOUT_MILLIS);
            sslSocket = (SSLSocket) factory.createSocket(plain, host, port, true);
        } catch (Exception e) {
            try {
                plain.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
        try {
            if (!acceptAnySslCert) {
                SSLParameters parameters = sslSocket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslSocket.setSSLParameters(parameters);
            }
            sslSocket.startHandshake();
        } catch (Exception e) {
            try {
                sslSocket.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
        return sslSocket;
    }

    private static SSLSocketFactory trustAnySslSocketFactory() throws Exception {
        TrustManager[] trustManagers = new TrustManager[]{
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
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers, new SecureRandom());
        return context.getSocketFactory();
    }

    private void readHandshakeResponse(String key) throws Exception {
        String statusLine = readHttpLine();
        if (statusLine == null || !statusLine.contains(" 101 ")) {
            throw new IOException("websocket upgrade failed: " + statusLine);
        }

        String accept = null;
        String line;
        while ((line = readHttpLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            if ("sec-websocket-accept".equalsIgnoreCase(name)) {
                accept = line.substring(colon + 1).trim();
            }
        }
        String expected = expectedAccept(key);
        if (!expected.equals(accept)) {
            throw new IOException("websocket accept mismatch");
        }
    }

    private String readHttpLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int current = in.read();
            if (current < 0) {
                if (buffer.size() == 0) {
                    return null;
                }
                break;
            }
            if (previous == '\r' && current == '\n') {
                byte[] data = buffer.toByteArray();
                int length = data.length > 0 && data[data.length - 1] == '\r' ? data.length - 1 : data.length;
                return new String(data, 0, length, StandardCharsets.US_ASCII);
            }
            buffer.write(current);
            previous = current;
        }
        return buffer.toString("US-ASCII");
    }

    private void sendFrame(int opcode, byte[] payload) throws IOException {
        if (out == null) {
            throw new IOException("not connected");
        }
        int length = payload == null ? 0 : payload.length;
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | opcode);
        if (length <= 125) {
            frame.write(0x80 | length);
        } else if (length <= 65535) {
            frame.write(0x80 | 126);
            frame.write((length >>> 8) & 0xff);
            frame.write(length & 0xff);
        } else {
            long longLength = length;
            frame.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) {
                frame.write((int) ((longLength >>> (8 * i)) & 0xff));
            }
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        frame.write(mask);
        for (int i = 0; i < length; i++) {
            frame.write(payload[i] ^ mask[i % 4]);
        }
        out.write(frame.toByteArray());
        out.flush();
    }

    private int readByte() throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new EOFException("unexpected websocket EOF");
        }
        return value;
    }

    private byte[] readFully(int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read < 0) {
                throw new EOFException("unexpected websocket EOF");
            }
            offset += read;
        }
        return data;
    }

    private static URI normalizeEndpoint(String value) {
        String endpoint = value == null || value.trim().isEmpty()
                ? "https://server:1234"
                : value.trim();
        URI parsed = URI.create(endpoint);
        String scheme = parsed.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return URI.create("wss:" + endpoint.substring(endpoint.indexOf(':') + 1));
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return URI.create("ws:" + endpoint.substring(endpoint.indexOf(':') + 1));
        }
        return parsed;
    }

    private static String expectedAccept(String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] data = digest.digest((key + ACCEPT_GUID).getBytes(StandardCharsets.US_ASCII));
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }
}
