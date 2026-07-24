package com.jpitsg.sysman;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Very basic full-text editor for the OpenVPN profile.conf. */
final class OpenVpnEditDialog {
    interface Listener {
        void onSave(String text);
    }

    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_BORDER = 0xFFE1E8E5;
    private static final int COLOR_FIELD_BG = 0xFFF1F5F3;
    private static final int COLOR_FIELD_BORDER = 0xFFDBE3DF;
    private static final int COLOR_TEXT = 0xFF15201C;
    private static final int COLOR_TEXT_DIM = 0xFF5B6B66;
    private static final int COLOR_NEUTRAL = 0xFFE9EDEA;
    private static final int COLOR_NEUTRAL_ON = 0xFF15201C;
    private static final int COLOR_PRIMARY = 0xFF1F6F4F;

    private OpenVpnEditDialog() {
    }

    static void show(Context context, String initialText, Listener listener) {
        new EditDialog(context, initialText, listener).show();
    }

    private static final class EditDialog extends Dialog {
        private final String initialText;
        private final Listener listener;

        EditDialog(Context context, String initialText, Listener listener) {
            super(context);
            this.initialText = initialText == null ? "" : initialText;
            this.listener = listener;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Context c = getContext();
            LinearLayout root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
            root.setBackground(roundedFill(COLOR_SURFACE, dp(c, 18), dp(c, 1), COLOR_BORDER));

            TextView title = new TextView(c);
            title.setText("Edit Profile");
            title.setTextSize(17);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            title.setTextColor(COLOR_TEXT);
            root.addView(title, matchWrap());

            TextView subtitle = new TextView(c);
            subtitle.setText("Editing profile.conf — saved verbatim, then re-validated. Keep the managed cd and file lines intact.");
            subtitle.setTextSize(11);
            subtitle.setTextColor(COLOR_TEXT_DIM);
            subtitle.setLineSpacing(0, 1.15f);
            subtitle.setPadding(0, dp(c, 4), 0, dp(c, 10));
            root.addView(subtitle, matchWrap());

            final EditText editor = new EditText(c);
            editor.setText(initialText);
            editor.setTextSize(12);
            editor.setTextColor(COLOR_TEXT);
            editor.setTypeface(Typeface.MONOSPACE);
            editor.setGravity(Gravity.TOP | Gravity.START);
            editor.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setHorizontallyScrolling(false);
            editor.setVerticalScrollBarEnabled(true);
            editor.setBackground(roundedFill(COLOR_FIELD_BG, dp(c, 12), dp(c, 1), COLOR_FIELD_BORDER));
            editor.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
            LinearLayout.LayoutParams editorLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            root.addView(editor, editorLp);

            LinearLayout buttons = new LinearLayout(c);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END);
            buttons.setPadding(0, dp(c, 12), 0, 0);

            buttons.addView(button(c, "Cancel", COLOR_NEUTRAL, COLOR_NEUTRAL_ON, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            }));
            Button save = button(c, "Save", COLOR_PRIMARY, 0xFFFFFFFF, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String text = editor.getText() == null ? "" : editor.getText().toString();
                    dismiss();
                    listener.onSave(text);
                }
            });
            LinearLayout.LayoutParams saveLp = (LinearLayout.LayoutParams) save.getLayoutParams();
            saveLp.setMargins(dp(c, 10), 0, 0, 0);
            buttons.addView(save);
            root.addView(buttons, matchWrap());

            setContentView(root);
            if (getWindow() != null) {
                getWindow().setLayout(
                        (int) (c.getResources().getDisplayMetrics().widthPixels * 0.94f),
                        (int) (c.getResources().getDisplayMetrics().heightPixels * 0.82f));
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
