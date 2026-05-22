package com.jpitsg.sysman;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public final class SystemManagerAccessibilityService extends AccessibilityService {
    private static volatile SystemManagerAccessibilityService instance;
    private static final String[] RESTART_TEXTS = new String[]{
            "Restart",
            "Reboot",
            "Restart phone",
            "Restart device"
    };
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
        syncMonitor();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Reboot automation reads the active window only while a reboot sequence is running.
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
                clickRestart(1);
            }
        }, Config.get(this).rebootPowerDialogWaitMs());
    }

    private void clickRestart(final int step) {
        wakeScreen("restart-click-" + step, SCREEN_WAKE_MILLIS);
        boolean clicked = clickAnyText(RESTART_TEXTS, true);
        LogStore.append(this, "reboot", "Restart click step=" + step + " result=" + clicked);
        if (step < 2) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    clickRestart(step + 1);
                }
            }, Config.get(SystemManagerAccessibilityService.this).rebootStepWaitMs());
            return;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                enterPinSequence();
            }
        }, Config.get(SystemManagerAccessibilityService.this).rebootStepWaitMs());
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
