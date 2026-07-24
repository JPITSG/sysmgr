package com.jpitsg.sysman;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
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
            LinearLayout root = Ui.dialogCard(c);

            root.addView(Ui.dialogTitle(c, "Edit Profile"), Ui.matchWrap());

            TextView subtitle = Ui.dialogSubtitle(c,
                    "Editing profile.conf — saved verbatim, then re-validated. Keep the managed cd and file lines intact.");
            subtitle.setTextSize(11);
            subtitle.setLineSpacing(0, 1.15f);
            subtitle.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 10));
            root.addView(subtitle, Ui.matchWrap());

            final EditText editor = new EditText(c);
            editor.setText(initialText);
            editor.setTextSize(12);
            editor.setTextColor(Ui.COLOR_TEXT);
            editor.setTypeface(Typeface.MONOSPACE);
            editor.setGravity(Gravity.TOP | Gravity.START);
            editor.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setHorizontallyScrolling(false);
            editor.setVerticalScrollBarEnabled(true);
            editor.setBackground(Ui.roundedFill(Ui.COLOR_FIELD_BG, Ui.dp(c, Ui.FIELD_CORNER), Ui.dp(c, 1), Ui.COLOR_FIELD_BORDER));
            editor.setPadding(Ui.dp(c, Ui.GAP), Ui.dp(c, 10), Ui.dp(c, Ui.GAP), Ui.dp(c, 10));
            LinearLayout.LayoutParams editorLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            root.addView(editor, editorLp);

            LinearLayout buttons = new LinearLayout(c);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END);
            buttons.setPadding(0, Ui.dp(c, Ui.GAP), 0, 0);

            buttons.addView(Ui.neutralButton(c, "Cancel", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            }), buttonParams(c, false));
            Button save = Ui.primaryButton(c, "Save", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String text = editor.getText() == null ? "" : editor.getText().toString();
                    dismiss();
                    listener.onSave(text);
                }
            });
            buttons.addView(save, buttonParams(c, true));
            root.addView(buttons, Ui.matchWrap());

            setContentView(root);
            Ui.sizeDialogWindow(this, Ui.DIALOG_WIDTH_FRACTION, 0.82f);
        }
    }

    private static LinearLayout.LayoutParams buttonParams(Context c, boolean leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (leftMargin) {
            lp.setMargins(Ui.dp(c, Ui.GAP), 0, 0, 0);
        }
        return lp;
    }
}
