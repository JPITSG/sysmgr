package com.jpitsg.sysman;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;
import java.util.Locale;

/**
 * Runs the capture pipeline for a bounded window and reports what it measured.
 *
 * <p>The server has no client yet, so this is how the frame source and the
 * differ get exercised on a real screen: frame rate, how much of the display
 * actually changes between frames, and whether any capture failed. A run that
 * ends with no failures is also the evidence that HardwareBuffers are being
 * closed — leak them and the pool is exhausted within seconds, after which
 * every capture fails.
 */
final class VncCaptureProbe {
    static final int DURATION_SECONDS = 15;
    private static final long ACQUIRE_TIMEOUT_MILLIS = 3_000L;

    interface Callback {
        void onFinished(String summary);
    }

    private static volatile boolean running;

    private VncCaptureProbe() {
    }

    static boolean isRunning() {
        return running;
    }

    /** Starts a probe on a worker thread; the callback lands on the main thread. */
    static boolean start(final Context context, final Callback callback) {
        if (running) {
            return false;
        }
        running = true;
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                String summary;
                try {
                    summary = measure(app);
                } catch (RuntimeException e) {
                    summary = "Probe crashed: " + e.getClass().getSimpleName() + ": " + e.getMessage();
                } finally {
                    running = false;
                }
                final String result = summary;
                VncStateStore.setProbeResult(app, result);
                LogStore.append(app, "vnc", "Capture probe: " + result.replace('\n', ' '));
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onFinished(result);
                    }
                });
            }
        }, "SystemManagerVncProbe").start();
        return true;
    }

    private static String measure(Context context) {
        Config config = Config.get(context);
        int scalePercent = config.vncScalePercent();
        // Always the Accessibility engine: the probe is a diagnostic that has to
        // work without spending a single-use screen-capture consent.
        AccessibilityFrameSource source = new AccessibilityFrameSource(context);
        if (!source.start(scalePercent, config.vncMaxFps())) {
            return "Cannot capture: " + source.blockedReason();
        }

        int frames = 0;
        int failures = 0;
        int resizes = 0;
        int updates = 0;
        long totalRects = 0;
        long totalDirtyArea = 0;
        long slowestFrameMillis = 0;

        FrameDiffer differ = null;
        FrameSource.Frame previous = null;

        // Read off the source before stopping it: stop() clears the geometry.
        int outWidth = 0;
        int outHeight = 0;
        long outIntervalMillis = 0L;
        int outFailures = 0;
        String outLastFailure = "";

        long startedAt = SystemClock.elapsedRealtime();
        long deadline = startedAt + DURATION_SECONDS * 1000L;
        try {
            while (SystemClock.elapsedRealtime() < deadline) {
                long frameStart = SystemClock.elapsedRealtime();
                FrameSource.Frame frame = source.acquire(ACQUIRE_TIMEOUT_MILLIS);
                if (frame == null) {
                    failures++;
                    continue;
                }
                frames++;
                slowestFrameMillis = Math.max(slowestFrameMillis,
                        SystemClock.elapsedRealtime() - frameStart);

                boolean resized = source.consumeSizeChanged()
                        || differ == null
                        || differ.width() != frame.width
                        || differ.height() != frame.height;
                if (resized) {
                    differ = new FrameDiffer(frame.width, frame.height);
                    previous = null;
                    resizes++;
                }
                if (previous != null) {
                    List<Rect> dirty = differ.diff(frame.pixels, previous.pixels);
                    if (!dirty.isEmpty()) {
                        updates++;
                        totalRects += dirty.size();
                        totalDirtyArea += FrameDiffer.area(dirty);
                    }
                }
                previous = frame;
            }
        } finally {
            outWidth = source.width();
            outHeight = source.height();
            outIntervalMillis = source.intervalMillis();
            outFailures = source.failureCount();
            outLastFailure = source.lastFailure();
            source.stop();
        }

        double seconds = Math.max(0.001d, (SystemClock.elapsedRealtime() - startedAt) / 1000d);
        long frameArea = (long) outWidth * outHeight;
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.US, "%d×%d at %d%% · %d frames in %.1fs (%.1f fps)",
                outWidth, outHeight, scalePercent, frames, seconds, frames / seconds));
        if (updates > 0 && frameArea > 0) {
            text.append(String.format(Locale.US,
                    "\nChanged on %d of %d comparisons · %.1f rects avg · %.1f%% of screen avg",
                    updates, Math.max(0, frames - resizes),
                    totalRects / (double) updates,
                    100d * (totalDirtyArea / (double) updates) / frameArea));
        } else {
            text.append("\nNo change detected between frames");
        }
        text.append(String.format(Locale.US, "\nSlowest frame %d ms · interval %d ms · %d failures",
                slowestFrameMillis, outIntervalMillis, Math.max(failures, outFailures)));
        if (!outLastFailure.isEmpty()) {
            text.append("\nLast failure: ").append(outLastFailure);
        }
        if (resizes > 1) {
            text.append("\nDisplay resized ").append(resizes - 1).append(" time(s)");
        }
        return text.toString();
    }
}
