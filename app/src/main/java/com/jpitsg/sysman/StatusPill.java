package com.jpitsg.sysman;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

/** A compact state label: a coloured dot and a few capital letters on a tinted capsule. */
final class StatusPill extends TextView {
    private final boolean showDot;
    private int tone = -1;

    StatusPill(Context context, boolean showDot) {
        super(context);
        this.showDot = showDot;
        setTextSize(10.5f);
        setTypeface(Typeface.DEFAULT_BOLD);
        setLetterSpacing(0.1f);
        setGravity(Gravity.CENTER);
        setIncludeFontPadding(false);
        setMaxWidth(Ui.dp(context, 190));
        int h = Ui.dp(context, showDot ? 11 : 10);
        setPadding(h, Ui.dp(context, 7), h, Ui.dp(context, 7));
        Ui.marqueeLabel(this);
        set("", Ui.TONE_NEUTRAL);
    }

    /** Applies the text and the tone's colours; restarts the marquee only when the words changed. */
    void set(CharSequence text, int tone) {
        CharSequence previous = getText();
        boolean changed = previous == null || !text.toString().contentEquals(previous);
        if (!changed && tone == this.tone) {
            return;
        }
        this.tone = tone;
        setText(text);
        setTextColor(Ui.toneFg(tone));
        setBackground(Ui.roundedFill(Ui.toneBg(tone), Ui.dp(getContext(), Ui.PILL_CORNER), 0, 0));
        if (showDot) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Ui.toneFg(tone));
            int size = Ui.dp(getContext(), 7);
            dot.setSize(size, size);
            setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
            setCompoundDrawablePadding(Ui.dp(getContext(), 7));
        }
        if (changed) {
            Ui.rearmMarquee(this);
        }
    }
}
