package com.jpitsg.sysman;

import android.content.Context;
import android.widget.LinearLayout;

/**
 * The card at the root of a dialog. It never grows taller than the screen it is on, so a long
 * body scrolls inside the card instead of pushing the buttons off the screen. The limit is read
 * on every layout pass rather than once when the dialog is built, because the screen can turn
 * while a dialog is open.
 */
final class DialogCard extends LinearLayout {
    DialogCard(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int limit = Math.round(getResources().getDisplayMetrics().heightPixels * Ui.DIALOG_MAX_HEIGHT_FRACTION);
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        if (mode == MeasureSpec.UNSPECIFIED
                || (mode == MeasureSpec.AT_MOST && MeasureSpec.getSize(heightMeasureSpec) > limit)) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
