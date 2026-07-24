package com.jpitsg.sysman;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Presents the result of validating an imported OpenVPN profile: errors block
 * the import (Close only); a clean/warned result offers Keep / Discard.
 */
final class OpenVpnImportDialog {
    interface Listener {
        void onKeep();

        void onDiscard();
    }

    private OpenVpnImportDialog() {
    }

    static void show(Context context, OpenVpnValidationResult result, Listener listener) {
        new ReportDialog(context, result, listener).show();
    }

    static void showReport(Context context, OpenVpnProfileStore.Meta meta) {
        OpenVpnValidationResult result = new OpenVpnValidationResult();
        result.remoteHost = meta.remoteHost;
        result.remotePort = meta.remotePort;
        result.remoteProto = meta.remoteProto;
        result.devType = meta.devType;
        for (String w : meta.warnings) {
            result.warn(w);
        }
        for (String slot : meta.requiredSlots) {
            if (!meta.satisfiedSlots.containsKey(slot)) {
                result.requireSlot(slot);
            }
        }
        new ReportDialog(context, result, null).show();
    }

    private static final class ReportDialog extends Dialog {
        private final OpenVpnValidationResult result;
        private final Listener listener;

        ReportDialog(Context context, OpenVpnValidationResult result, Listener listener) {
            super(context);
            this.result = result;
            this.listener = listener;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Context c = getContext();
            LinearLayout root = Ui.dialogCard(c);

            root.addView(Ui.dialogTitle(c, "Profile Import"), Ui.matchWrap());

            if (!result.remoteHost.isEmpty()) {
                TextView summary = Ui.dialogSubtitle(c, result.remoteHost + ":" + result.remotePort
                        + " " + result.remoteProto + " · dev " + result.devType);
                summary.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
                root.addView(summary, Ui.matchWrap());
            }

            ScrollView scroll = new ScrollView(c);
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list);

            for (String e : result.errors) {
                list.addView(row(c, "✕  " + e, Ui.COLOR_BAD, true));
            }
            for (String w : result.warnings) {
                list.addView(row(c, "•  " + w, Ui.COLOR_TEXT_DIM, false));
            }
            for (String slot : result.requiredSlots) {
                if (!result.satisfiedSlots.containsKey(slot)) {
                    list.addView(row(c, "↑  needs file: " + slot, Ui.COLOR_TEXT_FAINT, false));
                }
            }
            if (result.errors.isEmpty() && result.warnings.isEmpty() && result.requiredSlots.isEmpty()) {
                list.addView(row(c, "No issues found.", Ui.COLOR_TEXT_DIM, false));
            }

            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            scrollLp.setMargins(0, Ui.dp(c, 4), 0, Ui.dp(c, Ui.GAP));
            root.addView(scroll, scrollLp);

            LinearLayout buttons = new LinearLayout(c);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END);

            if (listener == null || !result.ok()) {
                buttons.addView(Ui.neutralButton(c, "Close", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (listener != null) {
                            listener.onDiscard();
                        }
                        dismiss();
                    }
                }), buttonParams(c, false));
            } else {
                buttons.addView(Ui.neutralButton(c, "Discard", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onDiscard();
                        dismiss();
                    }
                }), buttonParams(c, false));
                buttons.addView(Ui.primaryButton(c, "Keep Profile", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onKeep();
                        dismiss();
                    }
                }), buttonParams(c, true));
            }
            root.addView(buttons, Ui.matchWrap());

            setContentView(root);
            Ui.sizeDialogWindow(this, Ui.DIALOG_WIDTH_FRACTION, 0f);
        }

        private TextView row(Context c, String text, int color, boolean bold) {
            TextView t = new TextView(c);
            t.setText(text);
            t.setTextSize(13);
            t.setTextColor(color);
            t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
            t.setLineSpacing(0, 1.15f);
            t.setPadding(0, Ui.dp(c, 5), 0, Ui.dp(c, 5));
            return t;
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
