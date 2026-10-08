package com.jpitsg.sysman;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.Button;

/**
 * A button whose optional leading icon follows the text colour and is laid out
 * together with the label as one centred group. Without an icon it behaves
 * exactly like a plain {@link Button}.
 */
final class IconButton extends Button {
    private Drawable icon;
    private int iconSizePx;

    IconButton(Context context) {
        super(context);
        setGravity(Gravity.CENTER);
    }

    /** {@code resId} of 0 removes the icon. */
    void setIcon(int resId, int sizeDp, int gapDp) {
        if (resId == 0) {
            icon = null;
            setCompoundDrawablesRelative(null, null, null, null);
            setGravity(Gravity.CENTER);
            return;
        }
        iconSizePx = Ui.dp(getContext(), sizeDp);
        icon = getContext().getDrawable(resId).mutate();
        icon.setBounds(0, 0, iconSizePx, iconSizePx);
        icon.setTint(getCurrentTextColor());
        setCompoundDrawablesRelative(icon, null, null, null);
        setCompoundDrawablePadding(Ui.dp(getContext(), gapDp));
        // Start-aligned so onDraw can shift the whole group to the middle.
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        if (icon != null) {
            icon.setTint(color);
        }
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        super.setTextColor(colors);
        if (icon != null) {
            icon.setTint(getCurrentTextColor());
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (icon == null || getLayout() == null) {
            super.onDraw(canvas);
            return;
        }
        float group = iconSizePx + getCompoundDrawablePadding() + getLayout().getLineWidth(0);
        float room = getWidth() - getPaddingLeft() - getPaddingRight() - group;
        canvas.save();
        canvas.translate(Math.max(0f, room / 2f), 0f);
        super.onDraw(canvas);
        canvas.restore();
    }
}
