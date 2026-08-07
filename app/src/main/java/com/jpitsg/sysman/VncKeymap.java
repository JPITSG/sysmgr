package com.jpitsg.sysman;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;

/**
 * Maps X11 keysyms onto what an accessibility service is actually allowed to
 * do.
 *
 * <p>There is no way to inject arbitrary key codes without {@code
 * INJECT_EVENTS}, which needs a platform signature. So printable characters go
 * into whatever text field has input focus, and the keys that would otherwise
 * be lost are mapped onto global actions instead. A client can type, steer with
 * the arrow keys and press the system buttons; it cannot send an arbitrary
 * keystroke to an arbitrary app.
 */
final class VncKeymap {
    static final int GLOBAL_ACTION_NONE = -1;

    // Keys handled as editing operations on the focused node rather than as
    // global actions.
    static final int KEYSYM_BACKSPACE = 0xFF08;
    static final int KEYSYM_TAB = 0xFF09;
    static final int KEYSYM_RETURN = 0xFF0D;
    static final int KEYSYM_DELETE = 0xFFFF;

    private static final int KEYSYM_ESCAPE = 0xFF1B;
    private static final int KEYSYM_HOME = 0xFF50;
    private static final int KEYSYM_LEFT = 0xFF51;
    private static final int KEYSYM_UP = 0xFF52;
    private static final int KEYSYM_RIGHT = 0xFF53;
    private static final int KEYSYM_DOWN = 0xFF54;
    private static final int KEYSYM_END = 0xFF57;
    private static final int KEYSYM_F1 = 0xFFBE;
    private static final int KEYSYM_F2 = 0xFFBF;
    private static final int KEYSYM_F3 = 0xFFC0;
    private static final int KEYSYM_F4 = 0xFFC1;
    private static final int KEYSYM_F5 = 0xFFC2;
    private static final int KEYSYM_F6 = 0xFFC3;

    /** Keysyms at or above this are Unicode with a fixed offset. */
    private static final int UNICODE_BASE = 0x01000000;

    private VncKeymap() {
    }

    /**
     * The global action for a keysym, or {@link #GLOBAL_ACTION_NONE}. Actions
     * the running platform is too old for map to none rather than throwing.
     */
    static int globalActionFor(int keysym) {
        switch (keysym) {
            case KEYSYM_ESCAPE:
                return AccessibilityService.GLOBAL_ACTION_BACK;
            case KEYSYM_HOME:
                return AccessibilityService.GLOBAL_ACTION_HOME;
            case KEYSYM_END:
                return AccessibilityService.GLOBAL_ACTION_RECENTS;
            case KEYSYM_LEFT:
                return sinceTiramisu(AccessibilityService.GLOBAL_ACTION_DPAD_LEFT);
            case KEYSYM_UP:
                return sinceTiramisu(AccessibilityService.GLOBAL_ACTION_DPAD_UP);
            case KEYSYM_RIGHT:
                return sinceTiramisu(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT);
            case KEYSYM_DOWN:
                return sinceTiramisu(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN);
            case KEYSYM_F1:
                return AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS;
            case KEYSYM_F2:
                return AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS;
            case KEYSYM_F3:
                return sinceBaklava(AccessibilityService.GLOBAL_ACTION_MENU);
            case KEYSYM_F4:
                return AccessibilityService.GLOBAL_ACTION_POWER_DIALOG;
            case KEYSYM_F5:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                        : GLOBAL_ACTION_NONE;
            case KEYSYM_F6:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
                        : GLOBAL_ACTION_NONE;
            default:
                return GLOBAL_ACTION_NONE;
        }
    }

    /** The character a keysym types, or 0 when it is not a printable key. */
    static char charFor(int keysym) {
        if (keysym >= 0x20 && keysym <= 0x7E) {
            return (char) keysym;
        }
        // Latin-1 supplement sits at its own code points, minus the soft hyphen
        // and non-breaking space which are not worth typing.
        if (keysym >= 0xA0 && keysym <= 0xFF) {
            return (char) keysym;
        }
        if (keysym >= UNICODE_BASE && keysym <= UNICODE_BASE + 0x10FFFF) {
            int codePoint = keysym - UNICODE_BASE;
            if (codePoint >= 0x20 && codePoint != 0x7F && codePoint <= 0xFFFF) {
                return (char) codePoint;
            }
        }
        return 0;
    }

    /**
     * Shift, Control, Alt, Meta, Caps Lock and friends. The client already
     * sends the shifted keysym for a character, so these carry no information
     * here — and a client types several of them per sentence, which is worth
     * knowing before writing a log line for each.
     */
    static boolean isModifier(int keysym) {
        return (keysym >= 0xFFE1 && keysym <= 0xFFEE) || keysym == 0xFF7E || keysym == 0xFF20;
    }

    private static int sinceTiramisu(int action) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? action : GLOBAL_ACTION_NONE;
    }

    private static int sinceBaklava(int action) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ? action : GLOBAL_ACTION_NONE;
    }

    /** Human-readable name for the log, so a keysym that does nothing says so. */
    static String describe(int keysym) {
        char typed = charFor(keysym);
        if (typed != 0) {
            return "'" + typed + "'";
        }
        switch (keysym) {
            case KEYSYM_BACKSPACE: return "BackSpace";
            case KEYSYM_TAB: return "Tab";
            case KEYSYM_RETURN: return "Return";
            case KEYSYM_DELETE: return "Delete";
            case KEYSYM_ESCAPE: return "Escape";
            case KEYSYM_HOME: return "Home";
            case KEYSYM_END: return "End";
            case KEYSYM_LEFT: return "Left";
            case KEYSYM_UP: return "Up";
            case KEYSYM_RIGHT: return "Right";
            case KEYSYM_DOWN: return "Down";
            default: return "keysym 0x" + Integer.toHexString(keysym);
        }
    }
}
