package com.jpitsg.sysman;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;

/**
 * Turns RFB pointer and key events into accessibility gestures and actions.
 *
 * <p>Pointer handling is a small state machine because {@code dispatchGesture}
 * allows exactly one gesture at a time — dispatching a second cancels the
 * first — while a drag is a chain of continued strokes that each have to be
 * dispatched after the previous one lands. So only one is ever in flight, and
 * the client's latest position is coalesced into whatever goes out next. That
 * also makes it self-pacing: a client sending sixty moves a second and one
 * sending five both produce a smooth drag, just with different granularity.
 *
 * <p>A held stroke that is not continued gets cancelled by the platform, so a
 * stationary touch is kept alive by oscillating a single pixel — well inside
 * the touch slop, so apps still read it as stationary.
 */
final class VncInputInjector {
    private static final int BUTTON_LEFT = 1;
    private static final int BUTTON_RIGHT = 4;
    private static final int WHEEL_UP = 8;
    private static final int WHEEL_DOWN = 16;

    private static final long DOWN_SEGMENT_MILLIS = 50L;
    private static final long DRAG_SEGMENT_MILLIS = 40L;
    private static final long HOLD_SEGMENT_MILLIS = 120L;
    private static final long RELEASE_SEGMENT_MILLIS = 30L;
    private static final long LONG_PRESS_MILLIS = 600L;
    private static final long SCROLL_MILLIS = 180L;
    /** A wheel notch scrolls a quarter of the display. */
    private static final int SCROLL_DIVISOR = 4;
    private static final int MAX_QUEUED_SCROLL = 4;

    private final Context context;
    private final int frameWidth;
    private final int frameHeight;
    private final int displayWidth;
    private final int displayHeight;

    private final Object lock = new Object();
    private boolean stopped;
    private boolean dispatchInFlight;

    private boolean touchDown;
    private GestureDescription.StrokeDescription lastStroke;
    private int lastX;
    private int lastY;
    private boolean holdWiggle;

    private int pendingX;
    private int pendingY;
    private int pendingMask;
    private int previousMask;
    private int pendingScroll;
    private boolean pendingLongPress;
    private volatile boolean typingWorked = true;

