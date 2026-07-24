package com.jpitsg.sysman;

import android.app.Dialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class TonePickerDialog {
    interface Listener {
        void onToneSelected(String title, String typeLabel);
    }

    private static final int COLOR_SURFACE = Ui.COLOR_SURFACE;
    private static final int COLOR_BORDER = Ui.COLOR_BORDER;
    private static final int COLOR_FIELD_BG = Ui.COLOR_FIELD_BG;
    private static final int COLOR_FIELD_BORDER = Ui.COLOR_FIELD_BORDER;
    private static final int COLOR_TEXT = Ui.COLOR_TEXT;
    private static final int COLOR_TEXT_DIM = Ui.COLOR_TEXT_DIM;
    private static final int COLOR_PRIMARY = Ui.COLOR_PRIMARY;
    private static final int COLOR_PRIMARY_CONTAINER = Ui.COLOR_PRIMARY_CONTAINER;
    private static final int COLOR_PRIMARY_ON_CONTAINER = Ui.COLOR_PRIMARY_ON_CONTAINER;
    private static final Object CACHE_LOCK = new Object();
    private static List<ToneEntry> cachedTones;

    private TonePickerDialog() {
    }

    static void show(Context context, String currentTitle, Listener listener) {
        PickerDialog dialog = new PickerDialog(context, currentTitle, listener);
        dialog.show();
    }

    private static final class PickerDialog extends Dialog {
        private final String currentTitle;
        private final Listener listener;
        private ToneAdapter adapter;
        private Ringtone previewRingtone;
        private String previewUri;

        PickerDialog(Context context, String currentTitle, Listener listener) {
            super(context);
            this.currentTitle = currentTitle == null ? "" : currentTitle.trim();
            this.listener = listener;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            LinearLayout root = new LinearLayout(getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(getContext(), 18), dp(getContext(), 16), dp(getContext(), 18), dp(getContext(), 18));
            root.setBackground(roundedFill(COLOR_SURFACE, dp(getContext(), Ui.DIALOG_CORNER), dp(getContext(), 1), COLOR_BORDER));

            LinearLayout header = new LinearLayout(getContext());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(header, matchWrap());

            adapter = new ToneAdapter(getContext(), new ArrayList<ToneEntry>());

            final EditText search = new EditText(getContext());
            search.setSingleLine(true);
            search.setHint("Search tones");
            search.setTextSize(15);
            search.setTextColor(COLOR_TEXT);
            search.setHintTextColor(Ui.COLOR_TEXT_FAINT);
            search.setBackground(roundedFill(COLOR_FIELD_BG, dp(getContext(), Ui.FIELD_CORNER), dp(getContext(), 1), COLOR_FIELD_BORDER));
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
            loadingText.setText("Loading tones");
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
            empty.setText("No tones found");
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
                    ToneEntry entry = adapter.getItem(position);
                    if (entry.uri.equals(previewUri)) {
                        stopPreview();
                        if (listener != null) {
                            listener.onToneSelected(entry.title, entry.typeLabel);
                        }
                        dismiss();
                        return;
                    }
                    playPreview(entry);
                }
            });

            setContentView(root);
            loadTonesAsync(getContext().getApplicationContext(), currentTitle, new LoadListener() {
                @Override
                public void onLoaded(List<ToneEntry> entries) {
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
            int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * Ui.DIALOG_WIDTH_FRACTION);
            int height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.86f);
            window.setLayout(width, height);
        }

        @Override
        public void dismiss() {
            stopPreview();
            super.dismiss();
        }

        @Override
        protected void onStop() {
            stopPreview();
            super.onStop();
        }

        private void playPreview(ToneEntry entry) {
            stopPreview();
            try {
                Ringtone ringtone = RingtoneManager.getRingtone(getContext(), Uri.parse(entry.uri));
                if (ringtone == null) {
                    return;
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
                ringtone.play();
                previewRingtone = ringtone;
                previewUri = entry.uri;
                adapter.setPreviewUri(previewUri);
            } catch (RuntimeException ignored) {
                previewUri = null;
                adapter.setPreviewUri(null);
            }
        }

        private void stopPreview() {
            if (previewRingtone != null) {
                try {
                    previewRingtone.stop();
                } catch (RuntimeException ignored) {
                }
                previewRingtone = null;
            }
            if (previewUri != null) {
                previewUri = null;
                if (adapter != null) {
                    adapter.setPreviewUri(null);
                }
            }
        }
    }

    private interface LoadListener {
        void onLoaded(List<ToneEntry> entries);
    }

    private static void loadTonesAsync(final Context context, final String currentTitle, final LoadListener listener) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<ToneEntry> entries = loadTones(context, currentTitle);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onLoaded(entries);
                    }
                });
            }
        }, "SystemManagerTonePicker").start();
    }

    private static List<ToneEntry> loadTones(Context context, String currentTitle) {
        List<ToneEntry> base;
        synchronized (CACHE_LOCK) {
            base = cachedTones;
        }
        if (base == null) {
            base = loadAvailableTones(context);
            synchronized (CACHE_LOCK) {
                if (cachedTones == null) {
                    cachedTones = base;
                } else {
                    base = cachedTones;
                }
            }
        }
        List<ToneEntry> entries = new ArrayList<>();
        for (ToneEntry entry : base) {
            boolean selected = currentTitle != null
                    && !currentTitle.isEmpty()
                    && currentTitle.trim().equalsIgnoreCase(entry.title);
            entries.add(new ToneEntry(entry.title, entry.typeLabel, entry.typeAbbrev, entry.uri, entry.typeOrder, selected));
        }
        sortTones(entries);
        return entries;
    }

    private static List<ToneEntry> loadAvailableTones(Context context) {
        List<ToneEntry> entries = new ArrayList<>();
        Set<String> seenUris = new HashSet<>();
        loadType(context, entries, seenUris, RingtoneManager.TYPE_ALARM, "Alarm", "AL", 0);
        loadType(context, entries, seenUris, RingtoneManager.TYPE_RINGTONE, "Ringtone", "RT", 1);
        loadType(context, entries, seenUris, RingtoneManager.TYPE_NOTIFICATION, "Notification", "NT", 2);
        sortTones(entries);
        return entries;
    }

    private static void loadType(Context context, List<ToneEntry> entries, Set<String> seenUris, int type,
                                 String typeLabel, String typeAbbrev, int typeOrder) {
        RingtoneManager manager = new RingtoneManager(context);
        manager.setType(type);
        Cursor cursor = null;
        try {
            cursor = manager.getCursor();
            if (cursor == null) {
                return;
            }
            for (int i = 0; i < cursor.getCount(); i++) {
                if (!cursor.moveToPosition(i)) {
                    continue;
                }
                String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                Uri uri = manager.getRingtoneUri(i);
                if (title == null || title.trim().isEmpty() || uri == null) {
                    continue;
                }
                String uriText = uri.toString();
                if (!seenUris.add(uriText)) {
                    continue;
                }
                entries.add(new ToneEntry(title.trim(), typeLabel, typeAbbrev, uriText, typeOrder, false));
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static void sortTones(List<ToneEntry> entries) {
        Collections.sort(entries, new Comparator<ToneEntry>() {
            @Override
            public int compare(ToneEntry left, ToneEntry right) {
                if (left.selected != right.selected) {
                    return left.selected ? -1 : 1;
                }
                int titleCompare = left.title.toLowerCase(Locale.US).compareTo(right.title.toLowerCase(Locale.US));
                if (titleCompare != 0) {
                    return titleCompare;
                }
                int typeCompare = left.typeOrder - right.typeOrder;
                if (typeCompare != 0) {
                    return typeCompare;
                }
                return left.uri.compareTo(right.uri);
            }
        });
    }

    private static final class ToneEntry {
        final String title;
        final String typeLabel;
        final String typeAbbrev;
        final String uri;
        final int typeOrder;
        final boolean selected;

        ToneEntry(String title, String typeLabel, String typeAbbrev, String uri, int typeOrder, boolean selected) {
            this.title = title;
            this.typeLabel = typeLabel;
            this.typeAbbrev = typeAbbrev;
            this.uri = uri;
            this.typeOrder = typeOrder;
            this.selected = selected;
        }
    }

    private static final class ToneAdapter extends BaseAdapter {
        private final Context context;
        private final List<ToneEntry> all = new ArrayList<>();
        private final List<ToneEntry> visible = new ArrayList<>();
        private String currentQuery = "";
        private String previewUri;

        ToneAdapter(Context context, List<ToneEntry> entries) {
            this.context = context;
            setEntries(entries);
        }

        void setEntries(List<ToneEntry> entries) {
            all.clear();
            visible.clear();
            all.addAll(entries);
            filter(currentQuery);
        }

        void setPreviewUri(String uri) {
            previewUri = uri;
            notifyDataSetChanged();
        }

        void filter(String query) {
            currentQuery = query == null ? "" : query;
            String normalized = currentQuery.trim().toLowerCase(Locale.US);
            visible.clear();
            if (normalized.isEmpty()) {
                visible.addAll(all);
            } else {
                for (ToneEntry entry : all) {
                    if (entry.title.toLowerCase(Locale.US).contains(normalized)
                            || entry.typeLabel.toLowerCase(Locale.US).contains(normalized)) {
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
        public ToneEntry getItem(int position) {
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

            ToneEntry entry = getItem(position);
            row.badge.setText(entry.typeAbbrev);
            row.title.setText(entry.title);
            row.type.setText(entry.typeLabel);
            boolean previewing = entry.uri.equals(previewUri);
            row.selected.setText(previewing ? "Playing" : "Selected");
            row.selected.setVisibility((previewing || entry.selected) ? View.VISIBLE : View.GONE);
            return convertView;
        }

        private RowViews createRow() {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
            root.setMinimumHeight(dp(context, 68));

            TextView badge = new TextView(context);
            badge.setTextSize(12);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(COLOR_PRIMARY_ON_CONTAINER);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(roundedFill(COLOR_PRIMARY_CONTAINER, dp(context, 999), 0, 0));
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(context, 42), dp(context, 42));
            badgeLp.setMargins(0, 0, dp(context, 12), 0);
            root.addView(badge, badgeLp);

            LinearLayout textBlock = new LinearLayout(context);
            textBlock.setOrientation(LinearLayout.VERTICAL);
            textBlock.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(textBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView title = new TextView(context);
            title.setTextSize(15);
            title.setTextColor(COLOR_TEXT);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            textBlock.addView(title, matchWrap());

            TextView type = new TextView(context);
            type.setTextSize(12);
            type.setTextColor(COLOR_TEXT_DIM);
            type.setSingleLine(true);
            type.setEllipsize(TextUtils.TruncateAt.END);
            textBlock.addView(type, matchWrap());

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

            return new RowViews(root, badge, title, type, selected);
        }
    }

    private static final class RowViews {
        final LinearLayout root;
        final TextView badge;
        final TextView title;
        final TextView type;
        final TextView selected;

        RowViews(LinearLayout root, TextView badge, TextView title, TextView type, TextView selected) {
            this.root = root;
            this.badge = badge;
            this.title = title;
            this.type = type;
            this.selected = selected;
        }
    }

    private static GradientDrawable roundedFill(int color, int cornerPx, int strokePx, int strokeColor) {
        return Ui.roundedFill(color, cornerPx, strokePx, strokeColor);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return Ui.matchWrap();
    }

    private static int dp(Context context, int value) {
        return Ui.dp(context, value);
    }
}
