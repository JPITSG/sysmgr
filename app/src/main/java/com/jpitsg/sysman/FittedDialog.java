package com.jpitsg.sysman;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The base of the app's dialogs. It sizes the dialog's window as a share of the screen and keeps
 * it that way. The activity handles rotation itself instead of being recreated, so a window that
 * was sized once, in pixels, would keep its old size when the screen turned: wider than the new
 * screen, with its buttons out of reach. The activity calls {@link #refitOpen} whenever the screen
 * changes, and every open dialog is sized again from the screen as it is now.
 */
abstract class FittedDialog extends Dialog {
    /** The dialogs on screen, held weakly so a dialog that never reports stopping is not kept alive. */
    private static final List<WeakReference<FittedDialog>> OPEN = new ArrayList<>();

    private final float widthFraction;
    private final float heightFraction;

    /**
     * @param widthFraction  share of the screen width, though never wider than {@link Ui#CONTENT_MAX_WIDTH}
     * @param heightFraction share of the screen height; 0 sizes the dialog to its content, which
     *                       {@link DialogCard} keeps within the screen
     */
    FittedDialog(Context context, float widthFraction, float heightFraction) {
        super(context);
        this.widthFraction = widthFraction;
        this.heightFraction = heightFraction;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            // The card draws its own rounded surface, so the window behind it must be clear.
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        fit();
        OPEN.add(new WeakReference<>(this));
    }

    @Override
    protected void onStop() {
        for (Iterator<WeakReference<FittedDialog>> it = OPEN.iterator(); it.hasNext(); ) {
            FittedDialog dialog = it.next().get();
            if (dialog == null || dialog == this) {
                it.remove();
            }
        }
        super.onStop();
    }

    /** Sizes every open dialog from the screen as it is now. */
    static void refitOpen() {
        for (Iterator<WeakReference<FittedDialog>> it = OPEN.iterator(); it.hasNext(); ) {
            FittedDialog dialog = it.next().get();
            if (dialog == null) {
                it.remove();
            } else {
                dialog.fit();
            }
        }
    }

    private void fit() {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        DisplayMetrics m = getContext().getResources().getDisplayMetrics();
        int width = Math.min(Math.round(m.widthPixels * widthFraction),
                Math.round(Ui.CONTENT_MAX_WIDTH * m.density));
        int height = heightFraction > 0f
                ? Math.round(m.heightPixels * heightFraction)
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        window.setLayout(width, height);
    }
}
