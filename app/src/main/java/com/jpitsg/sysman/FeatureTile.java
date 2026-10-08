package com.jpitsg.sysman;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One feature on the overview: an icon chip in the feature's own hue, a state
 * dot, the feature name and a live line of status. Colour carries the state —
 * a running tile is lit, an attention tile is red, an idle tile is grey — so
 * the grid reads at a glance.
 */
final class FeatureTile extends LinearLayout {
    private final int accent;
    private final int iconRes;
    private final String title;
    private LinearLayout chip;
    private ImageView chipIcon;
    private View dot;
    private TextView titleView;
    private TextView statusView;
    private LinearLayout top;
    private LinearLayout texts;
    private boolean wide;
    private String status = "";
    private String detail = "";
    private int tone = Ui.TONE_NEUTRAL;

    FeatureTile(Context context, String title, int iconRes, int accentFamily) {
        super(context);
        this.title = title;
        this.iconRes = iconRes;
        this.accent = accentFamily;
        setOrientation(VERTICAL);
        setClickable(true);
        setFocusable(true);
        int pad = Ui.dp(context, 14);
        setPadding(pad, pad, pad, Ui.dp(context, 15));

        top = new LinearLayout(context);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.TOP);

        chip = Ui.iconChip(context, iconRes, 42, 22, Ui.COLOR_NEUTRAL_CONTAINER, Ui.COLOR_TEXT_FAINT);
        chipIcon = (ImageView) chip.getChildAt(0);

        dot = new View(context);

        texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);

        titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTypeface(Ui.FONT_MEDIUM);
        titleView.setTextColor(Ui.COLOR_TEXT);
        titleView.setIncludeFontPadding(false);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        statusView = new TextView(context);
        statusView.setTextSize(12.5f);
        statusView.setIncludeFontPadding(false);
        statusView.setLineSpacing(0, 1.15f);
        statusView.setMaxLines(2);
        statusView.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams statusLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = Ui.dp(context, 4);
        texts.addView(statusView, statusLp);

        arrange();
        applyStyle();
    }

    /**
     * A lone tile in the last row spans it, and lays its parts out side by side so it does not
     * look like a tall tile that was stretched.
     */
    void setWide(boolean wide) {
        if (this.wide == wide) {
            return;
        }
        this.wide = wide;
        arrange();
    }

    private void arrange() {
        Context context = getContext();
        removeAllViews();
        top.removeAllViews();
        int pad = Ui.dp(context, 14);
        if (wide) {
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(pad, pad, pad + Ui.dp(context, 2), pad);
            addView(chip);
            LayoutParams textsLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            textsLp.leftMargin = Ui.dp(context, 14);
            addView(texts, textsLp);
            addView(dot, new LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18)));
        } else {
            setOrientation(VERTICAL);
            setGravity(Gravity.NO_GRAVITY);
            setPadding(pad, pad, pad, Ui.dp(context, 15));
            addView(top, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            top.addView(chip);
            top.addView(new View(context), new LayoutParams(0, 1, 1f));
            LayoutParams dotLp = new LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18));
            dotLp.topMargin = Ui.dp(context, 1);
            top.addView(dot, dotLp);
            LayoutParams textsLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            textsLp.topMargin = Ui.dp(context, 14);
            addView(texts, textsLp);
        }
    }

    int tone() {
        return tone;
    }

    String title() {
        return title;
    }

    void setStatus(String newStatus, int newTone) {
        String clean = newStatus == null ? "" : newStatus;
        if (clean.equals(status) && newTone == tone) {
            return;
        }
        status = clean;
        tone = newTone;
        applyStyle();
    }

    void setDetail(String newDetail) {
        String clean = newDetail == null ? "" : newDetail;
        if (clean.equals(detail)) {
            return;
        }
        detail = clean;
        applyStyle();
    }

    private void applyStyle() {
        Context c = getContext();
        int corner = Ui.dp(c, Ui.CARD_CORNER);
        int surface = Ui.COLOR_SURFACE;
        int accentFg = Ui.accentFg(accent);
        int accentBg = Ui.accentContainer(accent);

        Drawable base;
        int chipBg;
        int chipFg;
        if (tone == Ui.TONE_OK) {
            int wash = Ui.blend(accentBg, surface, Ui.dark ? 0.55f : 0.62f);
            GradientDrawable g = Ui.gradient(GradientDrawable.Orientation.TL_BR, wash, surface, corner);
            g.setStroke(Ui.dp(c, 1), Ui.blend(accentFg, surface, Ui.dark ? 0.32f : 0.28f));
            base = g;
            chipBg = accentBg;
            chipFg = accentFg;
        } else if (tone == Ui.TONE_BAD) {
            int wash = Ui.blend(Ui.COLOR_DANGER_CONTAINER, surface, Ui.dark ? 0.7f : 0.75f);
            GradientDrawable g = Ui.gradient(GradientDrawable.Orientation.TL_BR, wash, surface, corner);
            g.setStroke(Ui.dp(c, 1), Ui.blend(Ui.COLOR_DANGER, surface, 0.45f));
            base = g;
            chipBg = Ui.COLOR_DANGER_CONTAINER;
            chipFg = Ui.COLOR_DANGER;
        } else {
            base = Ui.roundedFill(surface, corner, Ui.dp(c, 1), Ui.COLOR_BORDER);
            chipBg = Ui.COLOR_NEUTRAL_CONTAINER;
            chipFg = Ui.COLOR_TEXT_FAINT;
        }
        setBackground(Ui.ripple(Ui.alpha(accentFg, 0.14f), base, corner));
        Ui.elevate(this, tone == Ui.TONE_NEUTRAL ? 1f : 2f);

        chip.setBackground(Ui.roundedFill(chipBg, Ui.dp(c, 14), 0, 0));
        chipIcon.setColorFilter(chipFg);
        dot.setBackground(dotDrawable());

        titleView.setTextColor(Ui.COLOR_TEXT);
        SpannableStringBuilder line = new SpannableStringBuilder();
        int statusColor = tone == Ui.TONE_OK ? Ui.COLOR_OK
                : tone == Ui.TONE_BAD ? Ui.COLOR_BAD : Ui.COLOR_TEXT_DIM;
        if (!status.isEmpty()) {
            line.append(status);
            line.setSpan(new ForegroundColorSpan(statusColor), 0, line.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, line.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (!detail.isEmpty()) {
            int start = line.length();
            if (start > 0) {
                line.append(" · ");
                start = line.length();
            }
            line.append(detail);
            line.setSpan(new ForegroundColorSpan(Ui.COLOR_TEXT_DIM), start - (start > 0 ? 3 : 0), line.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        statusView.setText(line);
        setContentDescription(title + ". " + status + (detail.isEmpty() ? "" : ", " + detail));
    }

    private Drawable dotDrawable() {
        Context c = getContext();
        int color = Ui.toneAccent(tone);
        if (tone == Ui.TONE_NEUTRAL) {
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(0);
            ring.setStroke(Ui.dp(c, 2), Ui.alpha(color, 0.7f));
            LayerDrawable layers = new LayerDrawable(new Drawable[]{ring});
            int inset = Ui.dp(c, 4);
            layers.setLayerInset(0, inset, inset, inset, inset);
            return layers;
        }
        GradientDrawable halo = new GradientDrawable();
        halo.setShape(GradientDrawable.OVAL);
        halo.setColor(Ui.alpha(color, 0.22f));
        GradientDrawable core = new GradientDrawable();
        core.setShape(GradientDrawable.OVAL);
        core.setColor(color);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{halo, core});
        int inset = Ui.dp(c, 4);
        layers.setLayerInset(1, inset, inset, inset, inset);
        return layers;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animate().cancel();
                animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animate().cancel();
                animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(new OvershootInterpolator(2.2f)).start();
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }
}
