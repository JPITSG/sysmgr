package com.jpitsg.sysman;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** App-styled confirmation dialog for destructive or state-changing actions. */
final class OpenVpnConfirmDialog {
    interface Listener {
        void onConfirm();
    }

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
            LinearLayout root = Ui.dialogCard(c);

            root.addView(Ui.dialogTitle(c, title), Ui.matchWrap());

            TextView messageView = Ui.dialogSubtitle(c, message);
            messageView.setTextSize(14);
            messageView.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, Ui.GAP + 4));
            root.addView(messageView, Ui.matchWrap());

            LinearLayout buttons = new LinearLayout(c);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END);

            buttons.addView(Ui.neutralButton(c, "Cancel", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            }), buttonParams(c, false));

            final View.OnClickListener confirmClick = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                    listener.onConfirm();
                }
            };
            Button confirm = danger
                    ? Ui.dangerButton(c, confirmLabel, confirmClick)
                    : Ui.primaryButton(c, confirmLabel, confirmClick);
            buttons.addView(confirm, buttonParams(c, true));

            root.addView(buttons, Ui.matchWrap());
            setContentView(root);
            Ui.sizeDialogWindow(this, Ui.DIALOG_WIDTH_FRACTION, 0f);
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