    private final AccessibilityService.GestureResultCallback gestureCallback =
            new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gesture) {
                    finishDispatch(false);
                }

                @Override
                public void onCancelled(GestureDescription gesture) {
                    // A cancelled continuation breaks the chain: the touch is
                    // gone as far as the platform is concerned, so the next
                    // press has to start a fresh stroke.
                    finishDispatch(true);
                }
            };

    VncInputInjector(Context context, int frameWidth, int frameHeight,
                     int displayWidth, int displayHeight) {
        this.context = context.getApplicationContext();
        this.frameWidth = Math.max(1, frameWidth);
        this.frameHeight = Math.max(1, frameHeight);
        this.displayWidth = Math.max(1, displayWidth);
        this.displayHeight = Math.max(1, displayHeight);
    }

    // ---- Pointer ------------------------------------------------------------

    void onPointerEvent(int buttonMask, int frameX, int frameY) {
        if (isViewOnly()) {
            return;
        }
        int x = clampX(frameX * displayWidth / frameWidth);
        int y = clampY(frameY * displayHeight / frameHeight);
        synchronized (lock) {
            if (stopped) {
                return;
            }
            pendingX = x;
            pendingY = y;
            // Buttons act on the press edge; RFB reports a wheel notch as a
            // press and release of the same button, which would otherwise
            // scroll twice.
            int pressed = buttonMask & ~previousMask;
            previousMask = buttonMask;
            pendingMask = buttonMask;
            if (!touchDown) {
                if ((pressed & BUTTON_RIGHT) != 0) {
                    pendingLongPress = true;
                }
                if ((pressed & WHEEL_UP) != 0 && pendingScroll < MAX_QUEUED_SCROLL) {
                    pendingScroll++;
                }
                if ((pressed & WHEEL_DOWN) != 0 && pendingScroll > -MAX_QUEUED_SCROLL) {
                    pendingScroll--;
                }
            }
        }
        pump();
    }

    private void pump() {
        GestureDescription gesture;
        synchronized (lock) {
            if (stopped || dispatchInFlight) {
                return;
            }
            try {
                gesture = nextGesture();
            } catch (RuntimeException e) {
                // Building a stroke throws on a degenerate or off-display path.
                // The client's next event starts a fresh one; letting it escape
                // would unwind the reader thread and drop the session.
                LogStore.append(context, "vnc", "Dropped gesture: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                resetTouch();
                return;
            }
            if (gesture == null) {
                return;
            }
            dispatchInFlight = true;
        }
        // Dispatched outside the lock: it is a binder call, and the callback
        // can land before it returns.
        if (!SystemManagerAccessibilityService.dispatchVncGesture(gesture, gestureCallback)) {
            synchronized (lock) {
                dispatchInFlight = false;
                resetTouch();
            }
        }
    }

    private void finishDispatch(boolean cancelled) {
        synchronized (lock) {
            dispatchInFlight = false;
            if (cancelled) {
                resetTouch();
            }
        }
        pump();
    }

    /** Decides the next gesture from the touch state and what the client last sent. */
    private GestureDescription nextGesture() {
        if (touchDown) {
            boolean stillDown = (pendingMask & BUTTON_LEFT) != 0;
            if (!stillDown) {
                return continueTouch(pendingX, pendingY, false, RELEASE_SEGMENT_MILLIS);
            }
            if (pendingX != lastX || pendingY != lastY) {
                return continueTouch(pendingX, pendingY, true, DRAG_SEGMENT_MILLIS);
            }
            holdWiggle = !holdWiggle;
            return continueTouch(lastX, nudge(lastY), true, HOLD_SEGMENT_MILLIS);
        }
        if (pendingLongPress) {
            pendingLongPress = false;
            return longPress(pendingX, pendingY);
        }
        if (pendingScroll != 0) {
            int direction = pendingScroll > 0 ? 1 : -1;
            pendingScroll -= direction;
            return scroll(pendingX, pendingY, direction);
        }
        if ((pendingMask & BUTTON_LEFT) != 0) {
            return beginTouch(pendingX, pendingY);
        }
        return null;
    }

    private GestureDescription beginTouch(int x, int y) {
        int endY = nudge(y);
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, endY);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0L, DOWN_SEGMENT_MILLIS, true);
        lastStroke = stroke;
        lastX = x;
        lastY = endY;
        touchDown = true;
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    private GestureDescription continueTouch(int x, int y, boolean willContinue, long duration) {
        if (lastStroke == null) {
            resetTouch();
            return null;
        }
        int endX = x;
        int endY = y;
        if (endX == lastX && endY == lastY) {
            // A stroke needs a path with length; zero movement is rejected.
            endY = nudge(lastY);
        }
        Path path = new Path();
        path.moveTo(lastX, lastY);
        path.lineTo(endX, endY);

        GestureDescription.StrokeDescription stroke;
        try {
            stroke = lastStroke.continueStroke(path, 0L, duration, willContinue);
        } catch (RuntimeException e) {
            resetTouch();
            return null;
        }
        lastStroke = willContinue ? stroke : null;
        lastX = endX;
        lastY = endY;
        touchDown = willContinue;
        return new GestureDescription.Builder().addStroke(stroke).build();
    }

    private GestureDescription longPress(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, nudge(y));
        return new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, LONG_PRESS_MILLIS, false))
                .build();
    }

    /** Wheel up scrolls content up, which means the finger travels down. */
    private GestureDescription scroll(int x, int y, int direction) {
        int distance = Math.max(1, displayHeight / SCROLL_DIVISOR);
        int fromY = clampY(y);
        int toY = clampY(fromY + direction * distance);
        if (toY == fromY) {
            toY = clampY(fromY - direction * distance);
        }
        if (toY == fromY) {
            return null;
        }
        Path path = new Path();
        path.moveTo(clampX(x), fromY);
        path.lineTo(clampX(x), toY);
        return new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, SCROLL_MILLIS, false))
                .build();
    }

    private void resetTouch() {
        touchDown = false;
        lastStroke = null;
    }

    // ---- Keyboard -----------------------------------------------------------

    void onKeyEvent(boolean down, int keysym) {
        // Acting on press only: without key injection there is no meaningful
        // release, and handling both would type everything twice.
        if (!down || isViewOnly()) {
            return;
        }
        if (keysym == VncKeymap.KEYSYM_BACKSPACE) {
            SystemManagerAccessibilityService.deleteVncBackward();
            return;
        }
        if (keysym == VncKeymap.KEYSYM_RETURN) {
            SystemManagerAccessibilityService.submitVncText();
            return;
        }
        if (keysym == VncKeymap.KEYSYM_DELETE || keysym == VncKeymap.KEYSYM_TAB) {
            return;
        }
        char typed = VncKeymap.charFor(keysym);
        if (typed != 0) {
            boolean applied = SystemManagerAccessibilityService.insertVncText(String.valueOf(typed));
            // Logged on the transition only: typing into a window with no text
            // field would otherwise write a line to disk per keystroke.
            if (!applied && typingWorked) {
                LogStore.append(context, "vnc", "Nothing focused to type into");
            }
            typingWorked = applied;
            return;
        }
        int action = VncKeymap.globalActionFor(keysym);
        if (action != VncKeymap.GLOBAL_ACTION_NONE) {
            SystemManagerAccessibilityService.performVncGlobalAction(action);
            return;
        }
        if (!VncKeymap.isModifier(keysym)) {
            LogStore.append(context, "vnc", "Ignored key " + VncKeymap.describe(keysym));
        }
    }

    // ---- Lifecycle ----------------------------------------------------------

    /** Releases a held touch so a disconnect does not leave a finger down. */
    void stop() {
        GestureDescription release = null;
        synchronized (lock) {
            if (stopped) {
                return;
            }
            stopped = true;
            if (touchDown && lastStroke != null && !dispatchInFlight) {
                release = continueTouch(lastX, lastY, false, RELEASE_SEGMENT_MILLIS);
            }
            resetTouch();
        }
        if (release != null) {
            SystemManagerAccessibilityService.dispatchVncGesture(release, null);
        }
    }

    private boolean isViewOnly() {
        return Config.get(context).vncViewOnly();
    }

    /**
     * Nudges one pixel to give a path positive length, away from the edge so
     * the result stays on the display — a gesture outside it is rejected.
     */
    private int nudge(int y) {
        return y + 1 < displayHeight ? y + 1 : Math.max(0, y - 1);
    }

    private int clampX(int x) {
        return Math.max(0, Math.min(displayWidth - 1, x));
    }

    private int clampY(int y) {
        return Math.max(0, Math.min(displayHeight - 1, y));
    }
}
