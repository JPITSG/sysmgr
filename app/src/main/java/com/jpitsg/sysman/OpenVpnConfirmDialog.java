package com.jpitsg.sysman;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Simple confirm dialog for replace-while-connected and Clear Profile. */
final class OpenVpnConfirmDialog {
    interface Listener {
        void onConfirm();
    }

    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_BORDER = 0xFFE1E8E5;
    private static final int COLOR_TEXT = 0xFF15201C;
    private static final int COLOR_TEXT_DIM = 0xFF5B6B66;
    private static final int COLOR_NEUTRAL = 0xFFE9EDEA;
    private static final int COLOR_NEUTRAL_ON = 0xFF15201C;
    private static final int COLOR_PRIMARY = 0xFF1F6F4F;
    private static final int COLOR_DANGER = 0xFFB94436;

    private OpenVpnConfirmDialog() {
    }

    static void show(Context context, String title, String message, String confirmLabel,
                     boolean danger, Listener listener) {
        new ConfirmDialog(context, title, message, confirmLabel, danger, listener).show();
    }

    private static final class ConfirmDialog extends Dialog {
        private final String title;
        private final String message;
        private final String confirmLabel;
        private final boolean danger;
        private final Listener listener;

        ConfirmDialog(Context context, String title, String message, String confirmLabel,
                      boolean danger, Listener listener) {
            super(context);
            this.title = title;
            this.message = message;
            this.confirmLabel = confirmLabel;
            this.danger = danger;
            this.listener = listener;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Context c = getContext();
            LinearLayout root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 16));
            root.setBackground(roundedFill(COLOR_SURFACE, dp(c, 18), dp(c, 1), COLOR_BORDER));

            TextView titleView = new TextView(c);
            titleView.setText(title);
            titleView.setTextSize(17);
            titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            titleView.setTextColor(COLOR_TEXT);
            root.addView(titleView, matchWrap());

            TextView messageView = new TextView(c);
            messageView.setText(message);
            messageView.setTextSize(14);
            messageView.setTextColor(COLOR_TEXT_DIM);
            messageView.setLineSpacing(0, 1.2f);
            messageView.setPadding(0, dp(c, 8), 0, dp(c, 16));
            root.addView(messageView, matchWrap());

            LinearLayout buttons = new LinearLayout(c);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END);

            buttons.addView(button(c, "Cancel", COLOR_NEUTRAL, COLOR_NEUTRAL_ON, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            }));
            Button confirm = button(c, confirmLabel, danger ? COLOR_DANGER : COLOR_PRIMARY, 0xFFFFFFFF,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dismiss();
                            listener.onConfirm();
                        }
                    });
            LinearLayout.LayoutParams confirmLp = (LinearLayout.LayoutParams) confirm.getLayoutParams();
            confirmLp.setMargins(dp(c, 10), 0, 0, 0);
            buttons.addView(confirm);

            root.addView(buttons, matchWrap());
            setContentView(root);
            if (getWindow() != null) {
                getWindow().setLayout(
                        (int) (c.getResources().getDisplayMetrics().widthPixels * 0.9f),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }

        private Button button(Context c, String text, int bg, int fg, View.OnClickListener l) {
            Button b = new Button(c);
            b.setText(text);
            b.setAllCaps(false);
            b.setTextColor(fg);
            b.setTextSize(14);
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            b.setStateListAnimator(null);
            b.setElevation(0);
            b.setBackground(roundedFill(bg, dp(c, 12), 0, 0));
            b.setPadding(dp(c, 18), dp(c, 8), dp(c, 18), dp(c, 8));
            b.setOnClickListener(l);
            b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 46)));
            return b;
        }
    }

    private static GradientDrawable roundedFill(int color, int cornerDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(cornerDp);
        if (strokeDp > 0) {
            d.setStroke(strokeDp, strokeColor);
        }
        return d;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
