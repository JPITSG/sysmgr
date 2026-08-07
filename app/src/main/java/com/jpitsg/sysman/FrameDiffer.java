package com.jpitsg.sysman;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns two consecutive frames into the list of rectangles that actually
 * changed.
 *
 * <p>This is what makes three frames a second feel usable: a still screen sends
 * nothing at all, and a tap sends one small rectangle instead of eight
 * megabytes. Tiles are compared with an early exit on the first differing
 * pixel, then coalesced — horizontally into runs, then vertically where runs
 * line up — so a changing status bar is one rectangle rather than seventeen.
 */
final class FrameDiffer {
    static final int TILE = 64;

    private final int width;
    private final int height;
    private final int tileCols;
    private final int tileRows;
    private final boolean[] dirty;

    FrameDiffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.tileCols = (width + TILE - 1) / TILE;
        this.tileRows = (height + TILE - 1) / TILE;
        this.dirty = new boolean[tileCols * tileRows];
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    /** The whole frame as one rectangle, for the first update and after a resize. */
    List<Rect> fullFrame() {
        List<Rect> rects = new ArrayList<>(1);
        rects.add(new Rect(0, 0, width, height));
        return rects;
    }

    /**
     * @param current  the newest frame's pixels
     * @param previous the frame before it, same dimensions
     * @return coalesced dirty rectangles, empty when nothing moved
     */
    List<Rect> diff(int[] current, int[] previous) {
        if (current == null || previous == null
                || current.length < width * height || previous.length < width * height) {
            return fullFrame();
        }

        for (int row = 0; row < tileRows; row++) {
            int y0 = row * TILE;
            int y1 = Math.min(y0 + TILE, height);
            for (int col = 0; col < tileCols; col++) {
                int x0 = col * TILE;
                int x1 = Math.min(x0 + TILE, width);
                dirty[row * tileCols + col] = tileChanged(current, previous, x0, y0, x1, y1);
            }
        }
        return coalesce();
    }

    private boolean tileChanged(int[] current, int[] previous, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            int base = y * width;
            for (int x = x0; x < x1; x++) {
                if (current[base + x] != previous[base + x]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Merges dirty tiles into as few rectangles as possible: each tile row
     * becomes horizontal runs, and a run is grown downwards whenever the row
     * below has a run with exactly the same span.
     */
    private List<Rect> coalesce() {
        List<Rect> rects = new ArrayList<>();
        // Open runs from the previous row, keyed by their column span.
        List<Rect> open = new ArrayList<>();

        for (int row = 0; row < tileRows; row++) {
            List<Rect> runs = runsInRow(row);
            List<Rect> stillOpen = new ArrayList<>(runs.size());

            for (Rect run : runs) {
                Rect matched = null;
                for (Rect candidate : open) {
                    if (candidate.left == run.left && candidate.right == run.right) {
                        matched = candidate;
                        break;
                    }
                }
                if (matched != null) {
                    matched.bottom = run.bottom;
                    open.remove(matched);
                    stillOpen.add(matched);
                } else {
                    stillOpen.add(run);
                }
            }
            // Anything not extended by this row is finished.
            rects.addAll(open);
            open = stillOpen;
        }
        rects.addAll(open);
        return rects;
    }

    private List<Rect> runsInRow(int row) {
        List<Rect> runs = new ArrayList<>();
        int y0 = row * TILE;
        int y1 = Math.min(y0 + TILE, height);
        int runStart = -1;

        for (int col = 0; col <= tileCols; col++) {
            boolean isDirty = col < tileCols && dirty[row * tileCols + col];
            if (isDirty && runStart < 0) {
                runStart = col;
            } else if (!isDirty && runStart >= 0) {
                int x0 = runStart * TILE;
                int x1 = Math.min(col * TILE, width);
                runs.add(new Rect(x0, y0, x1, y1));
                runStart = -1;
            }
        }
        return runs;
    }

    /** Total pixels covered by a rectangle list, for logging and stats. */
    static long area(List<Rect> rects) {
        long total = 0;
        for (Rect rect : rects) {
            total += (long) rect.width() * rect.height();
        }
        return total;
    }
}
