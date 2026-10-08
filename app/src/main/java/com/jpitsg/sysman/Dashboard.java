package com.jpitsg.sysman;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.LinearGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The overview a user lands on: the app mark and theme switch, a status hero
 * with a dial and three vital signs, an optional update banner, and every
 * feature as a tile grouped by purpose. It only draws; the activity owns the
 * state and pushes it in through the setters.
 */
final class Dashboard {
    interface Listener {
        void onOpenPage(String id);

        void onToggleTheme();

        void onOpenUpdate();
    }

    private static final class Section {
        final TextView count;
        final TileGrid grid;
        final List<String> ids = new ArrayList<>();

        Section(TextView count, TileGrid grid) {
            this.count = count;
            this.grid = grid;
        }
    }

    private final Context context;
    private final Listener listener;
    private final ContentColumn column;
    private final LinearLayout topBar;
    private final ImageView themeIcon;
    private final View themeButton;
    private final TextView subtitleView;
    private final LinearLayout hero;
    private final StatusRingView ring;
    private final TextView headlineView;
    private final TextView sublineView;
    private final LinearLayout updateBanner;
    private final TextView updateDetail;
    private final Vital battery;
    private final Vital wifi;
    private final Vital link;
    private final LinearLayout sectionHost;
    private final Map<String, FeatureTile> tiles = new LinkedHashMap<>();
    private final Map<String, Section> sections = new LinkedHashMap<>();
    private final List<String> ringIds = new ArrayList<>();
    private final Runnable heroRefresh = new Runnable() {
        @Override
        public void run() {
            refreshHero();
        }
    };
    private String versionName = "";
    private String deviceName = "";

    /** One vital sign in the hero: an icon, a value and a caption. */
    private static final class Vital {
        final LinearLayout box;
        final ImageView icon;
        final TextView value;
        final TextView label;

        Vital(LinearLayout box, ImageView icon, TextView value, TextView label) {
            this.box = box;
            this.icon = icon;
            this.value = value;
            this.label = label;
        }
    }

