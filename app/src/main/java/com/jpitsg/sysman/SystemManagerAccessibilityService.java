package com.jpitsg.sysman;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.concurrent.Executor;

public final class SystemManagerAccessibilityService extends AccessibilityService {
    private static volatile SystemManagerAccessibilityService instance;
    private static volatile boolean highPriorityKeyCaptureWanted;
    // On this device the power menu no longer has a Restart button; rebooting is
    // triggered by a press-and-hold at screen center swiped up to the top.
    private static final long REBOOT_SWIPE_HOLD_MILLIS = 400L;
    private static final long REBOOT_SWIPE_MOVE_MILLIS = 500L;
    private static final String[] PIN_CONFIRM_TEXTS = new String[]{
            "OK",
            "Enter",
            "Done",
            "Confirm",
            "Continue",
            "Submit"
    };
    private static final int PIN_CONFIRM_ATTEMPTS = 3;
    private static final long REBOOT_CPU_WAKE_MILLIS = 120_000L;
    private static final long SCREEN_WAKE_MILLIS = 15_000L;
    private static final long FINAL_SCREEN_WAKE_MILLIS = 30_000L;
    private static final long WAKE_ACTIVITY_SETTLE_MILLIS = 1_200L;

    private WifiChangeMonitor monitor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean rebootRunning;
    private PowerManager.WakeLock rebootCpuWakeLock;
    private PowerManager.WakeLock rebootScreenWakeLock;

    static void sync(Context context) {
        SystemManagerAccessibilityService service = instance;
        if (service != null) {
            service.syncMonitor();
            return;
        }

        Config config = Config.get(context);
        if (config.isTrackingEnabled()
                && config.postOnWifiChange()
                && !config.showWifiMonitorNotification()
                && !PermissionState.accessibilityServiceEnabled(context)) {
            LogStore.append(context, "accessibility", "Hidden Wi-Fi monitor needs Accessibility service enabled");
        }
    }

    static void setHighPriorityKeyCaptureEnabled(boolean enabled) {
        if (highPriorityKeyCaptureWanted == enabled) {
            return;
        }
        highPriorityKeyCaptureWanted = enabled;

        final SystemManagerAccessibilityService service = instance;
        if (service != null) {
            service.handler.post(new Runnable() {
                @Override
                public void run() {
                    service.syncHighPriorityKeyCapture();
                }
            });
        }
    }

