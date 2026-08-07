package com.jpitsg.sysman;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Screen capture through {@link MediaProjection}: full frame rate, at the cost
 * of a consent tap on every start.
 *
 * <p>The virtual display is created at the scaled size, so the compositor does
 * the downscale and there is no per-frame copy at all — the opposite of the
 * Accessibility engine, which cannot avoid one.
 *
 * <p>The projection token itself belongs to {@link VncService}, not to this
 * source. Android 14 and newer permit one virtual display per projection, so a
 * rotation resizes that display and swaps its surface in place. Ending the
 * source also ends the projection on those releases; the next capture session
 * needs fresh consent instead of failing while trying to reuse the token.
 */
final class ProjectionFrameSource implements FrameSource {
    private static final String DISPLAY_NAME = "SystemManagerVnc";
    private static final long IMAGE_WAIT_SLICE_MILLIS = 200L;

    private final Context context;
    private final Object frameLock = new Object();

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private HandlerThread handlerThread;
    private Handler handler;

    private int scalePercent = Config.VNC_SCALE_FULL;
    private long minIntervalMillis;
    private long lastCaptureAt;

    private int sourceWidth;
    private int sourceHeight;
    private int scaledWidth;
    private int scaledHeight;
    private int[][] buffers;
    private int bufferIndex;
    private int[] rowPixels;

    private boolean imagePending;
    private volatile boolean sizeChanged;
    private volatile String blockedReason = "";