    Dashboard(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        int page = Ui.dp(context, Ui.PAGE_PADDING);

        column = new ContentColumn(context);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        // ---- Top bar: mark, name, theme switch ------------------------------
        topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(page + Ui.dp(context, 2), Ui.dp(context, 10), page, Ui.dp(context, 4));
        column.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(context);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        topBar.addView(logo, new LinearLayout.LayoutParams(Ui.dp(context, 46), Ui.dp(context, 46)));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titlesLp.leftMargin = Ui.dp(context, 12);
        topBar.addView(titles, titlesLp);

        TextView appName = Ui.text(context, "System Manager", 20, Ui.COLOR_TEXT, true);
        appName.setTypeface(Ui.FONT_BOLD);
        appName.setSingleLine(true);
        titles.addView(appName);
        subtitleView = Ui.text(context, "", 12.5f, Ui.COLOR_TEXT_DIM, false);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(context, 3);
        titles.addView(subtitleView, subLp);

        FrameLayout themeFrame = new FrameLayout(context);
        themeFrame.setBackground(Ui.ripple(Ui.COLOR_RIPPLE_LIGHT,
                Ui.roundedFill(Ui.COLOR_SURFACE, Ui.dp(context, 22), Ui.dp(context, 1), Ui.COLOR_BORDER),
                Ui.dp(context, 22)));
        Ui.elevate(themeFrame, 1f);
        themeFrame.setClickable(true);
        themeFrame.setFocusable(true);
        themeIcon = Ui.icon(context, Ui.dark ? R.drawable.ic_sun : R.drawable.ic_moon, 22, Ui.COLOR_TEXT);
        themeFrame.addView(themeIcon, new FrameLayout.LayoutParams(
                Ui.dp(context, 22), Ui.dp(context, 22), Gravity.CENTER));
        themeFrame.setContentDescription(Ui.dark ? "Switch to the light theme" : "Switch to the dark theme");
        themeFrame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dashboard.this.listener.onToggleTheme();
            }
        });
        themeButton = themeFrame;
        topBar.addView(themeFrame, new LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)));

        // ---- Hero ------------------------------------------------------------
        hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(new HeroDrawable(context));
        hero.setPadding(Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 16));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroLp.setMargins(page, Ui.dp(context, 14), page, 0);
        column.addView(hero, heroLp);
        Ui.elevate(hero, 4f);

        LinearLayout heroTop = new LinearLayout(context);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(heroTop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ring = new StatusRingView(context);
        heroTop.addView(ring, new LinearLayout.LayoutParams(Ui.dp(context, 116), Ui.dp(context, 116)));

        LinearLayout heroText = new LinearLayout(context);
        heroText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams heroTextLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        heroTextLp.leftMargin = Ui.dp(context, 18);
        heroTop.addView(heroText, heroTextLp);

        TextView kicker = Ui.caps(context, "System status");
        kicker.setTextColor(0xB3FFFFFF);
        kicker.setTextSize(10.5f);
        heroText.addView(kicker);

        headlineView = Ui.text(context, "", 21, Color.WHITE, true);
        headlineView.setTypeface(Ui.FONT_BOLD);
        headlineView.setMaxLines(2);
        headlineView.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams headlineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headlineLp.topMargin = Ui.dp(context, 7);
        heroText.addView(headlineView, headlineLp);

        sublineView = Ui.text(context, "", 13, 0xCCFFFFFF, false);
        LinearLayout.LayoutParams sublineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sublineLp.topMargin = Ui.dp(context, 5);
        heroText.addView(sublineView, sublineLp);

        LinearLayout vitals = new LinearLayout(context);
        vitals.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams vitalsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vitalsLp.topMargin = Ui.dp(context, 16);
        hero.addView(vitals, vitalsLp);
        battery = addVital(vitals, R.drawable.ic_battery, "—", "Battery");
        wifi = addVital(vitals, R.drawable.ic_wifi, "—", "Network");
        link = addVital(vitals, R.drawable.ic_activity, "—", "Link");

        ring.setColors(0xFFFFFFFF, 0xFFFFC857, 0x4DFFFFFF, Color.WHITE, 0xB3FFFFFF);

        // ---- Update banner ---------------------------------------------------
        updateBanner = new LinearLayout(context);
        updateBanner.setOrientation(LinearLayout.HORIZONTAL);
        updateBanner.setGravity(Gravity.CENTER_VERTICAL);
        updateBanner.setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12));
        int blue = Ui.accentFg(Ui.ACCENT_BLUE);
        int blueBg = Ui.accentContainer(Ui.ACCENT_BLUE);
        updateBanner.setBackground(Ui.ripple(Ui.alpha(blue, 0.14f),
                Ui.roundedFill(blueBg, Ui.dp(context, 20), Ui.dp(context, 1), Ui.alpha(blue, 0.35f)),
                Ui.dp(context, 20)));
        updateBanner.setClickable(true);
        updateBanner.setFocusable(true);
        updateBanner.setVisibility(View.GONE);
        updateBanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dashboard.this.listener.onOpenUpdate();
            }
        });
        LinearLayout updateChip = Ui.iconChip(context, R.drawable.ic_download, 40, 22, blue, Ui.dark ? 0xFF0B1B3F : 0xFFFFFFFF);
        updateBanner.addView(updateChip);
        LinearLayout updateText = new LinearLayout(context);
        updateText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams updateTextLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        updateTextLp.leftMargin = Ui.dp(context, 12);
        updateBanner.addView(updateText, updateTextLp);
        TextView updateTitle = Ui.text(context, "Update available", 15, Ui.COLOR_TEXT, true);
        updateText.addView(updateTitle);
        updateDetail = Ui.text(context, "", 12.5f, Ui.COLOR_TEXT_DIM, false);
        LinearLayout.LayoutParams updateDetailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        updateDetailLp.topMargin = Ui.dp(context, 3);
        updateText.addView(updateDetail, updateDetailLp);
        updateBanner.addView(Ui.icon(context, R.drawable.ic_chevron_right, 22, blue));
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerLp.setMargins(page, Ui.dp(context, 14), page, 0);
        column.addView(updateBanner, bannerLp);

        sectionHost = new LinearLayout(context);
        sectionHost.setOrientation(LinearLayout.VERTICAL);
        sectionHost.setPadding(page, 0, page, 0);
        sectionHost.setClipChildren(false);
        sectionHost.setClipToPadding(false);
        column.addView(sectionHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.setPadding(0, 0, 0, Ui.dp(context, 28));
    }

    private Vital addVital(LinearLayout parent, int iconRes, String value, String label) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(context, 12), Ui.dp(context, 11), Ui.dp(context, 10), Ui.dp(context, 11));
        box.setBackground(Ui.roundedFill(0x24FFFFFF, Ui.dp(context, 16), Ui.dp(context, 1), 0x1FFFFFFF));

        // The value takes the full width of the chip, so large text sizes shorten it
        // only as a last resort; the icon and caption sit beneath.
        TextView valueView = Ui.text(context, value, 16.5f, Color.WHITE, true);
        valueView.setTypeface(Ui.FONT_BOLD);
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lineLp.topMargin = Ui.dp(context, 6);
        box.addView(line, lineLp);
        ImageView icon = Ui.icon(context, iconRes, 14, 0xCCFFFFFF);
        line.addView(icon, new LinearLayout.LayoutParams(Ui.dp(context, 14), Ui.dp(context, 14)));
        TextView labelView = Ui.text(context, label, 11, 0xB3FFFFFF, false);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.leftMargin = Ui.dp(context, 5);
        line.addView(labelView, labelLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (parent.getChildCount() > 0) {
            lp.leftMargin = Ui.dp(context, 8);
        }
        parent.addView(box, lp);
        return new Vital(box, icon, valueView, labelView);
    }

    View view() {
        return column;
    }

    /** Applies the system-bar insets to the top bar and the bottom of the column. */
    void setInsets(int left, int top, int right, int bottom) {
        int page = Ui.dp(context, Ui.PAGE_PADDING);
        topBar.setPadding(page + Ui.dp(context, 2) + left, Ui.dp(context, 10) + top,
                page + right, Ui.dp(context, 4));
        sectionHost.setPadding(page + left, 0, page + right, 0);
        ((LinearLayout.LayoutParams) hero.getLayoutParams()).setMargins(
                page + left, Ui.dp(context, 14), page + right, 0);
        ((LinearLayout.LayoutParams) updateBanner.getLayoutParams()).setMargins(
                page + left, Ui.dp(context, 14), page + right, 0);
        column.setPadding(0, 0, 0, Ui.dp(context, 28) + bottom);
        column.requestLayout();
    }

    // ---- Tiles -----------------------------------------------------------------

    /**
     * Adds a tile under {@code group} (created on first use). {@code inDial}
     * decides whether the feature gets an arc in the hero dial.
     */
    FeatureTile addTile(final String id, String group, String title, int iconRes, int accent, boolean inDial) {
        Section section = sections.get(group);
        if (section == null) {
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 4), 0);
            header.addView(Ui.caps(context, group), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView count = Ui.text(context, "", 11.5f, Ui.COLOR_TEXT_FAINT, true);
            header.addView(count);
            LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            headerLp.topMargin = Ui.dp(context, 26);
            headerLp.bottomMargin = Ui.dp(context, 12);
            sectionHost.addView(header, headerLp);

            TileGrid grid = new TileGrid(context, 150, Ui.GAP);
            grid.setClipChildren(false);
            sectionHost.addView(grid, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            section = new Section(count, grid);
            sections.put(group, section);
        }
        FeatureTile tile = new FeatureTile(context, title, iconRes, accent);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onOpenPage(id);
            }
        });
        section.grid.addView(tile);
        section.ids.add(id);
        tiles.put(id, tile);
        if (inDial) {
            ringIds.add(id);
        }
        return tile;
    }

    FeatureTile tile(String id) {
        return tiles.get(id);
    }

    void setTileVisible(String id, boolean visible) {
        FeatureTile tile = tiles.get(id);
        if (tile != null) {
            tile.setVisibility(visible ? View.VISIBLE : View.GONE);
            scheduleHeroRefresh();
        }
    }

    /** Coalesces the many small state changes of one refresh into a single hero update. */
    void scheduleHeroRefresh() {
        column.removeCallbacks(heroRefresh);
        column.post(heroRefresh);
    }

    private void refreshHero() {
        int n = ringIds.size();
        int[] states = new int[n];
        int on = 0;
        int attention = 0;
        for (int i = 0; i < n; i++) {
            FeatureTile tile = tiles.get(ringIds.get(i));
            int tone = tile == null ? Ui.TONE_NEUTRAL : tile.tone();
            if (tone == Ui.TONE_OK) {
                states[i] = StatusRingView.STATE_ON;
                on++;
            } else if (tone == Ui.TONE_BAD) {
                states[i] = StatusRingView.STATE_ATTENTION;
                attention++;
            } else {
                states[i] = StatusRingView.STATE_OFF;
            }
        }
        ring.setStates(states);
        ring.setCenter(Integer.toString(on), "on");
        String headline;
        if (attention > 0) {
            headline = attention == 1 ? "1 item needs attention" : attention + " items need attention";
        } else if (on == 0) {
            headline = "Nothing is running";
        } else {
            headline = "All systems normal";
        }
        headlineView.setText(headline);
        int off = n - on - attention;
        String counts = on + " on · " + off + " off" + (attention > 0 ? " · " + attention + " need attention" : "");
        sublineView.setText(counts);
        ring.setContentDescription(headline + ". " + counts + ".");

        for (Section section : sections.values()) {
            int visible = 0;
            int running = 0;
            for (String id : section.ids) {
                FeatureTile tile = tiles.get(id);
                if (tile.getVisibility() == View.GONE) {
                    continue;
                }
                visible++;
                if (tile.tone() == Ui.TONE_OK) {
                    running++;
                }
            }
            section.count.setText(running + " of " + visible + " on");
        }
    }

    // ---- Header and hero state ---------------------------------------------------

    void setIdentity(String version, String device) {
        versionName = version == null ? "" : version;
        deviceName = device == null ? "" : device;
        StringBuilder text = new StringBuilder();
        if (!versionName.isEmpty()) {
            text.append("v").append(versionName);
        }
        if (!deviceName.isEmpty()) {
            if (text.length() > 0) {
                text.append("  ·  ");
            }
            text.append(deviceName);
        }
        subtitleView.setText(text);
    }

    void setBattery(int percent, boolean charging) {
        battery.icon.setImageResource(charging ? R.drawable.ic_battery_charging : R.drawable.ic_battery);
        battery.value.setText(percent < 0 ? "—" : percent + "%");
        battery.label.setText(charging ? "Charging" : "Battery");
    }

    /** The network the phone is on: what to call it, a caption and the icon that fits. */
    void setNetwork(int iconRes, String value, String caption) {
        wifi.icon.setImageResource(iconRes);
        wifi.value.setText(value);
        wifi.label.setText(caption);
    }

    void setLink(boolean enabled, boolean connected, double uptimePercent) {
        if (!enabled) {
            link.value.setText("Off");
            link.label.setText("Link");
        } else if (!connected) {
            link.value.setText("Offline");
            link.label.setText("Link");
        } else {
            link.value.setText(String.format(java.util.Locale.US, "%.2f%%", uptimePercent));
            link.label.setText("Uptime");
        }
    }

    /** {@code versionName} of null hides the banner. */
    void setUpdate(String availableVersion) {
        if (availableVersion == null) {
            updateBanner.setVisibility(View.GONE);
            return;
        }
        updateDetail.setText("Version " + availableVersion + " is ready to install");
        updateBanner.setVisibility(View.VISIBLE);
    }

    /** A friendly device name, falling back to the hardware model. */
    static String deviceName(Context context) {
        String name = null;
        try {
            name = Settings.Global.getString(context.getContentResolver(), "device_name");
        } catch (RuntimeException ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            name = Build.MODEL;
        }
        return name == null ? "" : name.trim();
    }

    /** The hero's backdrop: a deep gradient with soft light and faint rings, like a radar screen. */
    private static final class HeroDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rings = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF bounds = new RectF();
        private final float corner;
        private final float density;

        HeroDrawable(Context context) {
            density = context.getResources().getDisplayMetrics().density;
            corner = 28f * density;
            rings.setStyle(Paint.Style.STROKE);
            rings.setStrokeWidth(1.2f * density);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1f * density);
            edge.setColor(0x24FFFFFF);
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect r) {
            bounds.set(r);
            clip.reset();
            clip.addRoundRect(bounds, corner, corner, Path.Direction.CW);
            fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    Ui.COLOR_HERO_START, Ui.COLOR_HERO_END, Shader.TileMode.CLAMP));
            float gr = Math.max(r.width(), r.height()) * 0.62f;
            glow.setShader(new RadialGradient(r.right - r.width() * 0.12f, r.top + r.height() * 0.08f, gr,
                    new int[]{Ui.dark ? 0x553FD6A0 : 0x3DFFFFFF, Ui.dark ? 0x003FD6A0 : 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawPath(clip, fill);
            canvas.save();
            canvas.clipPath(clip);
            canvas.drawRect(bounds, glow);
            // Concentric rings centred behind the dial.
            float cx = bounds.left + 18f * density + 58f * density;
            float cy = bounds.top + 18f * density + 58f * density;
            for (int i = 0; i < 4; i++) {
                float radius = (74f + i * 34f) * density;
                rings.setColor(Color.argb(Math.max(0, 30 - i * 7), 255, 255, 255));
                canvas.drawCircle(cx, cy, radius, rings);
            }
            canvas.restore();
            canvas.drawRoundRect(bounds.left + density / 2f, bounds.top + density / 2f,
                    bounds.right - density / 2f, bounds.bottom - density / 2f, corner, corner, edge);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void getOutline(android.graphics.Outline outline) {
            outline.setRoundRect(getBounds(), corner);
        }
    }
}
