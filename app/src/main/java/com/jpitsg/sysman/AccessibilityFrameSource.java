package com.jpitsg.sysman;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.SystemClock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Screen capture through {@link AccessibilityService#takeScreenshot}.
 *
 * <p>The default engine, because it needs no per-session consent and so can
 * start unattended when a Wi-Fi rule fires. The price is the platform's rate
 * limit of one screenshot every 333 ms — a hard ceiling of about three frames a
 * second, which {@link FrameDiffer} is what makes tolerable.
 *
 * <p>Buffers are sized from the first frame rather than from the display
 * metrics, so a rotation is just another size change and there is no second
 * source of truth to keep in step. Two pixel buffers alternate so a frame stays
 * readable while its successor is captured, which is exactly the lifetime the
 * differ needs and saves a full copy per frame.
 */
final class AccessibilityFrameSource implements FrameSource {
    /** Platform limit is 333 ms; the margin keeps us off the error path. */
    private static final long MIN_CAPTURE_INTERVAL_MILLIS = 340L;
    private static final long MAX_CAPTURE_INTERVAL_MILLIS = 2_000L;

    private final Context context;

    /** Discards a callback that arrives after its acquire already gave up. */
    private final AtomicInteger generation = new AtomicInteger();

    private ExecutorService executor;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private int scalePercent = Config.VNC_SCALE_FULL;
    private long minIntervalMillis = MIN_CAPTURE_INTERVAL_MILLIS;
    private long lastCaptureAt;

    private int sourceWidth;
    private int sourceHeight;
    private int scaledWidth;
    private int scaledHeight;
    private Bitmap target;
    private Canvas canvas;
    private Rect destRect;
    private int[][] buffers;
    private int bufferIndex;

    private volatile boolean sizeChanged;
    private volatile String blockedReason = "";
    private volatile String lastFailure = "";
    private int failureCount;

    AccessibilityFrameSource(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean start(int scalePercent) {
        String blocked = SystemManagerAccessibilityService.screenshotBlockedReason(context);
        if (blocked != null) {
            blockedReason = blocked;
            return false;
        }
        this.scalePercent = scalePercent < Config.VNC_SCALE_HALF || scalePercent > Config.VNC_SCALE_FULL
                ? Config.VNC_SCALE_FULL
                : scalePercent;
        this.minIntervalMillis = MIN_CAPTURE_INTERVAL_MILLIS;
        this.lastCaptureAt = 0L;
        this.failureCount = 0;
        this.lastFailure = "";
        this.blockedReason = "";
        this.executor = Executors.newSingleThreadExecutor();
        return true;
    }

    @Override
    public Frame acquire(long timeoutMillis) {
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null || currentExecutor.isShutdown()) {
            return null;
        }
        pace();

        final int gen = generation.incrementAndGet();
        final CountDownLatch latch = new CountDownLatch(1);
        final Frame[] holder = new Frame[1];

        boolean dispatched = SystemManagerAccessibilityService.takeScreenshotForVnc(
                currentExecutor,
                new AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(AccessibilityService.ScreenshotResult result) {
                        try {
                            holder[0] = consume(result, gen);
                        } catch (RuntimeException e) {
                            noteFailure("capture crashed: " + e.getClass().getSimpleName()
                                    + ": " + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        handleFailure(errorCode);
                        latch.countDown();
                    }
                });
        if (!dispatched) {
            blockedReason = "Accessibility service is not connected";
            return null;
        }

        lastCaptureAt = SystemClock.elapsedRealtime();
        try {
            if (!latch.await(Math.max(200L, timeoutMillis), TimeUnit.MILLISECONDS)) {
                // Bump the generation so a late callback discards its frame
                // rather than writing into a buffer the caller may be reading.
                generation.incrementAndGet();
                noteFailure("capture timed out");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            generation.incrementAndGet();
            return null;
        }
        return holder[0];
    }

    /**
     * Converts one screenshot into a frame. Runs on the capture executor. The
     * HardwareBuffer must be closed on every path — leaking them exhausts the
     * pool and every later capture fails.
     */
    private Frame consume(AccessibilityService.ScreenshotResult result, int gen) {
        HardwareBuffer hardwareBuffer = result.getHardwareBuffer();
        if (hardwareBuffer == null) {
            noteFailure("no hardware buffer");
            return null;
        }
        try {
            if (generation.get() != gen) {
                return null;
            }
            Bitmap wrapped = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.getColorSpace());
            if (wrapped == null) {
                noteFailure("could not wrap hardware buffer");
                return null;
            }
            try {
                ensureBuffers(wrapped.getWidth(), wrapped.getHeight());
                // Hardware bitmaps cannot be read directly, so this draw into a
                // reused software bitmap is both the copy and the downscale.
                canvas.drawBitmap(wrapped, null, destRect, paint);
            } finally {
                wrapped.recycle();
            }

            bufferIndex ^= 1;
            int[] pixels = buffers[bufferIndex];
            target.getPixels(pixels, 0, scaledWidth, 0, 0, scaledWidth, scaledHeight);
            return new Frame(pixels, scaledWidth, scaledHeight, System.currentTimeMillis());
        } finally {
            hardwareBuffer.close();
        }
    }

    /** Allocates, or reallocates after a rotation or display size change. */
    private void ensureBuffers(int srcWidth, int srcHeight) {
        if (target != null && srcWidth == sourceWidth && srcHeight == sourceHeight) {
            return;
        }
        sourceWidth = srcWidth;
        sourceHeight = srcHeight;
        scaledWidth = Math.max(1, srcWidth * scalePercent / 100);
        scaledHeight = Math.max(1, srcHeight * scalePercent / 100);

        if (target != null) {
            target.recycle();
        }
        target = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(target);
        destRect = new Rect(0, 0, scaledWidth, scaledHeight);
        buffers = new int[][]{new int[scaledWidth * scaledHeight], new int[scaledWidth * scaledHeight]};
        bufferIndex = 0;
        sizeChanged = true;
    }

    /** Sleeps out whatever is left of the platform's minimum interval. */
    private void pace() {
        if (lastCaptureAt == 0L) {
            return;
        }
        long elapsed = SystemClock.elapsedRealtime() - lastCaptureAt;
        long remaining = minIntervalMillis - elapsed;
        if (remaining > 0L) {
            SystemClock.sleep(remaining);
        }
    }

    private void handleFailure(int errorCode) {
        if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
            // Back off rather than hammer: the limit moves with the platform,
            // so let it tell us what it actually is.
            minIntervalMillis = Math.min(MAX_CAPTURE_INTERVAL_MILLIS, minIntervalMillis + 50L);
            noteFailure("rate limited; interval now " + minIntervalMillis + " ms");
            return;
        }
        noteFailure(failureLabel(errorCode));
    }

    private static String failureLabel(int errorCode) {
        switch (errorCode) {
            case AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR:
                return "internal error";
            case AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS:
                return "no accessibility access";
            case AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY:
                return "invalid display";
            case AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW:
                return "protected content on screen";
            default:
                return "error " + errorCode;
        }
    }

    private synchronized void noteFailure(String message) {
        failureCount++;
        lastFailure = message;
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
    public boolean consumeSizeChanged() {
        boolean changed = sizeChanged;
        sizeChanged = false;
        return changed;
    }

    @Override
    public String blockedReason() {
        return blockedReason;
    }

    synchronized int failureCount() {
        return failureCount;
    }

    synchronized String lastFailure() {
        return lastFailure;
    }

    /** The interval the engine settled on, which the rate limiter may have raised. */
    long intervalMillis() {
        return minIntervalMillis;
    }

    @Override
    public void stop() {
        generation.incrementAndGet();
        if (executor != null) {
            executor.shutdownNow();
            try {
                // A capture already inside drawBitmap is not interruptible, and
                // recycling the target underneath it would throw. Give it a
                // moment to land before pulling the bitmap away.
                executor.awaitTermination(500L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        if (target != null) {
            target.recycle();
            target = null;
        }
        canvas = null;
        destRect = null;
        buffers = null;
        scaledWidth = 0;
        scaledHeight = 0;
        sourceWidth = 0;
        sourceHeight = 0;
    }
}
