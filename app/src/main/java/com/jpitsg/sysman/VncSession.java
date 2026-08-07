package com.jpitsg.sysman;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One client's RFB 3.8 conversation: handshake, authentication, then a reader
 * thread taking client messages while a frame thread answers update requests.
 *
 * <p>Two threads because RFB is request-driven in one direction and streamed in
 * the other. Only the frame thread ever writes, so there is no write lock to
 * get wrong. The frame thread also does the capturing, which means nothing is
 * captured at all until a client asks for a frame — the cheapest possible idle.
 */
final class VncSession implements Runnable {
    private static final Charset ASCII = Charset.forName("US-ASCII");
    private static final String PROTOCOL_VERSION = "RFB 003.008\n";
    private static final int SECURITY_VNC_AUTH = 2;
    private static final int SECURITY_RESULT_OK = 0;
    private static final int SECURITY_RESULT_FAILED = 1;
    private static final int HANDSHAKE_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_CUT_TEXT_BYTES = 1 << 20;
    private static final long FRAME_WAIT_MILLIS = 500L;

    interface Listener {
        void onCredentialsAccepted(VncSession session, String clientAddress);

        void onAuthenticated(VncSession session, String clientAddress);

        void onClosed(VncSession session, String clientAddress, String reason);

        void onAuthFailed(String clientAddress);
    }

    private final Context context;
    private final Socket socket;
    private final Listener listener;
    private final String clientAddress;
    private final RfbEncoder encoder = new RfbEncoder();
    private final Set<Integer> clientEncodings = new HashSet<>();

    private final Object updateLock = new Object();
    private long requestSeq;
    private long servedSeq;
    private long fullUpdateVersion;
    private boolean fullUpdateRequested;
    private Rect requestedRegion;

    private volatile boolean running = true;
    private volatile String closeReason = "";
    private volatile long lastProgressAt = SystemClock.elapsedRealtime();

    private DataInputStream in;
    private OutputStream out;
    private FrameSource source;
    private FrameSource.Frame initialFrame;
    private volatile VncInputInjector injector;
    private Thread frameThread;
    // Written by the frame thread on a resize, read by the reader thread when
    // it clamps an update request.
    private volatile int frameWidth;
    private volatile int frameHeight;
    private volatile boolean supportsZrle;
    private volatile boolean supportsDesktopSize;
    /**
     * Handed over rather than applied in place: the encoder belongs to the
     * frame thread, and swapping its format part way through a rectangle would
     * put two layouts in one update.
     */
    private volatile RfbEncoder.PixelFormat pendingFormat;

    VncSession(Context context, Socket socket, Listener listener) {
        this.context = context.getApplicationContext();
        this.socket = socket;
        this.listener = listener;
        this.clientAddress = describe(socket);
    }

    String clientAddress() {
        return clientAddress;
    }

