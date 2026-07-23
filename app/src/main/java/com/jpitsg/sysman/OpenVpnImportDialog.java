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
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Presents the result of validating an imported OpenVPN profile: errors block
 * the import (Close only); a clean/warned result offers Keep / Discard.
 */
final class OpenVpnImportDialog {
    interface Listener {
        void onKeep();

        void onDiscard();
    }

    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_BORDER = 0xFFE1E8E5;
    private static final int COLOR_TEXT = 0xFF15201C;
    private static final int COLOR_TEXT_DIM = 0xFF5B6B66;
    private static final int COLOR_FAINT = 0xFF8C9A95;
    private static final int COLOR_PRIMARY = 0xFF1F6F4F;
    private static final int COLOR_PRIMARY_CONTAINER = 0xFFDDEEE5;
    private static final int COLOR_PRIMARY_ON = 0xFF0D3E2C;
    private static final int COLOR_NEUTRAL = 0xFFE9EDEA;
    private static final int COLOR_NEUTRAL_ON = 0xFF15201C;
    private static final int COLOR_BAD = 0xFFB94436;

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
            LinearLayout root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
            root.setBackground(roundedFill(COLOR_SURFACE, dp(c, 18), dp(c, 1), COLOR_BORDER));

            TextView title = new TextView(c);
            title.setText("Profile Import");
            title.setTextSize(17);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            title.setTextColor(COLOR_TEXT);
            root.addView(title, matchWrap());

            if (!result.remoteHost.isEmpty()) {
                TextView summary = new TextView(c);
                summary.setText(result.remoteHost + ":" + result.remotePort + " " + result.remoteProto
                        + " · dev " + result.devType);
                summary.setTextSize(12);
                summary.setTextColor(COLOR_TEXT_DIM);
                summary.setPadding(0, dp(c, 4), 0, dp(c, 8));
                root.addView(summary, matchWrap());
            }

            ScrollView scroll = new ScrollView(c);
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(list);

            for (String e : result.errors) {
                list.addView(row(c, "✕  " + e, COLOR_BAD, true));
            }
            for (String w : result.warnings) {
                list.addView(row(c, "•  " + w, COLOR_TEXT_DIM, false));
            }
            for (String slot : result.requiredSlots) {
                if (!result.satisfiedSlots.containsKey(slot)) {
                    list.addView(row(c, "↑  needs file: " + slot, COLOR_FAINT, false));
                }
            }
            if (result.errors.isEmpty() && result.warnings.isEmpty() && result.requiredSlots.isEmpty()) {
                list.addView(row(c, "No issues found.", COLOR_TEXT_DIM, false));
            }

            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            scrollLp.setMargins(0, dp(c, 4), 0, dp(c, 12));
            root.addView(scroll, scrollLp);

            LinearLayout buttons = new LinearLayout(c);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END);

            if (listener == null || !result.ok()) {
                buttons.addView(button(c, "Close", COLOR_NEUTRAL, COLOR_NEUTRAL_ON, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (listener != null) {
                            listener.onDiscard();
                        }
                        dismiss();
                    }
                }));
            } else {
                buttons.addView(button(c, "Discard", COLOR_NEUTRAL, COLOR_NEUTRAL_ON, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onDiscard();
                        dismiss();
                    }
                }));
                Button keep = button(c, "Keep Profile", COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onKeep();
                        dismiss();
                    }
                });
                LinearLayout.LayoutParams keepLp = (LinearLayout.LayoutParams) keep.getLayoutParams();
                keepLp.setMargins(dp(c, 10), 0, 0, 0);
                buttons.addView(keep);
            }
            root.addView(buttons, matchWrap());

            setContentView(root);
            if (getWindow() != null) {
                getWindow().setLayout(
                        (int) (c.getResources().getDisplayMetrics().widthPixels * 0.92f),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }

        private TextView row(Context c, String text, int color, boolean bold) {
            TextView t = new TextView(c);
            t.setText(text);
            t.setTextSize(13);
            t.setTextColor(color);
            t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
            t.setLineSpacing(0, 1.15f);
            t.setPadding(0, dp(c, 5), 0, dp(c, 5));
            return t;
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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 46));
            b.setLayoutParams(lp);
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
