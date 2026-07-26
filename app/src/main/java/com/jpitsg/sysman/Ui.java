package com.jpitsg.sysman;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared design system for the whole app: one canonical palette, spacing/shape
 * scale, and view factories so the main screen and every dialog look like one
 * coherent system. The token values are the reference the main screen has
 * always used; dialogs adopt them here instead of keeping slightly divergent
 * private copies.
 */
final class Ui {
    private Ui() {
    }

    // ---- Palette -----------------------------------------------------------
    static final int COLOR_BG = 0xFFF4F7F4;
    static final int COLOR_SURFACE = 0xFFFFFFFF;
    static final int COLOR_GROUPED = 0xFFFAFCFA;
    static final int COLOR_BORDER = 0xFFE2E8E2;
    static final int COLOR_PRIMARY = 0xFF1E6F4F;
    static final int COLOR_PRIMARY_CONTAINER = 0xFFDDEEE5;
    static final int COLOR_PRIMARY_ON_CONTAINER = 0xFF0D3E2C;
    static final int COLOR_NEUTRAL_CONTAINER = 0xFFE9EDEA;
    static final int COLOR_NEUTRAL_ON_CONTAINER = 0xFF15201C;
    static final int COLOR_DANGER = 0xFFB94436;
    static final int COLOR_DANGER_CONTAINER = 0xFFFCE5E0;
    static final int COLOR_DANGER_ON_CONTAINER = 0xFF7E2B23;
    static final int COLOR_FIELD_BG = 0xFFF2F5F2;
    static final int COLOR_FIELD_BORDER = 0xFFDDE4DE;
    static final int COLOR_TEXT = 0xFF131D1A;
    static final int COLOR_TEXT_DIM = 0xFF5D6E68;
    static final int COLOR_TEXT_FAINT = 0xFF8C9A95;
    static final int COLOR_LABEL = 0xFF6A7770;
    static final int COLOR_OK = 0xFF1E6F4F;
    static final int COLOR_BAD = 0xFFB94436;
    static final int COLOR_ON_ACCENT = 0xFFFFFFFF;
    static final int COLOR_RIPPLE_DARK = 0x33FFFFFF;
    static final int COLOR_RIPPLE_LIGHT = 0x22000000;

    // ---- Spacing & shape (dp) ----------------------------------------------
    static final int GAP = 12;
    static final int CARD_CORNER = 22;
    static final int FIELD_CORNER = 14;
    static final int BUTTON_CORNER = 14;
    static final int GROUP_CORNER = 16;
    static final int PILL_CORNER = 999;
    static final int FIELD_MIN_HEIGHT = 48;
    static final int BUTTON_MIN_HEIGHT = 48;

    // Dialog shell.
    static final int DIALOG_CORNER = 22;
    static final int DIALOG_PADDING = 18;
    static final float DIALOG_WIDTH_FRACTION = 0.94f;

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Rounded fill. cornerPx/strokePx are in pixels (use dp(context, ...) to convert). */
    static GradientDrawable roundedFill(int color, int cornerPx, int strokePx, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(cornerPx);
        if (strokePx > 0) {
            d.setStroke(strokePx, strokeColor);
        }
        return d;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Lets a pill's label scroll inside its bubble instead of being clipped
     * when the text is wider than the pill. Marquee only animates when the text
     * genuinely overflows, so short labels are left alone.
     */
    static void marqueeLabel(TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        view.setMarqueeRepeatLimit(-1);
        view.setHorizontallyScrolling(true);
        view.setHorizontalFadingEdgeEnabled(true);
        view.setFadingEdgeLength(dp(view.getContext(), 8));
        // Marquee only runs on a focused or selected view. Selecting it starts
        // the scroll without stealing focus from real inputs; the platform
        // kicks it off once the view has been laid out and the window is
        // focused.
        view.setSelected(true);
    }

    /**
     * Restarts a {@link #marqueeLabel} view's scroll after its text changed.
     * Selection has to actually transition for the platform to re-evaluate the
     * marquee, and the check needs the new text's layout, so the re-select is
     * posted. Call only on a real text change — re-arming every refresh would
     * yank a scrolling label back to the start.
     */
    static void rearmMarquee(final TextView view) {
        view.setSelected(false);
        view.post(new Runnable() {
            @Override
            public void run() {
                view.setSelected(true);
            }
        });
    }

    // ---- Buttons (identical to the main screen's style) --------------------

    static Button button(Context c, String text, int bg, int fg, int ripple, View.OnClickListener listener) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setMinHeight(dp(c, BUTTON_MIN_HEIGHT));
        b.setMinimumHeight(dp(c, BUTTON_MIN_HEIGHT));
        b.setIncludeFontPadding(false);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setPadding(dp(c, GAP), dp(c, GAP), dp(c, GAP), dp(c, GAP));
        b.setBackground(new RippleDrawable(
                ColorStateList.valueOf(ripple),
                roundedFill(bg, dp(c, BUTTON_CORNER), 0, 0),
                null));
        b.setOnClickListener(listener);
        return b;
    }

    static Button primaryButton(Context c, String text, View.OnClickListener l) {
        return button(c, text, COLOR_PRIMARY, COLOR_ON_ACCENT, COLOR_RIPPLE_DARK, l);
    }

    static Button tonalButton(Context c, String text, View.OnClickListener l) {
        return button(c, text, COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER, COLOR_RIPPLE_LIGHT, l);
    }

    static Button neutralButton(Context c, String text, View.OnClickListener l) {
        return button(c, text, COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER, COLOR_RIPPLE_LIGHT, l);
    }

    static Button dangerButton(Context c, String text, View.OnClickListener l) {
        return button(c, text, COLOR_DANGER, COLOR_ON_ACCENT, COLOR_RIPPLE_DARK, l);
    }

    /** Adds a button to a horizontal row as an equal-weight cell, matching the main screen. */
    static void addRowButton(LinearLayout row, Button button) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(row.getContext(), BUTTON_MIN_HEIGHT), 1f);
        if (row.getChildCount() > 0) {
            lp.setMargins(dp(row.getContext(), GAP), 0, 0, 0);
        }
        row.addView(button, lp);
    }

    // ---- Dialog building blocks --------------------------------------------

    /** The rounded, bordered card that is the root of every dialog. */
    static LinearLayout dialogCard(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(roundedFill(COLOR_SURFACE, dp(c, DIALOG_CORNER), dp(c, 1), COLOR_BORDER));
        root.setPadding(dp(c, DIALOG_PADDING), dp(c, GAP + 4), dp(c, DIALOG_PADDING), dp(c, GAP + 4));
        return root;
    }

    static TextView dialogTitle(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(17);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setTextColor(COLOR_TEXT);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView dialogSubtitle(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(COLOR_TEXT_DIM);
        t.setLineSpacing(0, 1.2f);
        t.setIncludeFontPadding(false);
        return t;
    }

    static void sizeDialogWindow(Dialog dialog, float widthFraction, float heightFraction) {
        if (dialog.getWindow() == null) {
            return;
        }
        DisplayMetrics m = dialog.getContext().getResources().getDisplayMetrics();
        int width = (int) (m.widthPixels * widthFraction);
        int height = heightFraction > 0f
                ? (int) (m.heightPixels * heightFraction)
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setLayout(width, height);
    }
}
