package com.jpitsg.sysman;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Shared design system for the whole app: one canonical palette in a light and
 * a dark variant, spacing/shape scale, and view factories so the main screen
 * and every dialog look like one coherent system.
 *
 * <p>The palette fields are deliberately mutable statics rather than
 * constants: {@link #applyTheme} points them at the light or dark values once,
 * on the main thread, before any view is built, and every consumer (the
 * activity, its custom views and the dialogs) reads them at build time. A
 * theme change recreates the activity, which is what rebuilds the views.
 */
final class Ui {
    private Ui() {
    }

    // ---- Theme mode --------------------------------------------------------
    static final int MODE_SYSTEM = 0;
    static final int MODE_LIGHT = 1;
    static final int MODE_DARK = 2;

    /** How a piece of status is shown: neutral = off or idle, ok = running, bad = needs attention. */
    static final int TONE_NEUTRAL = 0;
    static final int TONE_OK = 1;
    static final int TONE_BAD = 2;

    private static final String PREFS = "system_manager_ui";
    private static final String KEY_THEME_MODE = "theme_mode";

    /** True while the dark palette is applied. */
    static boolean dark;

    // ---- Palette (assigned by applyTheme) ----------------------------------
    static int COLOR_BG;
    static int COLOR_BG_GLOW;
    static int COLOR_SURFACE;
    static int COLOR_GROUPED;
    static int COLOR_BORDER;
    static int COLOR_PRIMARY;
    static int COLOR_PRIMARY_CONTAINER;
    static int COLOR_PRIMARY_ON_CONTAINER;
    static int COLOR_NEUTRAL_CONTAINER;
    static int COLOR_NEUTRAL_ON_CONTAINER;
    static int COLOR_DANGER;
    static int COLOR_DANGER_CONTAINER;
    static int COLOR_DANGER_ON_CONTAINER;
    static int COLOR_FIELD_BG;
    static int COLOR_FIELD_BORDER;
    static int COLOR_TEXT;
    static int COLOR_TEXT_DIM;
    static int COLOR_TEXT_FAINT;
    static int COLOR_LABEL;
    static int COLOR_OK;
    static int COLOR_BAD;
    /** Foreground for text and icons on a {@link #COLOR_PRIMARY} fill. */
    static int COLOR_ON_ACCENT;
    /** Foreground for text and icons on a {@link #COLOR_DANGER} fill. */
    static int COLOR_ON_DANGER;
    static int COLOR_RIPPLE_LIGHT;
    static int COLOR_SWITCH_OFF_TRACK;
    static int COLOR_SWITCH_OFF_STROKE;
    static int COLOR_HERO_START;
    static int COLOR_HERO_END;

    // ---- Accent families (feature tiles) -----------------------------------
    static final int ACCENT_EMERALD = 0;
    static final int ACCENT_CYAN = 1;
    static final int ACCENT_BLUE = 2;
    static final int ACCENT_VIOLET = 3;
    static final int ACCENT_AMBER = 4;
    static final int ACCENT_ROSE = 5;
    static final int ACCENT_ORANGE = 6;
    static final int ACCENT_SLATE = 7;
    static final int ACCENT_TEAL = 8;

    private static final int[] ACCENT_FG_LIGHT = {
            0xFF0A7D5A, 0xFF0A7894, 0xFF2563EB, 0xFF7C3AED, 0xFFA84C06,
            0xFFC8305A, 0xFFBE3F0A, 0xFF475569, 0xFF0F766E};
    private static final int[] ACCENT_BG_LIGHT = {
            0xFFD5EFE4, 0xFFD3EDF4, 0xFFDCE7FE, 0xFFEADFFD, 0xFFFDEBC8,
            0xFFFCDDE5, 0xFFFDE0D0, 0xFFE3E8EE, 0xFFD2EFEC};
    private static final int[] ACCENT_FG_DARK = {
            0xFF4BE0AA, 0xFF52D3F0, 0xFF7AA7FF, 0xFFB596FF, 0xFFFFC25A,
            0xFFFF8CA8, 0xFFFF9A62, 0xFFAAB7C6, 0xFF4FD8CB};
    private static final int[] ACCENT_BG_DARK = {
            0xFF11382C, 0xFF0F3340, 0xFF17284D, 0xFF2A1F52, 0xFF3D2D0C,
            0xFF4A1B29, 0xFF432212, 0xFF212B36, 0xFF0E3733};

    static int accentFg(int family) {
        return (dark ? ACCENT_FG_DARK : ACCENT_FG_LIGHT)[family];
    }

    static int accentContainer(int family) {
        return (dark ? ACCENT_BG_DARK : ACCENT_BG_LIGHT)[family];
    }

    // ---- Spacing & shape (dp) ----------------------------------------------
    static final int GAP = 12;
    static final int PAGE_PADDING = 16;
    static final int CARD_CORNER = 24;
    static final int SECTION_CORNER = 20;
    static final int FIELD_CORNER = 14;
    static final int BUTTON_CORNER = 16;
    static final int GROUP_CORNER = 16;
    static final int PILL_CORNER = 999;
    static final int FIELD_MIN_HEIGHT = 50;
    static final int BUTTON_MIN_HEIGHT = 50;
    /** Widest the content column grows on tablets and in landscape. */
    static final int CONTENT_MAX_WIDTH = 640;

    // Dialog shell.
    static final int DIALOG_CORNER = 28;
    static final int DIALOG_PADDING = 22;
    static final float DIALOG_WIDTH_FRACTION = 0.92f;

    // ---- Theme application -------------------------------------------------

    static int themeMode(Context context) {
        return prefs(context).getInt(KEY_THEME_MODE, MODE_SYSTEM);
    }

    static void setThemeMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean systemIsDark(Context context) {
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Resolves the saved mode against the system setting and points the palette at the result. */
    static void applyTheme(Context context) {
        int mode = themeMode(context);
        boolean night = mode == MODE_DARK || (mode == MODE_SYSTEM && systemIsDark(context));
        applyPalette(night);
    }

    static void applyPalette(boolean night) {
        dark = night;
        COLOR_ON_ACCENT = night ? 0xFF00281B : 0xFFFFFFFF;
        COLOR_ON_DANGER = night ? 0xFF2D0A06 : 0xFFFFFFFF;
        COLOR_RIPPLE_LIGHT = night ? 0x26FFFFFF : 0x1F000000;
        if (night) {
            COLOR_BG = 0xFF080D0C;
            COLOR_BG_GLOW = 0xFF0F2D24;
            COLOR_SURFACE = 0xFF111917;
            COLOR_GROUPED = 0xFF0E1514;
            COLOR_BORDER = 0xFF1F2C28;
            COLOR_PRIMARY = 0xFF3FD6A0;
            COLOR_PRIMARY_CONTAINER = 0xFF123B2E;
            COLOR_PRIMARY_ON_CONTAINER = 0xFFBDF2DE;
            COLOR_NEUTRAL_CONTAINER = 0xFF1B2623;
            COLOR_NEUTRAL_ON_CONTAINER = 0xFFD2E0DB;
            COLOR_DANGER = 0xFFFF7A6B;
            COLOR_DANGER_CONTAINER = 0xFF44201C;
            COLOR_DANGER_ON_CONTAINER = 0xFFFFC9C1;
            COLOR_FIELD_BG = 0xFF0B1110;
            COLOR_FIELD_BORDER = 0xFF2A3A34;
            COLOR_TEXT = 0xFFE6F0ED;
            COLOR_TEXT_DIM = 0xFF9AB0A9;
            COLOR_TEXT_FAINT = 0xFF7A9088;
            COLOR_LABEL = 0xFF8FA59E;
            COLOR_SWITCH_OFF_TRACK = 0xFF0B1110;
            COLOR_SWITCH_OFF_STROKE = 0xFF5F746C;
            COLOR_HERO_START = 0xFF0D4E3E;
            COLOR_HERO_END = 0xFF0B3040;
        } else {
            COLOR_BG = 0xFFEEF3F1;
            COLOR_BG_GLOW = 0xFFD3EFE4;
            COLOR_SURFACE = 0xFFFFFFFF;
            COLOR_GROUPED = 0xFFF6F9F8;
            COLOR_BORDER = 0xFFDCE5E1;
            COLOR_PRIMARY = 0xFF0A7D5A;
            COLOR_PRIMARY_CONTAINER = 0xFFD5EFE4;
            COLOR_PRIMARY_ON_CONTAINER = 0xFF064A36;
            COLOR_NEUTRAL_CONTAINER = 0xFFE7EDEA;
            COLOR_NEUTRAL_ON_CONTAINER = 0xFF24342F;
            COLOR_DANGER = 0xFFBE3A2B;
            COLOR_DANGER_CONTAINER = 0xFFFBE3DF;
            COLOR_DANGER_ON_CONTAINER = 0xFF7A2118;
            COLOR_FIELD_BG = 0xFFF2F6F4;
            COLOR_FIELD_BORDER = 0xFFD3DDD8;
            COLOR_TEXT = 0xFF0F1C18;
            COLOR_TEXT_DIM = 0xFF4B5E57;
            COLOR_TEXT_FAINT = 0xFF62746C;
            COLOR_LABEL = 0xFF53665E;
            COLOR_SWITCH_OFF_TRACK = 0xFFF2F6F4;
            COLOR_SWITCH_OFF_STROKE = 0xFF8A9B94;
            COLOR_HERO_START = 0xFF0A8060;
            COLOR_HERO_END = 0xFF0A6477;
        }
        COLOR_OK = COLOR_PRIMARY;
        COLOR_BAD = COLOR_DANGER;
    }

    /** Which tone a status container colour stands for (pill and tile state travel as colours). */
    static int toneOfContainer(int container) {
        if (container == COLOR_PRIMARY_CONTAINER) {
            return TONE_OK;
        }
        if (container == COLOR_DANGER_CONTAINER) {
            return TONE_BAD;
        }
        return TONE_NEUTRAL;
    }

    static int toneFg(int tone) {
        return tone == TONE_OK ? COLOR_PRIMARY_ON_CONTAINER
                : tone == TONE_BAD ? COLOR_DANGER_ON_CONTAINER : COLOR_NEUTRAL_ON_CONTAINER;
    }

    static int toneBg(int tone) {
        return tone == TONE_OK ? COLOR_PRIMARY_CONTAINER
                : tone == TONE_BAD ? COLOR_DANGER_CONTAINER : COLOR_NEUTRAL_CONTAINER;
    }

    /** The saturated colour of a tone, for dots, icons and bars rather than text on a container. */
    static int toneAccent(int tone) {
        return tone == TONE_OK ? COLOR_OK : tone == TONE_BAD ? COLOR_BAD : COLOR_TEXT_FAINT;
    }

    // ---- Measurement & colour helpers --------------------------------------

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** {@code color} with its alpha scaled by {@code factor}. */
    static int alpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * factor);
        return Color.argb(Math.max(0, Math.min(255, a)), Color.red(color), Color.green(color), Color.blue(color));
    }

    /** {@code fg} laid over {@code bg} at {@code amount} opacity. */
    static int blend(int fg, int bg, float amount) {
        float inverse = 1f - amount;
        return Color.argb(255,
                Math.round(Color.red(fg) * amount + Color.red(bg) * inverse),
                Math.round(Color.green(fg) * amount + Color.green(bg) * inverse),
                Math.round(Color.blue(fg) * amount + Color.blue(bg) * inverse));
    }

    /** Capitalises only the first letter: "LINK OFFLINE" becomes "Link offline". */
    static String sentence(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String lower = text.toLowerCase(java.util.Locale.US);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
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

    static GradientDrawable gradient(GradientDrawable.Orientation orientation, int start, int end, int cornerPx) {
        GradientDrawable d = new GradientDrawable(orientation, new int[]{start, end});
        d.setCornerRadius(cornerPx);
        return d;
    }

    /** A ripple clipped to the same rounded shape as its content. */
    static RippleDrawable ripple(int rippleColor, Drawable content, int cornerPx) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content,
                roundedFill(0xFF000000, cornerPx, 0, 0));
    }

    /** Soft shadow in the light theme; the dark theme separates surfaces with tone and border instead. */
    static void elevate(View view, float dp) {
        view.setElevation(dark ? 0f : dp * view.getResources().getDisplayMetrics().density);
    }

    /** The window background: the page colour with a soft pool of the brand colour in the top-left corner. */
    static Drawable backdrop(Context c) {
        GradientDrawable base = new GradientDrawable();
        base.setColor(COLOR_BG);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(dp(c, 540));
        glow.setGradientCenter(0.05f, 0f);
        glow.setColors(new int[]{alpha(COLOR_BG_GLOW, dark ? 0.9f : 0.9f), alpha(COLOR_BG_GLOW, 0f)});
        return new LayerDrawable(new Drawable[]{base, glow});
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ---- Text --------------------------------------------------------------

    static final Typeface FONT_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface FONT_BOLD = Typeface.create("sans-serif-medium", Typeface.BOLD);

    static TextView text(Context c, CharSequence text, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(medium ? FONT_MEDIUM : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    /** Small spaced capitals used for section and group headings. */
    static TextView caps(Context c, String title) {
        TextView t = new TextView(c);
        t.setText(title.toUpperCase(java.util.Locale.US));
        t.setTextSize(11);
        t.setTextColor(COLOR_TEXT_FAINT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.14f);
        t.setIncludeFontPadding(false);
        return t;
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

    // ---- Icons -------------------------------------------------------------

    /** A tinted vector icon. Every icon in the set is white line art, so the colour filter does the colouring. */
    static ImageView icon(Context c, int resId, int sizeDp, int color) {
        ImageView v = new ImageView(c);
        v.setImageResource(resId);
        v.setColorFilter(color);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setLayoutParams(new ViewGroup.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    static Drawable tintedIcon(Context c, int resId, int sizeDp, int color) {
        Drawable d = c.getDrawable(resId).mutate();
        d.setTint(color);
        int px = dp(c, sizeDp);
        d.setBounds(0, 0, px, px);
        return d;
    }

    /** A rounded square holding an icon, the visual anchor of tiles and page headings. */
    static LinearLayout iconChip(Context c, int resId, int sizeDp, int iconDp, int bg, int fg) {
        LinearLayout chip = new LinearLayout(c);
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(roundedFill(bg, dp(c, Math.round(sizeDp * 0.34f)), 0, 0));
        ImageView icon = icon(c, resId, iconDp, fg);
        chip.addView(icon, new LinearLayout.LayoutParams(dp(c, iconDp), dp(c, iconDp)));
        chip.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return chip;
    }

    // ---- Buttons -----------------------------------------------------------

    static Button button(Context c, String text, int bg, int fg, int ripple, View.OnClickListener listener) {
        return iconButton(c, text, 0, bg, fg, ripple, listener);
    }

    /** A button with an optional leading icon (0 for none), laid out as one centred group. */
    static IconButton iconButton(Context c, String text, int iconRes, int bg, int fg, int ripple,
                                 View.OnClickListener listener) {
        IconButton b = new IconButton(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(FONT_MEDIUM);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setMinHeight(dp(c, BUTTON_MIN_HEIGHT));
        b.setMinimumHeight(dp(c, BUTTON_MIN_HEIGHT));
        b.setIncludeFontPadding(false);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setPadding(dp(c, GAP + 2), dp(c, GAP), dp(c, GAP + 2), dp(c, GAP));
        b.setIcon(iconRes, 18, 8);
        b.setTextColor(fg);
        b.setBackground(ripple(ripple, roundedFill(bg, dp(c, BUTTON_CORNER), 0, 0), dp(c, BUTTON_CORNER)));
        b.setOnClickListener(listener);
        return b;
    }

    static Button primaryButton(Context c, String text, View.OnClickListener l) {
        return button(c, text, COLOR_PRIMARY, COLOR_ON_ACCENT, alpha(COLOR_ON_ACCENT, 0.2f), l);
    }

    static Button tonalButton(Context c, String text, View.OnClickListener l) {
        return button(c, text, COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER, COLOR_RIPPLE_LIGHT, l);
    }

    static Button neutralButton(Context c, String text, View.OnClickListener l) {
        return button(c, text, COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER, COLOR_RIPPLE_LIGHT, l);
    }

    static Button dangerButton(Context c, String text, View.OnClickListener l) {
        return button(c, text, COLOR_DANGER, COLOR_ON_DANGER, alpha(COLOR_ON_DANGER, 0.2f), l);
    }

    /** Adds a button to a horizontal row as an equal-weight cell, matching the main screen. */
    static void addRowButton(LinearLayout row, Button button) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(row.getContext(), BUTTON_MIN_HEIGHT), 1f);
        if (row.getChildCount() > 0) {
            lp.setMargins(dp(row.getContext(), GAP), 0, 0, 0);
        }
        row.addView(button, lp);
    }

    // ---- Form controls -----------------------------------------------------

    /** Filled field with a hairline border that becomes an accent ring while focused. */
    static Drawable fieldBackground(Context c, boolean error) {
        int corner = dp(c, FIELD_CORNER);
        StateListDrawable states = new StateListDrawable();
        if (error) {
            states.addState(new int[]{}, roundedFill(COLOR_FIELD_BG, corner, dp(c, 2), COLOR_DANGER));
            return states;
        }
        states.addState(new int[]{android.R.attr.state_focused},
                roundedFill(COLOR_FIELD_BG, corner, dp(c, 2), COLOR_PRIMARY));
        states.addState(new int[]{},
                roundedFill(COLOR_FIELD_BG, corner, dp(c, 1), COLOR_FIELD_BORDER));
        return states;
    }

    /**
     * Pill track with a thumb that grows when on. The thumb box equals the
     * track height so the thumb sits level with both ends of its travel.
     */
    static void styleSwitch(Switch sw) {
        Context c = sw.getContext();
        int trackW = dp(c, 52);
        int trackH = dp(c, 30);
        int box = trackH;

        GradientDrawable trackOn = roundedFill(COLOR_PRIMARY, trackH / 2, 0, 0);
        trackOn.setSize(trackW, trackH);
        GradientDrawable trackOff = roundedFill(COLOR_SWITCH_OFF_TRACK, trackH / 2, dp(c, 2), COLOR_SWITCH_OFF_STROKE);
        trackOff.setSize(trackW, trackH);
        StateListDrawable track = new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked}, trackOn);
        track.addState(new int[]{}, trackOff);
        track.setEnterFadeDuration(140);
        track.setExitFadeDuration(140);

        StateListDrawable thumb = new StateListDrawable();
        thumb.addState(new int[]{android.R.attr.state_checked}, thumbCircle(c, COLOR_ON_ACCENT, 22, box));
        thumb.addState(new int[]{}, thumbCircle(c, COLOR_SWITCH_OFF_STROKE, 14, box));
        thumb.setEnterFadeDuration(140);
        thumb.setExitFadeDuration(140);

        sw.setTrackDrawable(track);
        sw.setThumbDrawable(thumb);
        sw.setSwitchMinWidth(trackW);
        sw.setThumbTextPadding(0);
        sw.setShowText(false);
        // A split track would cut the thumb's whole box out of the track, hiding a light thumb.
        sw.setSplitTrack(false);
        sw.setBackground(null);
    }

    private static Drawable thumbCircle(Context c, int color, int diameterDp, int boxPx) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        int d = dp(c, diameterDp);
        circle.setSize(d, d);
        int inset = Math.max(0, (boxPx - d) / 2);
        return new InsetDrawable(circle, inset);
    }

    static void styleSeekBar(SeekBar bar) {
        Context c = bar.getContext();
        int h = dp(c, 6);
        GradientDrawable track = roundedFill(COLOR_NEUTRAL_CONTAINER, h / 2, 0, 0);
        track.setSize(-1, h);
        GradientDrawable fill = roundedFill(COLOR_PRIMARY, h / 2, 0, 0);
        fill.setSize(-1, h);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{
                track, new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        bar.setProgressDrawable(layers);

        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(COLOR_PRIMARY);
        thumb.setStroke(dp(c, 3), COLOR_SURFACE);
        int d = dp(c, 26);
        thumb.setSize(d, d);
        bar.setThumb(thumb);
        bar.setSplitTrack(false);
        // The thumb is centred on the ends of the track, so the bar pads by its radius.
        bar.setPadding(d / 2, 0, d / 2, 0);
        bar.setThumbOffset(d / 2);
        bar.setBackground(null);
    }

    // ---- Dialog building blocks --------------------------------------------

    /** The rounded, bordered card that is the root of every dialog. */
    static LinearLayout dialogCard(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(roundedFill(COLOR_SURFACE, dp(c, DIALOG_CORNER), dp(c, 1), COLOR_BORDER));
        root.setPadding(dp(c, DIALOG_PADDING), dp(c, DIALOG_PADDING), dp(c, DIALOG_PADDING), dp(c, GAP + 6));
        return root;
    }

    static TextView dialogTitle(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(19);
        t.setTypeface(FONT_BOLD);
        t.setTextColor(COLOR_TEXT);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView dialogSubtitle(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(COLOR_TEXT_DIM);
        t.setLineSpacing(0, 1.2f);
        t.setIncludeFontPadding(false);
        return t;
    }

    static void sizeDialogWindow(Dialog dialog, float widthFraction, float heightFraction) {
        if (dialog.getWindow() == null) {
            return;
        }
        // The card draws its own rounded surface, so the window behind it must be clear.
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        DisplayMetrics m = dialog.getContext().getResources().getDisplayMetrics();
        int width = Math.min((int) (m.widthPixels * widthFraction),
                Math.round(CONTENT_MAX_WIDTH * m.density));
        int height = heightFraction > 0f
                ? (int) (m.heightPixels * heightFraction)
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setLayout(width, height);
    }
}
