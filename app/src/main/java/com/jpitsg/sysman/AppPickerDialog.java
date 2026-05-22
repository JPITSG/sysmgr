package com.jpitsg.sysman;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class AppPickerDialog {
    interface Listener {
        void onAppSelected(String packageName, String label);
    }

    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_BORDER = 0xFFE1E8E5;
    private static final int COLOR_FIELD_BG = 0xFFF1F5F3;
    private static final int COLOR_FIELD_BORDER = 0xFFDBE3DF;
    private static final int COLOR_TEXT = 0xFF15201C;
    private static final int COLOR_TEXT_DIM = 0xFF5B6B66;
    private static final int COLOR_PRIMARY = 0xFF1F6F4F;
    private static final Object CACHE_LOCK = new Object();
    private static List<AppEntry> cachedApps;

    private AppPickerDialog() {
    }

    static void show(Context context, String currentPackage, Listener listener) {
        PickerDialog dialog = new PickerDialog(context, currentPackage, listener);
        dialog.show();
    }

    private static final class PickerDialog extends Dialog {
        private final String currentPackage;
        private final Listener listener;

        PickerDialog(Context context, String currentPackage, Listener listener) {
            super(context);
            this.currentPackage = currentPackage == null ? "" : currentPackage.trim();
            this.listener = listener;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            LinearLayout root = new LinearLayout(getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(getContext(), 18), dp(getContext(), 16), dp(getContext(), 18), dp(getContext(), 18));
            root.setBackground(roundedFill(COLOR_SURFACE, dp(getContext(), 18), dp(getContext(), 1), COLOR_BORDER));

            LinearLayout header = new LinearLayout(getContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(header, matchWrap());

            final AppAdapter adapter = new AppAdapter(getContext(), new ArrayList<AppEntry>());

            final EditText search = new EditText(getContext());
            search.setSingleLine(true);
            search.setHint("Search apps");
            search.setTextSize(15);
            search.setTextColor(COLOR_TEXT);
            search.setHintTextColor(0xFF8C9A95);
            search.setBackground(roundedFill(COLOR_FIELD_BG, dp(getContext(), 12), dp(getContext(), 1), COLOR_FIELD_BORDER));
            search.setGravity(Gravity.CENTER_VERTICAL);
            search.setMinHeight(dp(getContext(), 50));
            search.setPadding(dp(getContext(), 14), dp(getContext(), 10), dp(getContext(), 14), dp(getContext(), 10));
            header.addView(search, new LinearLayout.LayoutParams(0, dp(getContext(), 50), 1f));

            TextView close = new TextView(getContext());
            close.setText("×");
            close.setTextSize(28);
            close.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            close.setTextColor(COLOR_TEXT_DIM);
            close.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(getContext(), 44), dp(getContext(), 50));
            closeLp.setMargins(dp(getContext(), 10), 0, 0, 0);
            header.addView(close, closeLp);
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dismiss();
                }
            });

            final LinearLayout loading = new LinearLayout(getContext());
            loading.setOrientation(LinearLayout.HORIZONTAL);
            loading.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams loadingLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f);
            loadingLp.setMargins(0, dp(getContext(), 12), 0, 0);
            ProgressBar spinner = new ProgressBar(getContext());
            loading.addView(spinner, new LinearLayout.LayoutParams(dp(getContext(), 34), dp(getContext(), 34)));
            TextView loadingText = new TextView(getContext());
            loadingText.setText("Loading apps");
            loadingText.setTextSize(14);
            loadingText.setTextColor(COLOR_TEXT_DIM);
            LinearLayout.LayoutParams loadingTextLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            loadingTextLp.setMargins(dp(getContext(), 12), 0, 0, 0);
            loading.addView(loadingText, loadingTextLp);
            root.addView(loading, loadingLp);

            final ListView listView = new ListView(getContext());
            listView.setDividerHeight(1);
            listView.setAdapter(adapter);
            listView.setVisibility(View.GONE);
            LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f);
            listLp.setMargins(0, dp(getContext(), 18), 0, 0);
            root.addView(listView, listLp);

            TextView empty = new TextView(getContext());
            empty.setText("No apps found");
            empty.setTextSize(14);
            empty.setTextColor(COLOR_TEXT_DIM);
            empty.setGravity(Gravity.CENTER);
            listView.setEmptyView(empty);
            root.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0));

            search.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.filter(s == null ? "" : s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    AppEntry entry = adapter.getItem(position);
                    if (listener != null) {
                        listener.onAppSelected(entry.packageName, entry.label);
                    }
                    dismiss();
                }
            });

            setContentView(root);
            loadAppsAsync(getContext().getApplicationContext(), currentPackage, new LoadListener() {
                @Override
                public void onLoaded(List<AppEntry> entries) {
                    if (!isShowing()) {
                        return;
                    }
                    adapter.setEntries(entries);
                    loading.setVisibility(View.GONE);
                    listView.setVisibility(View.VISIBLE);
                }
            });
        }

        @Override
        public void show() {
            super.show();
            Window window = getWindow();
            if (window == null) {
                return;
            }
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.94f);
            int height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.86f);
            window.setLayout(width, height);
        }
    }

    private interface LoadListener {
        void onLoaded(List<AppEntry> entries);
    }

    private static void loadAppsAsync(final Context context, final String currentPackage, final LoadListener listener) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppEntry> entries = loadApps(context, currentPackage);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onLoaded(entries);
                    }
                });
            }
        }, "SystemManagerAppPicker").start();
    }

    private static List<AppEntry> loadApps(Context context, String currentPackage) {
        List<AppEntry> base;
        synchronized (CACHE_LOCK) {
            base = cachedApps;
        }
        if (base == null) {
            base = loadInstalledApps(context);
            synchronized (CACHE_LOCK) {
                if (cachedApps == null) {
                    cachedApps = base;
                } else {
                    base = cachedApps;
                }
            }
        }
        List<AppEntry> entries = new ArrayList<>();
        for (AppEntry entry : base) {
            boolean selected = currentPackage != null && !currentPackage.isEmpty() && currentPackage.equals(entry.packageName);
            entries.add(new AppEntry(entry.label, entry.packageName, entry.applicationInfo, selected));
        }
        sortApps(entries);
        return entries;
    }

    private static List<AppEntry> loadInstalledApps(Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> applications = packageManager.getInstalledApplications(0);
        List<AppEntry> entries = new ArrayList<>();
        for (ApplicationInfo applicationInfo : applications) {
            String packageName = applicationInfo.packageName;
            CharSequence rawLabel = applicationInfo.loadLabel(packageManager);
            String label = rawLabel == null ? packageName : rawLabel.toString().trim();
            if (label.isEmpty()) {
                label = packageName;
            }
            entries.add(new AppEntry(label, packageName, applicationInfo, false));
        }
        sortApps(entries);
        return entries;
    }

    private static void sortApps(List<AppEntry> entries) {
        Collections.sort(entries, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry left, AppEntry right) {
                if (left.selected != right.selected) {
                    return left.selected ? -1 : 1;
                }
                int labelCompare = left.label.toLowerCase(Locale.US).compareTo(right.label.toLowerCase(Locale.US));
                if (labelCompare != 0) {
                    return labelCompare;
                }
                return left.packageName.compareTo(right.packageName);
            }
        });
    }

    private static final class AppEntry {
        final String label;
        final String packageName;
        final ApplicationInfo applicationInfo;
        final boolean selected;

        AppEntry(String label, String packageName, ApplicationInfo applicationInfo, boolean selected) {
            this.label = label;
            this.packageName = packageName;
            this.applicationInfo = applicationInfo;
            this.selected = selected;
        }
    }

    private static final class AppAdapter extends BaseAdapter {
        private final Context context;
        private final PackageManager packageManager;
        private final List<AppEntry> all = new ArrayList<>();
        private final List<AppEntry> visible = new ArrayList<>();
        private String currentQuery = "";

        AppAdapter(Context context, List<AppEntry> entries) {
            this.context = context;
            this.packageManager = context.getPackageManager();
            setEntries(entries);
        }

        void setEntries(List<AppEntry> entries) {
            all.clear();
            visible.clear();
            all.addAll(entries);
            filter(currentQuery);
        }

        void filter(String query) {
            currentQuery = query == null ? "" : query;
            String normalized = currentQuery.trim().toLowerCase(Locale.US);
            visible.clear();
            if (normalized.isEmpty()) {
                visible.addAll(all);
            } else {
                for (AppEntry entry : all) {
                    if (entry.label.toLowerCase(Locale.US).contains(normalized)
                            || entry.packageName.toLowerCase(Locale.US).contains(normalized)) {
                        visible.add(entry);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public AppEntry getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowViews row;
            if (convertView == null) {
                row = createRow();
                convertView = row.root;
                convertView.setTag(row);
            } else {
                row = (RowViews) convertView.getTag();
            }

            AppEntry entry = getItem(position);
            row.label.setText(entry.label);
            row.packageName.setText(entry.packageName);
            row.selected.setVisibility(entry.selected ? View.VISIBLE : View.GONE);
            try {
                Drawable icon = entry.applicationInfo.loadIcon(packageManager);
                row.icon.setImageDrawable(icon);
            } catch (RuntimeException ignored) {
                row.icon.setImageDrawable(null);
            }
            return convertView;
        }

        private RowViews createRow() {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
            root.setMinimumHeight(dp(context, 68));

            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(context, 42), dp(context, 42));
            ilp.setMargins(0, 0, dp(context, 12), 0);
            root.addView(icon, ilp);

            LinearLayout textBlock = new LinearLayout(context);
            textBlock.setOrientation(LinearLayout.VERTICAL);
            textBlock.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(textBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView label = new TextView(context);
            label.setTextSize(15);
            label.setTextColor(COLOR_TEXT);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            textBlock.addView(label, matchWrap());

            TextView packageName = new TextView(context);
            packageName.setTextSize(12);
            packageName.setTextColor(COLOR_TEXT_DIM);
            packageName.setSingleLine(true);
            packageName.setEllipsize(TextUtils.TruncateAt.END);
            textBlock.addView(packageName, matchWrap());

            TextView selected = new TextView(context);
            selected.setText("Selected");
            selected.setTextSize(11);
            selected.setTypeface(Typeface.DEFAULT_BOLD);
            selected.setTextColor(COLOR_PRIMARY);
            selected.setGravity(Gravity.CENTER);
            selected.setPadding(dp(context, 8), 0, 0, 0);
            root.addView(selected, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            return new RowViews(root, icon, label, packageName, selected);
        }
    }

    private static final class RowViews {
        final LinearLayout root;
        final ImageView icon;
        final TextView label;
        final TextView packageName;
        final TextView selected;

        RowViews(LinearLayout root, ImageView icon, TextView label, TextView packageName, TextView selected) {
            this.root = root;
            this.icon = icon;
            this.label = label;
            this.packageName = packageName;
            this.selected = selected;
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