    @Override
    public void run() {
        String reason = "closed";
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MILLIS);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 8192));
            out = new BufferedOutputStream(socket.getOutputStream(), 1 << 16);

            if (!handshake()) {
                reason = closeReason.isEmpty() ? "handshake failed" : closeReason;
                return;
            }
            // Past the handshake a client may legitimately go quiet for a long
            // time, so the read timeout comes off and liveness is left to TCP
            // keepalive and the idle timeout.
            socket.setSoTimeout(0);
            listener.onAuthenticated(this, clientAddress);

            startFrameThread();
            readerLoop();
            reason = closeReason.isEmpty() ? "client disconnected" : closeReason;
        } catch (EOFException e) {
            reason = closeReason.isEmpty() ? "client disconnected" : closeReason;
        } catch (IOException e) {
            reason = closeReason.isEmpty()
                    ? e.getClass().getSimpleName() + ": " + e.getMessage()
                    : closeReason;
        } catch (RuntimeException e) {
            reason = "session crashed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            close(reason);
            listener.onClosed(this, clientAddress, reason);
        }
    }

    /** Ends the session from another thread; the socket close unblocks both loops. */
    void stop(String reason) {
        if (closeReason.isEmpty()) {
            closeReason = reason;
        }
        running = false;
        closeQuietly();
        synchronized (updateLock) {
            updateLock.notifyAll();
        }
    }

    // ---- Handshake ----------------------------------------------------------

    private boolean handshake() throws IOException {
        out.write(PROTOCOL_VERSION.getBytes(ASCII));
        out.flush();

        byte[] clientVersion = new byte[12];
        in.readFully(clientVersion);
        String version = new String(clientVersion, ASCII).trim();
        LogStore.append(context, "vnc", "Client " + clientAddress + " speaks " + version);

        // Only VNC authentication is offered. Security type 1 is "none", which
        // has no place on a server that also injects input.
        out.write(1);
        out.write(SECURITY_VNC_AUTH);
        out.flush();

        int chosen = in.readUnsignedByte();
        if (chosen != SECURITY_VNC_AUTH) {
            failSecurity("unsupported security type " + chosen);
            closeReason = "client chose security type " + chosen;
            return false;
        }

        byte[] challenge = VncAuth.newChallenge();
        out.write(challenge);
        out.flush();

        byte[] response = new byte[VncAuth.CHALLENGE_LENGTH];
        in.readFully(response);
        if (!VncAuth.verify(VncSecretStore.password(context), challenge, response)) {
            failSecurity("Authentication failed");
            closeReason = "authentication failed";
            listener.onAuthFailed(clientAddress);
            return false;
        }
        // The password has been proven even if capture subsequently fails.
        // Wake before the first capture so the setting can help a sleeping
        // display, and clear any earlier failures for this host immediately.
        listener.onCredentialsAccepted(this, clientAddress);
        if (!running) {
            closeReason = "server stopped";
            return false;
        }
        // Brought up before the security result is sent, because that result is
        // the last message with room for an explanation. Once it says OK the
        // client is waiting for ServerInit, and a server that cannot produce
        // one can only hang up — which a client reports as the connection
        // being closed, with no hint as to why.
        if (!startFrameSource()) {
            failSecurity(closeReason);
            LogStore.append(context, "vnc", "Cannot serve " + clientAddress + ": " + closeReason);
            return false;
        }

        RfbEncoder.writeInt(out, SECURITY_RESULT_OK);
        out.flush();

        // ClientInit: the shared flag is read and ignored — this server serves
        // one client at a time either way.
        in.readUnsignedByte();

        writeServerInit();
        return true;
    }

    private boolean startFrameSource() {
        Config config = Config.get(context);
        FrameSource engine = Config.VNC_ENGINE_PROJECTION.equals(config.vncEngine())
                ? new ProjectionFrameSource(context)
                : new AccessibilityFrameSource(context);
        if (!engine.start(config.vncScalePercent(), config.vncMaxFps())) {
            closeReason = engine.blockedReason();
            return false;
        }
        source = engine;

        // ServerInit has to carry the framebuffer size, and the source only
        // knows it once it has seen a frame.
        FrameSource.Frame first = source.acquire(5_000L);
        if (first == null) {
            source.stop();
            source = null;
            closeReason = "could not capture the screen";
            return false;
        }
        frameWidth = first.width;
        frameHeight = first.height;
        // Do not throw the first capture away merely to learn its dimensions.
        // Projection only produces another buffer when the display is composed
        // again, so a static screen could otherwise leave the client's initial
        // framebuffer request unanswered indefinitely.
        initialFrame = first;
        source.consumeSizeChanged();
        // Client coordinates are in the scaled framebuffer; gestures have to be
        // dispatched in real display pixels.
        injector = new VncInputInjector(context, frameWidth, frameHeight,
                source.sourceWidth(), source.sourceHeight());
        return true;
    }

    private void writeServerInit() throws IOException {
        RfbEncoder.writeShort(out, frameWidth);
        RfbEncoder.writeShort(out, frameHeight);
        encoder.format().write(out);
        byte[] name = ("System Manager (" + android.os.Build.MODEL + ")").getBytes(ASCII);
        RfbEncoder.writeInt(out, name.length);
        out.write(name);
        out.flush();
        LogStore.append(context, "vnc", "Serving " + frameWidth + "x" + frameHeight
                + " to " + clientAddress);
    }

    private void failSecurity(String reason) {
        try {
            RfbEncoder.writeInt(out, SECURITY_RESULT_FAILED);
            byte[] text = reason.getBytes(ASCII);
            RfbEncoder.writeInt(out, text.length);
            out.write(text);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    // ---- Client messages ----------------------------------------------------

    private void readerLoop() throws IOException {
        while (running) {
            int type = in.read();
            if (type < 0) {
                return;
            }
            switch (type) {
                case 0:
                    readSetPixelFormat();
                    break;
                case 2:
                    readSetEncodings();
                    break;
                case 3:
                    readFramebufferUpdateRequest();
                    break;
                case 4:
                    readKeyEvent();
                    break;
                case 5:
                    readPointerEvent();
                    break;
                case 6:
                    readClientCutText();
                    break;
                default:
                    // The stream is framed by message type; an unknown one means
                    // we have lost sync and cannot safely skip ahead.
                    closeReason = "unknown client message type " + type;
                    return;
            }
        }
    }

    private void readSetPixelFormat() throws IOException {
        skipFully(3);
        int bitsPerPixel = in.readUnsignedByte();
        int depth = in.readUnsignedByte();
        boolean bigEndian = in.readUnsignedByte() != 0;
        boolean trueColour = in.readUnsignedByte() != 0;
        int redMax = in.readUnsignedShort();
        int greenMax = in.readUnsignedShort();
        int blueMax = in.readUnsignedShort();
        int redShift = in.readUnsignedByte();
        int greenShift = in.readUnsignedByte();
        int blueShift = in.readUnsignedByte();
        skipFully(3);

        RfbEncoder.PixelFormat format = new RfbEncoder.PixelFormat(bitsPerPixel, depth, bigEndian,
                trueColour, redMax, greenMax, blueMax, redShift, greenShift, blueShift);
        if (!format.supported()) {
            LogStore.append(context, "vnc", "Ignoring unsupported pixel format from "
                    + clientAddress + ": " + format);
            return;
        }
        synchronized (updateLock) {
            pendingFormat = format;
        }
        LogStore.append(context, "vnc", "Pixel format from " + clientAddress + ": " + format);
        // The client expects the next update in the new format, so anything
        // already on its screen is stale.
        requestFullUpdate();
    }

    private void readSetEncodings() throws IOException {
        skipFully(1);
        int count = in.readUnsignedShort();
        clientEncodings.clear();
        for (int i = 0; i < count; i++) {
            clientEncodings.add(in.readInt());
        }
        supportsZrle = clientEncodings.contains(RfbEncoder.ENCODING_ZRLE);
        supportsDesktopSize = clientEncodings.contains(RfbEncoder.ENCODING_DESKTOP_SIZE);
        LogStore.append(context, "vnc", "Encodings from " + clientAddress
                + ": using " + (supportsZrle ? "ZRLE" : "Raw")
                + (supportsDesktopSize ? ", DesktopSize supported" : ", no DesktopSize"));
    }

    private void readFramebufferUpdateRequest() throws IOException {
        boolean incremental = in.readUnsignedByte() != 0;
        int x = in.readUnsignedShort();
        int y = in.readUnsignedShort();
        int width = in.readUnsignedShort();
        int height = in.readUnsignedShort();

        Rect requested = new Rect(x, y, x + width, y + height);
        if (!requested.intersect(0, 0, frameWidth, frameHeight)) {
            return;
        }
        synchronized (updateLock) {
            requestSeq++;
            if (!incremental) {
                fullUpdateRequested = true;
            }
            requestedRegion = requestedRegion == null ? requested : union(requestedRegion, requested);
            updateLock.notifyAll();
        }
    }

    private void readKeyEvent() throws IOException {
        boolean down = in.readUnsignedByte() != 0;
        skipFully(2);
        int keysym = in.readInt();
        markProgress();
        if (injector != null) {
            injector.onKeyEvent(down, keysym);
        }
    }

    private void readPointerEvent() throws IOException {
        int buttonMask = in.readUnsignedByte();
        int x = in.readUnsignedShort();
        int y = in.readUnsignedShort();
        markProgress();
        if (injector != null) {
            injector.onPointerEvent(buttonMask, x, y);
        }
    }

    private void readClientCutText() throws IOException {
        skipFully(3);
        int length = in.readInt();
        if (length < 0 || length > MAX_CUT_TEXT_BYTES) {
            closeReason = "client cut text too large (" + length + " bytes)";
            running = false;
            return;
        }
        skipFully(length);
    }

    // ---- Frame delivery -----------------------------------------------------

    private void startFrameThread() {
        frameThread = new Thread(new Runnable() {
            @Override
            public void run() {
                frameLoop();
            }
        }, "SystemManagerVncFrames");
        frameThread.start();
    }

    private void frameLoop() {
        FrameSource.Frame previous = null;
        FrameDiffer differ = new FrameDiffer(frameWidth, frameHeight);
        long idleMillis = Config.get(context).vncIdleTimeoutMinutes() * 60_000L;

        try {
            while (running) {
                // Checked here as well as in the wait: a client watching a
                // static screen leaves a request outstanding forever, so the
                // waiting branch below is never reached.
                if (isIdle(idleMillis)) {
                    stop("idle timeout");
                    return;
                }
                long seq;
                boolean full;
                long fullVersion;
                Rect region;
                synchronized (updateLock) {
                    while (running && requestSeq == servedSeq) {
                        updateLock.wait(FRAME_WAIT_MILLIS);
                        if (isIdle(idleMillis)) {
                            stop("idle timeout");
                            return;
                        }
                    }
                    if (!running) {
                        return;
                    }
                    seq = requestSeq;
                    full = fullUpdateRequested;
                    fullVersion = fullUpdateVersion;
                    region = requestedRegion == null
                            ? new Rect(0, 0, frameWidth, frameHeight)
                            : new Rect(requestedRegion);
                }

                FrameSource.Frame frame = initialFrame;
                initialFrame = null;
                if (frame == null) {
                    frame = source.acquire(5_000L);
                }
                if (frame == null) {
                    if (isIdle(idleMillis)) {
                        stop("idle timeout");
                        return;
                    }
                    continue;
                }
                if (source.consumeSizeChanged()
                        || frame.width != frameWidth || frame.height != frameHeight) {
                    if (!supportsDesktopSize) {
                        // Without DesktopSize there is no way to tell the client,
                        // and serving the old geometry would be a lie.
                        stop("display size changed to " + frame.width + "x" + frame.height
                                + " and the client cannot be told");
                        return;
                    }
                    resizeTo(frame.width, frame.height);
                    differ = new FrameDiffer(frameWidth, frameHeight);
                    previous = null;
                    sendDesktopSize();
                    // The client re-requests after any update, and that request
                    // is the one that gets the repainted screen.
                    synchronized (updateLock) {
                        servedSeq = seq;
                        fullUpdateRequested = true;
                        fullUpdateVersion++;
                        requestedRegion = null;
                    }
                    continue;
                }

                List<Rect> dirty = (full || previous == null)
                        ? differ.fullFrame()
                        : differ.diff(frame.pixels, previous.pixels);
                previous = frame;
                boolean hadDamage = !dirty.isEmpty();
                List<Rect> clipped = clip(dirty, region);
                boolean damageOutsideRegion = FrameDiffer.area(clipped) < FrameDiffer.area(dirty);
                dirty = clipped;
                if (dirty.isEmpty()) {
                    if (hadDamage) {
                        // Something changed outside the requested region. The
                        // frame it changed in is already gone — the source only
                        // keeps two — so the next update has to be a full one or
                        // those pixels are lost for good.
                        synchronized (updateLock) {
                            fullUpdateRequested = true;
                        }
                    }
                    // The request stays outstanding: RFB lets the server hold an
                    // incremental request until something actually changes.
                    continue;
                }

                sendUpdate(frame, dirty);
                markProgress();
                synchronized (updateLock) {
                    servedSeq = seq;
                    if (requestSeq == seq) {
                        if (fullUpdateVersion == fullVersion) {
                            fullUpdateRequested = false;
                        }
                        requestedRegion = null;
                    }
                    if (damageOutsideRegion) {
                        // The current request has been answered, but pixels the
                        // client did not request must not disappear from the
                        // differ's history before a later region asks for them.
                        fullUpdateRequested = true;
                        fullUpdateVersion++;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            stop("write failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (RuntimeException e) {
            stop("frame loop crashed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void sendUpdate(FrameSource.Frame frame, List<Rect> rects) throws IOException {
        // Applied here, between rectangles, where a format change is safe.
        RfbEncoder.PixelFormat requested;
        synchronized (updateLock) {
            requested = pendingFormat;
            pendingFormat = null;
        }
        if (requested != null) {
            encoder.setFormat(requested);
        }

        boolean zrle = supportsZrle;
        out.write(0);
        out.write(0);
        RfbEncoder.writeShort(out, rects.size());
        for (Rect rect : rects) {
            encoder.writeRectHeader(out, rect,
                    zrle ? RfbEncoder.ENCODING_ZRLE : RfbEncoder.ENCODING_RAW);
            if (zrle) {
                encoder.writeZrle(out, frame.pixels, frame.width, rect);
            } else {
                encoder.writeRaw(out, frame.pixels, frame.width, rect);
            }
        }
        out.flush();
    }

    /** One rectangle carrying the new size and no pixel data. */
    private void sendDesktopSize() throws IOException {
        out.write(0);
        out.write(0);
        RfbEncoder.writeShort(out, 1);
        encoder.writeRectHeader(out, new Rect(0, 0, frameWidth, frameHeight),
                RfbEncoder.ENCODING_DESKTOP_SIZE);
        out.flush();
        LogStore.append(context, "vnc", "Resized to " + frameWidth + "x" + frameHeight
                + " for " + clientAddress);
    }

    /** Re-points the framebuffer and the input mapping at the new geometry. */
    private void resizeTo(int width, int height) {
        frameWidth = width;
        frameHeight = height;
        VncInputInjector previousInjector = injector;
        injector = new VncInputInjector(context, width, height,
                source.sourceWidth(), source.sourceHeight());
        if (previousInjector != null) {
            previousInjector.stop();
        }
    }

    private void requestFullUpdate() {
        synchronized (updateLock) {
            fullUpdateRequested = true;
            fullUpdateVersion++;
            updateLock.notifyAll();
        }
    }

    private boolean isIdle(long idleMillis) {
        return idleMillis > 0L && SystemClock.elapsedRealtime() - lastProgressAt > idleMillis;
    }

    private void markProgress() {
        lastProgressAt = SystemClock.elapsedRealtime();
    }

    // ---- Teardown -----------------------------------------------------------

    private void close(String reason) {
        running = false;
        if (closeReason.isEmpty()) {
            closeReason = reason;
        }
        synchronized (updateLock) {
            updateLock.notifyAll();
        }
        // Unblock a frame writer before waiting for it. Waiting first leaves a
        // non-reading client in control of teardown for the full join timeout.
        closeQuietly();
        if (frameThread != null && frameThread != Thread.currentThread()) {
            // Capture can itself be waiting for a frame for five seconds. Its
            // two implementations both honour interruption, so wake it before
            // the shorter join instead of stopping its buffers underneath it.
            frameThread.interrupt();
            try {
                frameThread.join(2_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (injector != null) {
            // Before the source goes: a disconnect mid-drag must not leave a
            // finger held down on the phone.
            injector.stop();
            injector = null;
        }
        if (source != null) {
            source.stop();
            source = null;
        }
        initialFrame = null;
        encoder.close();
    }

    private void closeQuietly() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    // ---- Helpers ------------------------------------------------------------

    private final byte[] skipScratch = new byte[4096];

    private void skipFully(int count) throws IOException {
        int remaining = count;
        while (remaining > 0) {
            int read = in.read(skipScratch, 0, Math.min(remaining, skipScratch.length));
            if (read < 0) {
                throw new EOFException("stream ended while skipping");
            }
            remaining -= read;
        }
    }

    private static List<Rect> clip(List<Rect> rects, Rect region) {
        List<Rect> clipped = new ArrayList<>(rects.size());
        for (Rect rect : rects) {
            Rect copy = new Rect(rect);
            if (copy.intersect(region)) {
                clipped.add(copy);
            }
        }
        return clipped;
    }

    private static Rect union(Rect left, Rect right) {
        Rect result = new Rect(left);
        result.union(right);
        return result;
    }

    private static String describe(Socket socket) {
        if (socket.getInetAddress() == null) {
            return "unknown";
        }
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }
}
