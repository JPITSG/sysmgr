package com.jpitsg.sysman;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Lays tiles out in equal-width columns, as many as fit at a comfortable
 * minimum tile width, with every tile in a row matching the tallest. Hidden
 * children take no space, so a tile can come and go without leaving a hole.
 */
final class TileGrid extends ViewGroup {
    private final int gap;
    private final int minTileWidth;
    private int columns = 2;
    private int tileWidth;
    private int[] rowHeights = new int[0];

    TileGrid(Context context, int minTileDp, int gapDp) {
        super(context);
        this.gap = Ui.dp(context, gapDp);
        this.minTileWidth = Ui.dp(context, minTileDp);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        columns = Math.max(2, Math.min(4, (width + gap) / (minTileWidth + gap)));
        tileWidth = (width - gap * (columns - 1)) / columns;

        int visible = 0;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getVisibility() != GONE) {
                visible++;
            }
        }
        int rows = (visible + columns - 1) / columns;
        rowHeights = new int[Math.max(rows, 0)];

        // A tile that would sit alone in the last row spans it instead of leaving a hole.
        boolean loneLast = visible > 0 && visible % columns == 1;
        int index = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            boolean spans = loneLast && index == visible - 1;
            if (child instanceof FeatureTile) {
                ((FeatureTile) child).setWide(spans);
            }
            child.measure(MeasureSpec.makeMeasureSpec(spans ? width : tileWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int row = index / columns;
            rowHeights[row] = Math.max(rowHeights[row], child.getMeasuredHeight());
            index++;
        }

        // Second pass: equalise the height of the tiles sharing a row.
        index = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            boolean spans = loneLast && index == visible - 1;
            child.measure(MeasureSpec.makeMeasureSpec(spans ? width : tileWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(rowHeights[index / columns], MeasureSpec.EXACTLY));
            index++;
        }

        int height = 0;
        for (int rowHeight : rowHeights) {
            height += rowHeight;
        }
        if (rows > 1) {
            height += gap * (rows - 1);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int index = 0;
        int y = 0;
        int rowStart = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int column = index % columns;
            int row = index / columns;
            if (row != rowStart) {
                y += rowHeights[rowStart] + gap;
                rowStart = row;
            }
            int x = column * (tileWidth + gap);
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            index++;
        }
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
