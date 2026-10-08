package com.jpitsg.sysman;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A dial of equal arcs, one per feature, coloured by whether that feature is
 * running, needs attention or is off, with a count in the middle. The arcs
 * sweep in one after another the first time the dial is shown, and cross-fade
 * whenever a state changes afterwards.
 */
final class StatusRingView extends View {
    static final int STATE_OFF = 0;
    static final int STATE_ON = 1;
    static final int STATE_ATTENTION = 2;

    private static final float GAP_DEGREES = 7f;
    private static final int SWEEP_MS = 1100;
    private static final int FADE_MS = 380;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bigPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final ArgbEvaluator evaluator = new ArgbEvaluator();

    private int[] states = new int[0];
    private int[] fromColors = new int[0];
    private int[] toColors = new int[0];
    private float[] fromWidths = new float[0];
    private float[] toWidths = new float[0];
    private float fade = 1f;
    private float reveal = 0f;
    private boolean revealed;
    private ValueAnimator revealAnimator;
    private ValueAnimator fadeAnimator;

    private int colorOn = 0xFFFFFFFF;
    private int colorAttention = 0xFFFFC857;
    private int colorOff = 0x40FFFFFF;
    private int colorText = 0xFFFFFFFF;
    private int colorSubtext = 0xB3FFFFFF;
    private String big = "";
    private String small = "";
    private final float stroke;

    StatusRingView(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        stroke = 9f * density;
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        bigPaint.setTextAlign(Paint.Align.CENTER);
        bigPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        bigPaint.setTextSize(34f * density);
        smallPaint.setTextAlign(Paint.Align.CENTER);
        smallPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        smallPaint.setTextSize(9.5f * density);
        smallPaint.setLetterSpacing(0.14f);
    }

    void setColors(int on, int attention, int off, int text, int subtext) {
        colorOn = on;
        colorAttention = attention;
        colorOff = off;
        colorText = text;
        colorSubtext = subtext;
        invalidate();
    }

    void setCenter(String bigText, String smallText) {
        big = bigText == null ? "" : bigText;
        small = smallText == null ? "" : smallText.toUpperCase(java.util.Locale.US);
        invalidate();
    }

    /** Sets the per-feature states; cross-fades changed arcs when the dial is already showing. */
    void setStates(int[] newStates) {
        boolean sameShape = newStates.length == states.length;
        float[] widths = new float[newStates.length];
        int[] colors = new int[newStates.length];
        for (int i = 0; i < newStates.length; i++) {
            colors[i] = colorFor(newStates[i]);
            widths[i] = widthFor(newStates[i]);
        }
        if (!sameShape || !revealed) {
            states = newStates.clone();
            fromColors = colors;
            toColors = colors.clone();
            fromWidths = widths;
            toWidths = widths.clone();
            fade = 1f;
            invalidate();
            return;
        }
        // Start the cross-fade from whatever is on screen right now.
        int[] shown = new int[states.length];
        float[] shownWidths = new float[states.length];
        for (int i = 0; i < states.length; i++) {
            shown[i] = displayColor(i);
            shownWidths[i] = displayWidth(i);
        }
        boolean changed = false;
        for (int i = 0; i < states.length; i++) {
            if (states[i] != newStates[i]) {
                changed = true;
                break;
            }
        }
        states = newStates.clone();
        fromColors = shown;
        fromWidths = shownWidths;
        toColors = colors;
        toWidths = widths;
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
        }
        if (!changed) {
            fade = 1f;
            invalidate();
            return;
        }
        fade = 0f;
        fadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        fadeAnimator.setDuration(FADE_MS);
        fadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                fade = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        fadeAnimator.start();
    }

    /** Plays the sweep-in once; later calls only matter if the dial was never revealed. */
    void reveal() {
        if (revealed || getVisibility() != VISIBLE) {
            return;
        }
        revealed = true;
        reveal = 0f;
        revealAnimator = ValueAnimator.ofFloat(0f, 1f);
        revealAnimator.setDuration(SWEEP_MS);
        revealAnimator.setInterpolator(new DecelerateInterpolator(1.4f));
        revealAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                reveal = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        revealAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        reveal();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (revealAnimator != null) {
            revealAnimator.cancel();
        }
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
        }
        reveal = 1f;
        fade = 1f;
        super.onDetachedFromWindow();
    }

    private int colorFor(int state) {
        return state == STATE_ON ? colorOn : state == STATE_ATTENTION ? colorAttention : colorOff;
    }

    private float widthFor(int state) {
        return state == STATE_ON ? stroke : state == STATE_ATTENTION ? stroke * 1.12f : stroke * 0.5f;
    }

    private int displayColor(int i) {
        return (Integer) evaluator.evaluate(fade, fromColors[i], toColors[i]);
    }

    private float displayWidth(int i) {
        return fromWidths[i] + (toWidths[i] - fromWidths[i]) * fade;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int wanted = Math.round(116f * getResources().getDisplayMetrics().density);
        int w = resolveSize(wanted, widthMeasureSpec);
        int h = resolveSize(wanted, heightMeasureSpec);
        int side = Math.min(w, h);
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = states.length;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outer = Math.min(getWidth(), getHeight()) / 2f;
        float radius = outer - stroke * 0.7f;
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        if (n > 0) {
            float segment = 360f / n;
            float t = reveal * (n + 4f);
            for (int i = 0; i < n; i++) {
                float local = Math.max(0f, Math.min(1f, (t - i) / 4f));
                if (local <= 0f) {
                    continue;
                }
                float width = displayWidth(i);
                float capDegrees = (float) Math.toDegrees((width / 2f) / radius);
                float span = Math.max(0.5f, segment - GAP_DEGREES - 2f * capDegrees);
                float start = -90f + i * segment + (segment - span) / 2f;
                arcPaint.setStrokeWidth(width);
                arcPaint.setColor(displayColor(i));
                // Ease the growth so each arc settles rather than snaps.
                float eased = 1f - (1f - local) * (1f - local);
                canvas.drawArc(oval, start, span * eased, false, arcPaint);
            }
        }

        bigPaint.setColor(colorText);
        smallPaint.setColor(colorSubtext);
        float bigHeight = bigPaint.descent() - bigPaint.ascent();
        float smallHeight = smallPaint.descent() - smallPaint.ascent();
        float gap = 2f * getResources().getDisplayMetrics().density;
        float block = bigHeight * 0.78f + (small.isEmpty() ? 0f : gap + smallHeight * 0.8f);
        float top = cy - block / 2f;
        canvas.drawText(big, cx, top + bigHeight * 0.78f - bigPaint.descent() * 0.55f, bigPaint);
        if (!small.isEmpty()) {
            canvas.drawText(small, cx, top + block - smallPaint.descent() * 0.4f, smallPaint);
        }
    }
}