    /**
     * Why the service cannot take a screenshot, or null when it can. Screen
     * capture through Accessibility needs no per-session consent, which is what
     * lets the VNC server start unattended.
     */
    static String screenshotBlockedReason(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "Screen capture through Accessibility needs Android 11 or newer";
        }
        SystemManagerAccessibilityService service = instance;
        if (service == null) {
            return "Accessibility service is not connected";
        }
        AccessibilityServiceInfo info = service.getServiceInfo();
        if (info == null) {
            return "Accessibility service info unavailable";
        }
        if ((info.getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) == 0) {
            // The capability is declared in accessibility_service.xml, so this
            // means the grant is stale: turn the service off and on again.
            return "Screenshot capability not granted; re-enable the Accessibility service";
        }
        return null;
    }

    /**
     * Takes one screenshot of the default display. The platform rate-limits
     * this to roughly one call every 333 ms and reports
     * {@code ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT} when called faster, so
     * the caller has to pace itself.
     *
     * @return false when the service is not connected; the callback will not run.
     */
    static boolean takeScreenshotForVnc(Executor executor, TakeScreenshotCallback callback) {
        SystemManagerAccessibilityService service = instance;
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback);
            return true;
        } catch (RuntimeException e) {
            LogStore.append(service, "vnc", "takeScreenshot rejected: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Lights the screen when a VNC client attaches. A dark display captures as
     * black, which looks like a broken server rather than a sleeping phone.
     * Reuses the wake path the reboot automation already relies on.
     */
    static void wakeScreenForVnc() {
        final SystemManagerAccessibilityService service = instance;
        if (service == null) {
            return;
        }
        service.handler.post(new Runnable() {
            @Override
            public void run() {
                service.wakeScreen("vnc-client", SCREEN_WAKE_MILLIS);
            }
        });
    }

    // ---- VNC input injection ------------------------------------------------

    /**
     * Dispatches one gesture on behalf of a VNC client. Callbacks land on this
     * service's handler, which is also the only thread that dispatches, so a
     * caller chaining continued strokes never races itself.
     *
     * @return false when the service is not connected; the callback will not run.
     */
    static boolean dispatchVncGesture(final GestureDescription gesture,
                                      final GestureResultCallback callback) {
        final SystemManagerAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        try {
            return service.dispatchGesture(gesture, callback, service.handler);
        } catch (RuntimeException e) {
            LogStore.append(service, "vnc", "Gesture dispatch rejected: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    static boolean performVncGlobalAction(int action) {
        SystemManagerAccessibilityService service = instance;
        if (service == null || action < 0) {
            return false;
        }
        try {
            return service.performGlobalAction(action);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Types into whatever has input focus, at the cursor.
     *
     * <p>There is no key injection without a platform signature, so text is
     * applied by rewriting the field's contents around its selection. That
     * works in a text field and nowhere else, which is the honest limit of what
     * an accessibility service can do.
     */
    static boolean insertVncText(String text) {
        return editFocusedText(text, false);
    }

    /** Backspace: removes the selection, or the character before the cursor. */
    static boolean deleteVncBackward() {
        return editFocusedText("", true);
    }

    private static boolean editFocusedText(String insert, boolean deleteBackward) {
        SystemManagerAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        AccessibilityNodeInfo focus = null;
        try {
            focus = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focus == null || !focus.isEditable()) {
                return false;
            }
            CharSequence current = focus.getText() == null ? "" : focus.getText();
            int length = current.length();
            int start = clampIndex(focus.getTextSelectionStart(), length);
            int end = clampIndex(focus.getTextSelectionEnd(), length);
            if (start > end) {
                int swap = start;
                start = end;
                end = swap;
            }
            if (deleteBackward && start == end) {
                if (start == 0) {
                    return true;
                }
                start--;
            }

            String next = current.subSequence(0, start) + insert + current.subSequence(end, length);
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next);
            if (!focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return false;
            }
            // The node is stale after the edit, so the caret has to be placed
            // through a refreshed copy or it snaps to the end of the field.
            int caret = start + insert.length();
            if (focus.refresh()) {
                Bundle selection = new Bundle();
                selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret);
                selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret);
                focus.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        } finally {
            if (focus != null) {
                focus.recycle();
            }
        }
    }

    /** Return key: asks the IME to act, falling back to clicking the field. */
    static boolean submitVncText() {
        SystemManagerAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        AccessibilityNodeInfo focus = null;
        try {
            focus = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focus == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && focus.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())) {
                return true;
            }
            return focus.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (RuntimeException e) {
            return false;
        } finally {
            if (focus != null) {
                focus.recycle();
            }
        }
    }

    private static int clampIndex(int index, int length) {
        if (index < 0 || index > length) {
            return length;
        }
        return index;
    }

    static boolean requestReboot(Context context, String reason) {
        SystemManagerAccessibilityService service = instance;
        if (service == null) {
            LogStore.append(context, "reboot", "Accessibility service is not connected; cannot reboot reason=" + reason);
            return false;
        }
        service.startRebootSequence(reason);
        return true;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        monitor = new WifiChangeMonitor(this, "accessibility");
        LogStore.append(this, "accessibility", "Accessibility service connected");
        syncHighPriorityKeyCapture();
        syncMonitor();
        VncManager.sync(this, "accessibility-connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Reboot automation reads the active window only while a reboot sequence is running.
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!highPriorityKeyCaptureWanted
                || event == null
                || event.getAction() != KeyEvent.ACTION_DOWN
                || event.getRepeatCount() != 0) {
            return false;
        }

        String reason;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                reason = "hardware-volume-up-accessibility";
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                reason = "hardware-volume-down-accessibility";
                break;
            case KeyEvent.KEYCODE_POWER:
                reason = "hardware-power-accessibility";
                break;
            default:
                return false;
        }

        HighPriorityAlertPlayer.stopHighPriorityForHardwareButton(this, reason);
        return false;
    }

    @Override
    public void onInterrupt() {
        LogStore.append(this, "accessibility", "Accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        rebootRunning = false;
        releaseWakeLocks();
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
        instance = null;
        // An Accessibility-backed session otherwise keeps serving its last
        // frame until the client idle timeout expires, and a blocked server
        // does not notice when Accessibility later comes back.
        VncManager.sync(this, "accessibility-disconnected");
        LogStore.append(this, "accessibility", "Accessibility service destroyed");
        super.onDestroy();
    }

    private void syncMonitor() {
        if (monitor == null) {
            monitor = new WifiChangeMonitor(this, "accessibility");
        }
        Config config = Config.get(this);
        if (config.isTrackingEnabled() && config.postOnWifiChange() && !config.showWifiMonitorNotification()) {
            monitor.start();
        } else {
            monitor.stop();
        }
    }

    private void syncHighPriorityKeyCapture() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            return;
        }

        int oldFlags = info.flags;
        int newFlags;
        if (highPriorityKeyCaptureWanted) {
            newFlags = oldFlags | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        } else {
            newFlags = oldFlags & ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        }
        if (newFlags == oldFlags) {
            return;
        }

        info.flags = newFlags;
        try {
            setServiceInfo(info);
            LogStore.append(this, "accessibility", "High-priority hardware key capture "
                    + (highPriorityKeyCaptureWanted ? "enabled" : "disabled"));
        } catch (RuntimeException e) {
            LogStore.append(this, "accessibility", "Could not update hardware key capture: "
                    + e.getMessage());
        }
    }

    private void startRebootSequence(final String reason) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (rebootRunning) {
                    LogStore.append(SystemManagerAccessibilityService.this, "reboot", "Reboot automation already running; ignored reason=" + reason);
                    return;
                }
                rebootRunning = true;
                acquireCpuWakeLock();
                wakeScreen("start", SCREEN_WAKE_MILLIS);
                requestWakeActivity("start");

                LogStore.append(SystemManagerAccessibilityService.this, "reboot", "Starting reboot automation reason=" + reason);
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openPowerDialog();
                    }
                }, WAKE_ACTIVITY_SETTLE_MILLIS);
            }
        });
    }

    private void openPowerDialog() {
        wakeScreen("power-dialog", SCREEN_WAKE_MILLIS);
        boolean opened = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        LogStore.append(this, "reboot", "Power dialog global action result=" + opened);
        if (!opened) {
            finishRebootSequence("power-dialog-failed");
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                swipeToReboot();
            }
        }, Config.get(this).rebootPowerDialogWaitMs());
    }

    /**
     * Reboot gesture for the current device: press and hold at the exact centre
     * of the screen, then (holding) swipe straight up to the top. Dispatched as
     * two continued strokes so the touch stays down for the whole motion.
     */
    private void swipeToReboot() {
        wakeScreen("reboot-swipe", SCREEN_WAKE_MILLIS);
        DisplayMetrics metrics = realMetrics();
        final int centerX = metrics.widthPixels / 2;
        final int centerY = metrics.heightPixels / 2;
        final int topY = Math.max(2, Math.round(metrics.heightPixels * 0.02f));
        LogStore.append(this, "reboot", "Reboot swipe center=(" + centerX + "," + centerY + ") -> topY=" + topY);

        Path holdPath = new Path();
        holdPath.moveTo(centerX, centerY);
        holdPath.lineTo(centerX, centerY - 1); // 1px so the stroke has positive length
        final GestureDescription.StrokeDescription hold =
                new GestureDescription.StrokeDescription(holdPath, 0L, REBOOT_SWIPE_HOLD_MILLIS, true);
        GestureDescription holdGesture = new GestureDescription.Builder().addStroke(hold).build();

        boolean dispatched = dispatchGesture(holdGesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gesture) {
                dispatchRebootSwipe(hold, centerX, centerY, topY);
            }

            @Override
            public void onCancelled(GestureDescription gesture) {
                LogStore.append(SystemManagerAccessibilityService.this, "reboot", "Reboot hold cancelled; continuing to swipe");
                dispatchRebootSwipe(hold, centerX, centerY, topY);
            }
        }, handler);
        if (!dispatched) {
            LogStore.append(this, "reboot", "Reboot hold gesture dispatch failed; skipping to PIN step");
            afterRebootSwipe();
        }
    }

    private void dispatchRebootSwipe(GestureDescription.StrokeDescription hold, int centerX, int centerY, int topY) {
        Path swipePath = new Path();
        swipePath.moveTo(centerX, centerY - 1);
        swipePath.lineTo(centerX, topY);
        GestureDescription.StrokeDescription swipe = hold.continueStroke(swipePath, 0L, REBOOT_SWIPE_MOVE_MILLIS, false);
        GestureDescription swipeGesture = new GestureDescription.Builder().addStroke(swipe).build();

        boolean dispatched = dispatchGesture(swipeGesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gesture) {
                LogStore.append(SystemManagerAccessibilityService.this, "reboot", "Reboot swipe completed");
                afterRebootSwipe();
            }

            @Override
            public void onCancelled(GestureDescription gesture) {
                LogStore.append(SystemManagerAccessibilityService.this, "reboot", "Reboot swipe cancelled");
                afterRebootSwipe();
            }
        }, handler);
        if (!dispatched) {
            LogStore.append(this, "reboot", "Reboot swipe gesture dispatch failed; skipping to PIN step");
            afterRebootSwipe();
        }
    }

    private void afterRebootSwipe() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                enterPinSequence();
            }
        }, Config.get(SystemManagerAccessibilityService.this).rebootStepWaitMs());
    }

    @SuppressWarnings("deprecation")
    private DisplayMetrics realMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null && windowManager.getDefaultDisplay() != null) {
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
        } else {
            metrics.setTo(getResources().getDisplayMetrics());
        }
        return metrics;
    }

    private void enterPinSequence() {
        wakeScreen("pin-entry", SCREEN_WAKE_MILLIS);
        String sequence = Config.get(this).rebootPinSequence();
        if (sequence.isEmpty()) {
            LogStore.append(this, "reboot", "No reboot PIN sequence configured; automation finished after restart clicks");
            finishRebootSequence("restart-clicks-complete");
            return;
        }
        clickPinDigit(sequence, 0);
    }

    private void clickPinDigit(final String sequence, final int index) {
        if (index >= sequence.length()) {
            LogStore.append(this, "reboot", "PIN sequence entry complete length=" + sequence.length());
            wakeScreen("pin-complete", FINAL_SCREEN_WAKE_MILLIS);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    clickPinConfirm(1);
                }
            }, Config.get(SystemManagerAccessibilityService.this).rebootStepWaitMs());
            return;
        }

        char digit = sequence.charAt(index);
        if (!Character.isDigit(digit)) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    clickPinDigit(sequence, index + 1);
                }
            }, Config.get(SystemManagerAccessibilityService.this).rebootStepWaitMs());
            return;
        }

        wakeScreen("pin-digit-" + index, SCREEN_WAKE_MILLIS);
        boolean clicked = clickText(Character.toString(digit), false);
        LogStore.append(this, "reboot", "PIN digit index=" + index + " result=" + clicked);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                clickPinDigit(sequence, index + 1);
            }
        }, Config.get(SystemManagerAccessibilityService.this).rebootStepWaitMs());
    }

    private boolean clickAnyText(String[] texts, boolean contains) {
        for (String text : texts) {
            if (clickText(text, contains)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickText(String text, boolean contains) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            LogStore.append(this, "reboot", "No active accessibility window while looking for text=" + text);
            return false;
        }
        try {
            return clickMatchingNode(root, text, contains);
        } catch (RuntimeException e) {
            LogStore.append(this, "reboot", "Click text failed text=" + text + " error=" + e.getMessage());
        } finally {
            root.recycle();
        }
        return false;
    }

    private boolean clickMatchingNode(AccessibilityNodeInfo node, String target, boolean contains) {
        if (node == null) {
            return false;
        }
        if ((matchesNodeText(node.getText(), target, contains)
                || matchesNodeText(node.getContentDescription(), target, contains))
                && clickNodeOrClickableParent(node)) {
            return true;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            try {
                if (child != null && clickMatchingNode(child, target, contains)) {
                    return true;
                }
            } finally {
                if (child != null) {
                    child.recycle();
                }
            }
        }
        return false;
    }

    private boolean matchesNodeText(CharSequence value, String target, boolean contains) {
        if (value == null || target == null) {
            return false;
        }
        String candidate = value.toString().trim();
        if (candidate.isEmpty()) {
            return false;
        }
        if (contains) {
            return candidate.toLowerCase().contains(target.toLowerCase());
        }
        if (candidate.equalsIgnoreCase(target)) {
            return true;
        }
        return target.length() == 1
                && Character.isDigit(target.charAt(0))
                && (candidate.startsWith(target + " ") || candidate.startsWith(target + "\n"));
    }

    private boolean clickNodeOrClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        try {
            for (int i = 0; current != null && i < 8; i++) {
                if (current.isEnabled() && current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
                AccessibilityNodeInfo parent = current.getParent();
                current.recycle();
                current = parent;
            }
        } catch (RuntimeException e) {
            LogStore.append(this, "reboot", "Node click failed: " + e.getMessage());
        } finally {
            if (current != null) {
                current.recycle();
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private void acquireCpuWakeLock() {
        releaseCpuWakeLock();
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        try {
            rebootCpuWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SystemManager:reboot-cpu");
            rebootCpuWakeLock.acquire(REBOOT_CPU_WAKE_MILLIS);
            LogStore.append(this, "reboot", "CPU wake lock acquired for reboot automation");
        } catch (RuntimeException e) {
            LogStore.append(this, "reboot", "CPU wake lock acquire failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void wakeScreen(String reason, long timeoutMillis) {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        boolean interactiveBefore = powerManager.isInteractive();
        try {
            releaseScreenWakeLock();
            rebootScreenWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "SystemManager:reboot-screen");
            rebootScreenWakeLock.acquire(Math.max(1000L, timeoutMillis));
            LogStore.append(this, "reboot", "Screen wake requested reason=" + reason
                    + " interactiveBefore=" + interactiveBefore
                    + " timeout_ms=" + timeoutMillis);
        } catch (RuntimeException e) {
            LogStore.append(this, "reboot", "Screen wake failed reason=" + reason + ": " + e.getMessage());
        }
    }

    private void requestWakeActivity(String reason) {
        try {
            Intent intent = new Intent(this, RebootWakeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            intent.putExtra(RebootWakeActivity.EXTRA_REASON, reason);
            startActivity(intent);
            LogStore.append(this, "reboot", "Wake activity requested reason=" + reason);
        } catch (RuntimeException e) {
            LogStore.append(this, "reboot", "Wake activity failed reason=" + reason + ": " + e.getMessage());
        }
    }

    private void clickPinConfirm(final int attempt) {
        wakeScreen("pin-confirm-" + attempt, FINAL_SCREEN_WAKE_MILLIS);
        boolean clicked = clickAnyText(PIN_CONFIRM_TEXTS, false);
        LogStore.append(this, "reboot", "PIN confirmation click attempt=" + attempt + " result=" + clicked);
        if (!clicked && attempt < PIN_CONFIRM_ATTEMPTS) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    clickPinConfirm(attempt + 1);
                }
            }, Config.get(SystemManagerAccessibilityService.this).rebootStepWaitMs());
            return;
        }
        finishRebootSequence(clicked ? "pin-confirm-clicked" : "pin-confirm-not-found");
    }

    private void finishRebootSequence(String result) {
        long releaseDelay = (result.startsWith("pin-") || "restart-clicks-complete".equals(result))
                ? FINAL_SCREEN_WAKE_MILLIS
                : 5000L;
        if ("restart-clicks-complete".equals(result)) {
            wakeScreen("restart-clicks-complete", FINAL_SCREEN_WAKE_MILLIS);
        }
        LogStore.append(this, "reboot", "Reboot automation sequence finished result=" + result);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                rebootRunning = false;
                releaseWakeLocks();
            }
        }, releaseDelay);
    }

    private void releaseWakeLocks() {
        releaseScreenWakeLock();
        releaseCpuWakeLock();
    }

    private void releaseCpuWakeLock() {
        if (rebootCpuWakeLock != null) {
            try {
                if (rebootCpuWakeLock.isHeld()) {
                    rebootCpuWakeLock.release();
                }
            } catch (RuntimeException ignored) {
            }
            rebootCpuWakeLock = null;
        }
    }

    private void releaseScreenWakeLock() {
        if (rebootScreenWakeLock != null) {
            try {
                if (rebootScreenWakeLock.isHeld()) {
                    rebootScreenWakeLock.release();
                }
            } catch (RuntimeException ignored) {
            }
            rebootScreenWakeLock = null;
        }
    }
}
