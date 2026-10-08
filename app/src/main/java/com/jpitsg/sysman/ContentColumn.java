package com.jpitsg.sysman;

import android.content.Context;
import android.widget.LinearLayout;

/** A vertical column that stops growing at a readable width and is centred by its parent on wide screens. */
final class ContentColumn extends LinearLayout {
    private final int maxWidth;

    ContentColumn(Context context) {
        super(context);
        setOrientation(VERTICAL);
        maxWidth = Ui.dp(context, Ui.CONTENT_MAX_WIDTH);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && width > maxWidth) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