    ProjectionFrameSource(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean start(int scalePercent, int maxFps) {
        projection = VncService.activeProjection();
        if (projection == null) {
            blockedReason = "Screen capture has not been authorised";
            return false;
        }
        this.scalePercent = scalePercent < Config.VNC_SCALE_HALF || scalePercent > Config.VNC_SCALE_FULL
                ? Config.VNC_SCALE_FULL
                : scalePercent;
        this.minIntervalMillis = maxFps > 0 ? Math.max(1L, 1000L / maxFps) : 0L;
        this.lastCaptureAt = 0L;
        this.blockedReason = "";

        handlerThread = new HandlerThread("SystemManagerVncProjection");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        int[] size = displaySize();
        if (size == null) {
            blockedReason = "Could not read the display size";
            stop();
            return false;
        }
        if (!configure(size[0], size[1])) {
            stop();
            return false;
        }
        return true;
    }

    /** Builds the reader and mirror at a given display size, replacing any existing pair. */
    private boolean configure(int width, int height) {
        int nextScaledWidth = Math.max(1, width * scalePercent / 100);
        int nextScaledHeight = Math.max(1, height * scalePercent / 100);
        ImageReader nextReader = null;

        try {
            nextReader = ImageReader.newInstance(
                    nextScaledWidth, nextScaledHeight, PixelFormat.RGBA_8888, 2);
            nextReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    synchronized (frameLock) {
                        imagePending = true;
                        frameLock.notifyAll();
                    }
                }
            }, handler);
            if (virtualDisplay == null) {
                virtualDisplay = projection.createVirtualDisplay(DISPLAY_NAME,
                        nextScaledWidth, nextScaledHeight, densityDpi(),
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        nextReader.getSurface(), null, handler);
                if (virtualDisplay == null) {
                    blockedReason = "Could not mirror the display";
                    closeReader(nextReader);
                    return false;
                }
            } else {
                // Android 14 rejects a second createVirtualDisplay() on the
                // same projection. resize()+setSurface() is the supported
                // configuration-change path on every version we support.
                virtualDisplay.resize(nextScaledWidth, nextScaledHeight, densityDpi());
                virtualDisplay.setSurface(nextReader.getSurface());
            }
        } catch (RuntimeException e) {
            blockedReason = "Could not mirror the display: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage();
            closeReader(nextReader);
            releaseDisplay();
            return false;
        }

        ImageReader previousReader = imageReader;
        imageReader = nextReader;
        if (previousReader != null) {
            closeReader(previousReader);
        }

        sourceWidth = width;
        sourceHeight = height;
        scaledWidth = nextScaledWidth;
        scaledHeight = nextScaledHeight;
        buffers = new int[][]{new int[scaledWidth * scaledHeight], new int[scaledWidth * scaledHeight]};
        rowPixels = new int[scaledWidth];
        bufferIndex = 0;
        sizeChanged = true;
        return true;
    }

    @Override
    public Frame acquire(long timeoutMillis) {
        if (imageReader == null) {
            return null;
        }
        pace();

        // A rotation changes the display size. The mirror is a fixed-size
        // surface, so without rebuilding it the content would just be
        // letterboxed into the old shape.
        int[] size = displaySize();
        if (size != null && (size[0] != sourceWidth || size[1] != sourceHeight)) {
            if (!configure(size[0], size[1])) {
                return null;
            }
        }

        long deadline = SystemClock.elapsedRealtime() + Math.max(1L, timeoutMillis);
        while (true) {
            Image image = null;
            try {
                image = imageReader.acquireLatestImage();
            } catch (RuntimeException ignored) {
            }
            if (image != null) {
                lastCaptureAt = SystemClock.elapsedRealtime();
                try {
                    return convert(image);
                } catch (RuntimeException e) {
                    return null;
                } finally {
                    image.close();
                }
            }
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) {
                // Nothing arrived, which on a mirrored display means nothing
                // changed. Cheaper than the Accessibility engine, which has to
                // re-capture to find that out.
                return null;
            }
            synchronized (frameLock) {
                if (!imagePending) {
                    try {
                        frameLock.wait(Math.min(remaining, IMAGE_WAIT_SLICE_MILLIS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
                imagePending = false;
            }
        }
    }

    /**
     * RGBA_8888 comes off the surface as R,G,B,A bytes; the rest of the
     * pipeline works in ARGB ints, so red and blue swap. Rows are padded to the
     * surface's stride, which is routinely wider than the image.
     */
    private Frame convert(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer ints = buffer.asIntBuffer();
        int strideInts = plane.getRowStride() / 4;

        bufferIndex ^= 1;
        int[] pixels = buffers[bufferIndex];
        for (int y = 0; y < scaledHeight; y++) {
            int rowStart = y * strideInts;
            if (rowStart + scaledWidth > ints.limit()) {
                break;
            }
            ints.position(rowStart);
            ints.get(rowPixels, 0, scaledWidth);
            int destination = y * scaledWidth;
            for (int x = 0; x < scaledWidth; x++) {
                int value = rowPixels[x];
                pixels[destination + x] = (value & 0xFF00FF00)
                        | ((value & 0x000000FF) << 16)
                        | ((value >> 16) & 0x000000FF);
            }
        }
        return new Frame(pixels, scaledWidth, scaledHeight, System.currentTimeMillis());
    }

    private void pace() {
        if (lastCaptureAt == 0L || minIntervalMillis <= 0L) {
            return;
        }
        long remaining = minIntervalMillis - (SystemClock.elapsedRealtime() - lastCaptureAt);
        if (remaining > 0L) {
            SystemClock.sleep(remaining);
        }
    }

    private int[] displaySize() {
        Display display = defaultDisplay();
        if (display == null) {
            return null;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            return null;
        }
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    private int densityDpi() {
        return Math.max(1, context.getResources().getConfiguration().densityDpi);
    }

    @SuppressWarnings("deprecation")
    private Display defaultDisplay() {
        DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        return manager == null ? null : manager.getDisplay(Display.DEFAULT_DISPLAY);
    }

    @Override
    public int width() {
        return scaledWidth;
    }

    @Override
    public int height() {
        return scaledHeight;
    }

    @Override
    public int sourceWidth() {
        return sourceWidth;
    }

    @Override
    public int sourceHeight() {
        return sourceHeight;
    }

    @Override
    public boolean consumeSizeChanged() {
        boolean changed = sizeChanged;
        sizeChanged = false;
        return changed;
    }

    @Override
    public String blockedReason() {
        return blockedReason;
    }

    @Override
    public void stop() {
        MediaProjection currentProjection = projection;
        releaseDisplay();
        projection = null;
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }
        handler = null;
        buffers = null;
        rowPixels = null;
        scaledWidth = 0;
        scaledHeight = 0;
        sourceWidth = 0;
        sourceHeight = 0;
        if (currentProjection != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                currentProjection.stop();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void releaseDisplay() {
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (RuntimeException ignored) {
            }
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (RuntimeException ignored) {
            }
            imageReader = null;
        }
        synchronized (frameLock) {
            imagePending = false;
        }
    }

    private static void closeReader(ImageReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (RuntimeException ignored) {
        }
    }
}
