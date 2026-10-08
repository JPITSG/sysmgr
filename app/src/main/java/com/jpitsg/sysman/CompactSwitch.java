package com.jpitsg.sysman;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.Checkable;
import android.widget.Switch;

/**
 * A switch about the size of the state dot it stands in for on an overview tile. The visible track
 * is small, so the view carries an invisible touch area around it, and the track and the thumb's
 * travel are the same size whether the switch is on or off; only the thumb grows a little.
 */
final class CompactSwitch extends View implements Checkable {
    interface Listener {
        void onCheckedChanged(boolean checked);
    }

    /** The visible track, in dp. */
    static final int TRACK_W = 28;
    static final int TRACK_H = 16;
    /** Invisible touch area added around the track on each side, in dp. */
    static final int TOUCH_X = 10;
    static final int TOUCH_Y = 10;

    private static final int THUMB_OFF = 9;
    private static final int THUMB_ON = 12;
    private static final float OUTLINE = 1.5f;
    private static final float HALO = 11f;
    private static final int SLIDE_MS = 160;
    private static final float DIMMED = 0.45f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private Listener listener;
    private boolean checked;
    private boolean dimmed;
    /** 0 when off, 1 when on, in between while the thumb slides. */
    private float position;
    private ValueAnimator slide;

    CompactSwitch(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
    }

    void setOnCheckedChangeListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void setChecked(boolean checked) {
        if (this.checked == checked) {
            return;
        }
        this.checked = checked;
        if (isAttachedToWindow() && isLaidOut()) {
            slideTo(checked ? 1f : 0f);
        } else {
            stopSlide();
            position = checked ? 1f : 0f;
            invalidate();
        }
        if (listener != null) {
            listener.onCheckedChanged(checked);
        }
    }

    /**
     * A faded switch is one that cannot be turned on yet. It still answers a tap, so the user can be
     * told why.
     */
    void setDimmed(boolean dimmed) {
        if (this.dimmed != dimmed) {
            this.dimmed = dimmed;
            invalidate();
        }
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }

    @Override
    public boolean performClick() {
        toggle();
        boolean handled = super.performClick();
        if (!handled) {
            playSoundEffect(SoundEffectConstants.CLICK);
        }
        return handled;
    }

    private void slideTo(float target) {
        stopSlide();
        slide = ValueAnimator.ofFloat(position, target);
        slide.setDuration(SLIDE_MS);
        slide.setInterpolator(new DecelerateInterpolator());
        slide.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                position = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        slide.start();
    }

    private void stopSlide() {
        if (slide != null) {
            slide.cancel();
            slide = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSlide();
        position = checked ? 1f : 0f;
        super.onDetachedFromWindow();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                resolveSize(Math.round((TRACK_W + 2 * TOUCH_X) * density), widthMeasureSpec),
                resolveSize(Math.round((TRACK_H + 2 * TOUCH_Y) * density), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float trackW = TRACK_W * density;
        float trackH = TRACK_H * density;
        float left = (getWidth() - trackW) / 2f;
        float top = (getHeight() - trackH) / 2f;
        float corner = trackH / 2f;
        float p = position;
        float fade = dimmed ? DIMMED : 1f;

        // The off track's fill and outline give way to the accent as the thumb travels.
        rect.set(left, top, left + trackW, top + trackH);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.alpha(Ui.blend(Ui.COLOR_PRIMARY, Ui.COLOR_SWITCH_OFF_TRACK, p), fade));
        canvas.drawRoundRect(rect, corner, corner, paint);
        if (p < 1f) {
            float stroke = OUTLINE * density;
            rect.inset(stroke / 2f, stroke / 2f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(Ui.alpha(Ui.COLOR_SWITCH_OFF_STROKE, (1f - p) * fade));
            canvas.drawRoundRect(rect, corner - stroke / 2f, corner - stroke / 2f, paint);
        }

        float diameter = (THUMB_OFF + (THUMB_ON - THUMB_OFF) * p) * density;
        float cx = left + corner + (trackW - 2f * corner) * p;
        float cy = top + corner;
        int thumb = Ui.alpha(Ui.blend(Ui.COLOR_ON_ACCENT, Ui.COLOR_SWITCH_OFF_STROKE, p), fade);
        paint.setStyle(Paint.Style.FILL);
        if (isPressed() || isFocused()) {
            paint.setColor(Ui.alpha(checked ? Ui.COLOR_PRIMARY : Ui.COLOR_SWITCH_OFF_STROKE, 0.22f));
            canvas.drawCircle(cx, cy, HALO * density, paint);
        }
        paint.setColor(thumb);
        canvas.drawCircle(cx, cy, diameter / 2f, paint);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Switch.class.getName();
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setChecked(checked);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(checked);
    }
}
