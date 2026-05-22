package com.jpitsg.sysman;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_COMMON_PERMISSIONS = 10;
    private static final int REQUEST_BACKGROUND_LOCATION = 11;
    private static final int REQUEST_NOTIFICATIONS = 12;
    private static final int REQUEST_EXPORT_SETTINGS = 20;
    private static final int REQUEST_IMPORT_SETTINGS = 21;

    /** Single spacing unit used for every margin and every padding in the UI. */
    private static final int GAP = 12;

    private static final int CARD_CORNER = 22;
    private static final int FIELD_CORNER = 14;
    private static final int BUTTON_CORNER = 14;
    private static final int GROUP_CORNER = 16;
    private static final int PILL_CORNER = 999;
    private static final int FIELD_MIN_HEIGHT = 48;
    private static final int BUTTON_MIN_HEIGHT = 48;
    private static final int TOGGLE_ROW_MIN_HEIGHT = 48;
    private static final int STATUS_ROW_MIN_HEIGHT = 36;
    private static final int PILL_WIDTH = 118;
    private static final int PANEL_ANIMATION_MS = 180;
    private static final int CHEVRON_SIZE = 24;
    private static final int STATUS_DOT_SIZE = 8;
    private static final int LOG_VISIBLE_LINES = 16;

    private static final int COLOR_BG = 0xFFF4F7F4;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_GROUPED = 0xFFFAFCFA;
    private static final int COLOR_BORDER = 0xFFE2E8E2;
    private static final int COLOR_PRIMARY = 0xFF1E6F4F;
    private static final int COLOR_PRIMARY_CONTAINER = 0xFFDDEEE5;
    private static final int COLOR_PRIMARY_ON_CONTAINER = 0xFF0D3E2C;
    private static final int COLOR_NEUTRAL_CONTAINER = 0xFFE9EDEA;
    private static final int COLOR_NEUTRAL_ON_CONTAINER = 0xFF15201C;
    private static final int COLOR_DANGER = 0xFFB94436;
    private static final int COLOR_DANGER_CONTAINER = 0xFFFCE5E0;
    private static final int COLOR_DANGER_ON_CONTAINER = 0xFF7E2B23;
    private static final int COLOR_FIELD_BG = 0xFFF2F5F2;
    private static final int COLOR_FIELD_BORDER = 0xFFDDE4DE;
    private static final int COLOR_TEXT = 0xFF131D1A;
    private static final int COLOR_TEXT_DIM = 0xFF5D6E68;
    private static final int COLOR_TEXT_FAINT = 0xFF8C9A95;
    private static final int COLOR_LABEL = 0xFF6A7770;
    private static final int COLOR_OK = 0xFF1E6F4F;
    private static final int COLOR_BAD = 0xFFB94436;
    private static final int COLOR_LOG_BG = 0xFF14201D;
    private static final int COLOR_LOG_FG = 0xFFBFE0CB;
    private static final int COLOR_RIPPLE_DARK = 0x33FFFFFF;
    private static final int COLOR_RIPPLE_LIGHT = 0x22000000;
    private static final int COLOR_SWITCH_TRACK_OFF = 0xFFCED4D2;
    private static final int COLOR_SWITCH_TRACK_ON = 0x991E6F4F;

    private LinearLayout statusContainer;
    private TextView statusPill;
    private TextView highPriorityPill;
    private TextView batteryAlertPill;
    private TextView rebootPill;
    private TextView remoteLinkPill;
    private TextView permissionsPill;
    private TextView logPill;
    private TextView notificationHistoryPill;
    private TextView settingsBackupPill;
    private TextView wifiBadge;
    private TextView wifiSummary;
    private TextView wifiMonitorWarning;
    private LinearLayout notificationHistoryList;
    private TextView logView;
    private Button startTrackingButton;
    private Button stopTrackingButton;
    private float logTouchStartY;
    private int logTouchStartScrollY;

    private EditText serverBaseUrlField;
    private EditText trackPathField;
    private EditText ssidPatternField;
    private EditText highBatteryIntervalField;
    private EditText lowBatteryIntervalField;
    private EditText batteryThresholdField;
    private EditText locationTimeoutField;
    private EditText desiredAccuracyField;
    private EditText maxCachedLocationField;
    private EditText httpTimeoutField;
    private EditText fallbackLatitudeField;
    private EditText fallbackLongitudeField;
    private EditText logMaxLinesField;
    private EditText highPriorityPackageField;
    private EditText highPriorityTextFilterField;
    private EditText highPriorityRemoteTextFilterField;
    private EditText highPriorityRemoteDedupeSecondsField;
    private EditText highPriorityToneTitleField;
    private EditText highPriorityPlaySecondsField;
    private EditText highPriorityDedupeSecondsField;
    private EditText highPriorityAlarmVolumePercentField;
    private EditText batteryAlertThresholdField;
    private EditText batteryAlertCheckIntervalField;
    private EditText batteryAlertVibrateSecondsField;
    private EditText rebootTriggerPackageField;
    private EditText rebootTriggerTitleField;
    private EditText rebootTriggerTextField;
    private EditText rebootScheduleHourField;
    private EditText rebootScheduleMinuteField;
    private EditText rebootWifiPatternField;
    private EditText rebootPinSequenceField;
    private EditText rebootDelayedTestSecondsField;
    private EditText rebootPowerDialogWaitMsField;
    private EditText rebootStepWaitMsField;
    private EditText remoteLinkEndpointField;
    private EditText remoteLinkUsernameField;
    private EditText remoteLinkPasswordField;
    private EditText remoteLinkHeartbeatSecondsField;

    private Switch useExactAlarmsSwitch;
    private Switch allowIdleAlarmsSwitch;
    private Switch postOnStartupSwitch;
    private Switch postOnWifiChangeSwitch;
    private Switch showWifiMonitorNotificationSwitch;
    private Switch useGpsProviderSwitch;
    private Switch useNetworkProviderSwitch;
    private Switch requestGpsOnSsidMismatchSwitch;
    private Switch useFallbackOnSsidMatchSwitch;
    private Switch useCachedBeforeFreshSwitch;
    private Switch includeExtendedFieldsSwitch;
    private Switch caseSensitiveSsidSwitch;
    private Switch highPriorityEnabledSwitch;
    private Switch highPriorityRemoteEnabledSwitch;
    private Switch highPriorityRaiseAlarmVolumeSwitch;
    private Switch batteryAlertEnabledSwitch;
    private Switch batteryAlertUseExactAlarmsSwitch;
    private Switch batteryAlertAllowIdleAlarmsSwitch;
    private Switch rebootAutomationEnabledSwitch;
    private Switch rebootNotificationTriggerEnabledSwitch;
    private Switch rebootRemoteTriggerEnabledSwitch;
    private Switch rebootScheduleEnabledSwitch;
    private Switch rebootOnlyWhenWifiNotMatchingSwitch;
    private Switch remoteLinkEnabledSwitch;
    private Switch remoteLinkAcceptAnySslCertSwitch;
    private Switch remoteLinkShowNotificationSwitch;
    private Switch logEnabledSwitch;
    private final List<Panel> panels = new ArrayList<>();
    private BroadcastReceiver remoteLinkStateReceiver;
    private BroadcastReceiver notificationHistoryReceiver;
    private boolean loadingConfig;

    private static final class Panel {
        final LinearLayout content;
        final TextView pill;
        final TextView indicator;

        Panel(LinearLayout content, TextView pill, TextView indicator) {
            this.content = content;
            this.pill = pill;
            this.indicator = indicator;
        }
    }

    private static final class AspectImageView extends ImageView {
        private final Path clipPath = new Path();
        private final RectF clipRect = new RectF();
        private final float clipRadius;

        AspectImageView(Context context) {
            super(context);
            clipRadius = Math.round(12 * context.getResources().getDisplayMetrics().density);
            setAdjustViewBounds(true);
            setScaleType(ScaleType.FIT_CENTER);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            clipRect.set(0, 0, getWidth(), getHeight());
            clipPath.reset();
            clipPath.addRoundRect(clipRect, clipRadius, clipRadius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            super.onDraw(canvas);
            canvas.restore();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            Drawable drawable = getDrawable();
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (drawable != null && width > 0) {
                int imageWidth = drawable.getIntrinsicWidth();
                int imageHeight = drawable.getIntrinsicHeight();
                int availableWidth = width - getPaddingLeft() - getPaddingRight();
                if (imageWidth > 0 && imageHeight > 0 && availableWidth > 0) {
                    int measuredHeight = Math.max(1, Math.round(availableWidth * (imageHeight / (float) imageWidth)))
                            + getPaddingTop() + getPaddingBottom();
                    setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(measuredHeight, heightMeasureSpec));
                    return;
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("System Manager");
        configureSystemBars();
        buildUi();
        loadConfigIntoFields();
        wireLiveSettings();
        refreshStatusAndLog();
        LogStore.append(this, "ui", "MainActivity opened");
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerRemoteLinkStateReceiver();
        registerNotificationHistoryReceiver();
        collapseAllPanels();
        refreshStatusAndLog();
    }

    @Override
    protected void onPause() {
        unregisterNotificationHistoryReceiver();
        unregisterRemoteLinkStateReceiver();
        super.onPause();
    }

    // ============================================================
    //  Spacing helpers
    // ============================================================

    /** Layout params for a vertical-stack child: GAP top margin if siblings exist, otherwise 0. */
    private LinearLayout.LayoutParams stack(LinearLayout parent) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (parent.getChildCount() > 0) {
            lp.setMargins(0, dp(GAP), 0, 0);
        }
        return lp;
    }

    /** Layout params for an in-row child: GAP start margin if siblings exist, otherwise 0. */
    private LinearLayout.LayoutParams inRow(LinearLayout row, int width, int height, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height, weight);
        if (row.getChildCount() > 0) {
            lp.setMargins(dp(GAP), 0, 0, 0);
        }
        return lp;
    }

    private LinearLayout newColumn() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout newRow() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    // ============================================================
    //  Top-level build
    // ============================================================

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);

        LinearLayout root = newColumn();
        root.setBackgroundColor(COLOR_BG);
        applyDynamicInsets(root);

        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        buildNotificationHistoryPanel(root);
        buildGpsLoggerPanel(root);
        buildRemoteLinkPanel(root);
        buildHighPriorityPanel(root);
        buildBatteryAlertPanel(root);
        buildRebootPanel(root);
        buildSettingsTransferPanel(root);
        buildPermissionsPanel(root);
        buildLogPanel(root);

        setContentView(scrollView);
        BatteryAlertManager.sync(this, "activity-open");
        RemoteLinkManager.sync(this, "activity-open");
    }

    private void buildGpsLoggerControls(LinearLayout panel) {
        LinearLayout row1 = newRow();
        panel.addView(row1, stack(panel));
        startTrackingButton = primaryButton("Start Tracking", action("start"));
        stopTrackingButton = neutralButton("Stop Tracking", action("stop"));
        addRowButton(row1, startTrackingButton);
        addRowButton(row1, stopTrackingButton);

        LinearLayout row2 = newRow();
        panel.addView(row2, stack(panel));
        addRowButton(row2, tonalButton("Send Now", action("send")));
        addRowButton(row2, tonalButton("Save GPS Settings", action("save_gps")));
    }

    // ============================================================
    //  Feature panels
    // ============================================================

    private void buildNotificationHistoryPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Notification History", true);
        notificationHistoryPill = frame.pill;

        notificationHistoryList = newColumn();
        notificationHistoryList.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        frame.content.addView(notificationHistoryList, stack(frame.content));

        LinearLayout buttons = newRow();
        frame.content.addView(buttons, stack(frame.content));
        addRowButton(buttons, tonalButton("Refresh", action("refresh_notification_history")));
        addRowButton(buttons, neutralButton("Clear", action("clear_notification_history")));
    }

    private void buildGpsLoggerPanel(LinearLayout root) {
        Panel panel = addExpandablePanel(root, "GPS Logger Settings", true);
        statusPill = panel.pill;
        buildEndpointSubsection(panel.content);
        buildWifiSubsection(panel.content);
        buildLocationSubsection(panel.content);
        buildSchedulingSubsection(panel.content);
        buildGpsLoggerControls(panel.content);
    }

    private void buildEndpointSubsection(LinearLayout panel) {
        addSubsectionLabel(panel, "Server");
        serverBaseUrlField = addField(panel, "Server base URL", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        trackPathField = addField(panel, "Track path", InputType.TYPE_CLASS_TEXT);
        httpTimeoutField = addField(panel, "HTTP timeout (seconds)", InputType.TYPE_CLASS_NUMBER);
        LinearLayout group = addToggleGroup(panel);
        includeExtendedFieldsSwitch = addGroupedToggle(group, "Include extended fields");
    }

    private void buildWifiSubsection(LinearLayout panel) {
        addSubsectionLabel(panel, "Wi-Fi Matching");
        ssidPatternField = addField(panel, "SSID pattern", InputType.TYPE_CLASS_TEXT);
        LinearLayout group = addToggleGroup(panel);
        caseSensitiveSsidSwitch = addGroupedToggle(group, "Case-sensitive SSID match");
        requestGpsOnSsidMismatchSwitch = addGroupedToggle(group, "Request GPS when SSID differs");
        useFallbackOnSsidMatchSwitch = addGroupedToggle(group, "Use fallback when SSID matches");
    }

    private void buildLocationSubsection(LinearLayout panel) {
        addSubsectionLabel(panel, "Location");
        highBatteryIntervalField = addField(panel, "Interval when battery ≥ threshold (minutes)", InputType.TYPE_CLASS_NUMBER);
        lowBatteryIntervalField = addField(panel, "Interval when battery < threshold (minutes)", InputType.TYPE_CLASS_NUMBER);
        batteryThresholdField = addField(panel, "Battery threshold (%)", InputType.TYPE_CLASS_NUMBER);
        locationTimeoutField = addField(panel, "Location timeout (seconds)", InputType.TYPE_CLASS_NUMBER);
        desiredAccuracyField = addField(panel, "Desired accuracy (meters)", InputType.TYPE_CLASS_NUMBER);
        maxCachedLocationField = addField(panel, "Max cached location (minutes)", InputType.TYPE_CLASS_NUMBER);
        fallbackLatitudeField = addField(panel, "Fallback latitude", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        fallbackLongitudeField = addField(panel, "Fallback longitude", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        LinearLayout group = addToggleGroup(panel);
        useGpsProviderSwitch = addGroupedToggle(group, "Use GPS provider");
        useNetworkProviderSwitch = addGroupedToggle(group, "Use network provider");
        useCachedBeforeFreshSwitch = addGroupedToggle(group, "Prefer accurate cached location");
    }

    private void buildSchedulingSubsection(LinearLayout panel) {
        addSubsectionLabel(panel, "Schedule");
        LinearLayout group = addToggleGroup(panel);
        useExactAlarmsSwitch = addGroupedToggle(group, "Use exact GPS and reboot alarms");
        allowIdleAlarmsSwitch = addGroupedToggle(group, "Allow GPS and reboot alarms while idle");
        postOnStartupSwitch = addGroupedToggle(group, "Send once after boot");
        postOnWifiChangeSwitch = addGroupedToggle(group, "Send when Wi-Fi changes");
        showWifiMonitorNotificationSwitch = addGroupedToggle(group, "Show Wi-Fi monitor notification");
    }

    private void buildHighPriorityPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "High Priority Alerts", true);
        highPriorityPill = frame.pill;

        addSubsectionLabel(frame.content, "Package Based Alerts");
        LinearLayout packageGroup = addToggleGroup(frame.content);
        highPriorityEnabledSwitch = addGroupedToggle(packageGroup, "Enable package-based alerts");
        highPriorityPackageField = addField(frame.content, "Notification app", InputType.TYPE_CLASS_TEXT);
        configureAppPickerField(highPriorityPackageField);
        highPriorityTextFilterField = addField(frame.content, "Text contains", InputType.TYPE_CLASS_TEXT);
        highPriorityDedupeSecondsField = addField(frame.content, "Duplicate window (seconds)", InputType.TYPE_CLASS_NUMBER);

        addSubsectionLabel(frame.content, "Remote Link Alerts");
        LinearLayout socketGroup = addToggleGroup(frame.content);
        highPriorityRemoteEnabledSwitch = addGroupedToggle(socketGroup, "Enable Remote Link alerts");
        highPriorityRemoteTextFilterField = addField(frame.content, "Text contains", InputType.TYPE_CLASS_TEXT);
        highPriorityRemoteDedupeSecondsField = addField(frame.content, "Duplicate window (seconds)", InputType.TYPE_CLASS_NUMBER);

        addSubsectionLabel(frame.content, "Tone & Volume");
        highPriorityToneTitleField = addField(frame.content, "Alarm tone", InputType.TYPE_CLASS_TEXT);
        configureTonePickerField(highPriorityToneTitleField);
        highPriorityPlaySecondsField = addField(frame.content, "Play duration (seconds)", InputType.TYPE_CLASS_NUMBER);
        highPriorityAlarmVolumePercentField = addField(frame.content, "Alarm volume (%)", InputType.TYPE_CLASS_NUMBER);
        LinearLayout volumeGroup = addToggleGroup(frame.content);
        highPriorityRaiseAlarmVolumeSwitch = addGroupedToggle(volumeGroup, "Temporarily raise alarm volume");

        LinearLayout row = newRow();
        frame.content.addView(row, stack(frame.content));
        addRowButton(row, tonalButton("Test Alert", action("test_high_priority_alert")));
        addRowButton(row, tonalButton("Notification Access", action("notification_access")));

        LinearLayout saveRow = newRow();
        frame.content.addView(saveRow, stack(frame.content));
        addRowButton(saveRow, tonalButton("Save High Priority Settings", action("save_high_priority")));
    }

    private void buildBatteryAlertPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Battery Alerts", true);
        batteryAlertPill = frame.pill;

        LinearLayout enableGroup = addToggleGroup(frame.content);
        batteryAlertEnabledSwitch = addGroupedToggle(enableGroup, "Enable battery alerts");

        addSubsectionLabel(frame.content, "Alert Settings");
        batteryAlertThresholdField = addField(frame.content, "Alert threshold (%)", InputType.TYPE_CLASS_NUMBER);
        batteryAlertCheckIntervalField = addField(frame.content, "Check interval (minutes)", InputType.TYPE_CLASS_NUMBER);
        batteryAlertVibrateSecondsField = addField(frame.content, "Vibration duration (seconds)", InputType.TYPE_CLASS_NUMBER);

        addSubsectionLabel(frame.content, "Schedule");
        LinearLayout scheduleGroup = addToggleGroup(frame.content);
        batteryAlertUseExactAlarmsSwitch = addGroupedToggle(scheduleGroup, "Use exact battery alert alarms");
        batteryAlertAllowIdleAlarmsSwitch = addGroupedToggle(scheduleGroup, "Allow battery alert alarms while idle");

        LinearLayout row = newRow();
        frame.content.addView(row, stack(frame.content));
        addRowButton(row, tonalButton("Test Battery Alert", action("test_battery_alert")));
        addRowButton(row, tonalButton("Notification Permission", action("notifications")));

        LinearLayout saveRow = newRow();
        frame.content.addView(saveRow, stack(frame.content));
        addRowButton(saveRow, tonalButton("Save Battery Alert Settings", action("save_battery_alert")));
    }

    private void buildRebootPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Reboot Automation", true);
        rebootPill = frame.pill;

        LinearLayout enableGroup = addToggleGroup(frame.content);
        rebootAutomationEnabledSwitch = addGroupedToggle(enableGroup, "Enable reboot automation");

        addSubsectionLabel(frame.content, "Notification Trigger");
        LinearLayout notifGroup = addToggleGroup(frame.content);
        rebootNotificationTriggerEnabledSwitch = addGroupedToggle(notifGroup, "Reboot from matching notification");
        rebootTriggerPackageField = addField(frame.content, "Notification app", InputType.TYPE_CLASS_TEXT);
        configureAppPickerField(rebootTriggerPackageField);
        rebootTriggerTitleField = addField(frame.content, "Title contains", InputType.TYPE_CLASS_TEXT);
        rebootTriggerTextField = addField(frame.content, "Text contains", InputType.TYPE_CLASS_TEXT);

        addSubsectionLabel(frame.content, "Remote Link Trigger");
        LinearLayout remoteGroup = addToggleGroup(frame.content);
        rebootRemoteTriggerEnabledSwitch = addGroupedToggle(remoteGroup, "Reboot from Remote Link");

        addSubsectionLabel(frame.content, "Daily Schedule");
        LinearLayout scheduleGroup = addToggleGroup(frame.content);
        rebootScheduleEnabledSwitch = addGroupedToggle(scheduleGroup, "Enable scheduled reboot");
        rebootScheduleHourField = addField(frame.content, "Hour (0-23)", InputType.TYPE_CLASS_NUMBER);
        rebootScheduleMinuteField = addField(frame.content, "Minute (0-59)", InputType.TYPE_CLASS_NUMBER);
        rebootWifiPatternField = addField(frame.content, "SSID pattern", InputType.TYPE_CLASS_TEXT);
        LinearLayout wifiMatchGroup = addToggleGroup(frame.content);
        rebootOnlyWhenWifiNotMatchingSwitch = addGroupedToggle(wifiMatchGroup, "Only reboot when SSID doesn't match");

        addSubsectionLabel(frame.content, "Automation");
        rebootPinSequenceField = addField(frame.content, "PIN sequence (optional)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        rebootDelayedTestSecondsField = addField(frame.content, "Delayed lock test delay (seconds)", InputType.TYPE_CLASS_NUMBER);
        rebootPowerDialogWaitMsField = addField(frame.content, "Power dialog wait (milliseconds)", InputType.TYPE_CLASS_NUMBER);
        rebootStepWaitMsField = addField(frame.content, "Step wait (milliseconds)", InputType.TYPE_CLASS_NUMBER);

        LinearLayout row1 = newRow();
        frame.content.addView(row1, stack(frame.content));
        addRowButton(row1, tonalButton("Test Now", action("test_reboot_now")));
        addRowButton(row1, tonalButton("Delayed Lock Test", action("test_reboot_delayed")));

        LinearLayout row2 = newRow();
        frame.content.addView(row2, stack(frame.content));
        addRowButton(row2, tonalButton("Accessibility", action("accessibility_settings")));
        addRowButton(row2, tonalButton("Save Reboot Settings", action("save_reboot")));
    }

    private void buildRemoteLinkPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Remote Link", true);
        remoteLinkPill = frame.pill;

        LinearLayout enableGroup = addToggleGroup(frame.content);
        remoteLinkEnabledSwitch = addGroupedToggle(enableGroup, "Enable Remote Link");
        remoteLinkAcceptAnySslCertSwitch = addGroupedToggle(enableGroup, "Accept any SSL cert");
        remoteLinkShowNotificationSwitch = addGroupedToggle(enableGroup, "Show Remote Link notification");

        addSubsectionLabel(frame.content, "Connection");
        remoteLinkEndpointField = addField(frame.content, "Remote Link endpoint", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        remoteLinkUsernameField = addField(frame.content, "Username", InputType.TYPE_CLASS_TEXT);
        remoteLinkPasswordField = addField(frame.content, "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        remoteLinkHeartbeatSecondsField = addField(frame.content, "Heartbeat (seconds)", InputType.TYPE_CLASS_NUMBER);

        LinearLayout row = newRow();
        frame.content.addView(row, stack(frame.content));
        addRowButton(row, tonalButton("Reconnect", action("remote_link_reconnect")));
        addRowButton(row, tonalButton("Ping", action("remote_link_ping")));

        LinearLayout saveRow = newRow();
        frame.content.addView(saveRow, stack(frame.content));
        addRowButton(saveRow, tonalButton("Save Remote Link", action("save_remote_link")));
    }

    private void buildSettingsTransferPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Settings Backup", true);
        settingsBackupPill = frame.pill;

        LinearLayout row = newRow();
        frame.content.addView(row, stack(frame.content));
        addRowButton(row, tonalButton("Export XML", action("export_settings")));
        addRowButton(row, tonalButton("Import XML", action("import_settings")));
    }

    private void buildPermissionsPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Permissions", true);
        permissionsPill = frame.pill;

        addSubsectionLabel(frame.content, "Readiness");
        statusContainer = newColumn();
        statusContainer.setBackground(roundedFill(COLOR_GROUPED, GROUP_CORNER, 1, COLOR_BORDER));
        statusContainer.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        frame.content.addView(statusContainer, stack(frame.content));

        addSubsectionLabel(frame.content, "Grant Access");
        LinearLayout r1 = newRow();
        frame.content.addView(r1, stack(frame.content));
        addRowButton(r1, tonalButton("Location & Wi-Fi", action("common_permissions")));
        addRowButton(r1, tonalButton("Background Location", action("background_location")));

        LinearLayout r2 = newRow();
        frame.content.addView(r2, stack(frame.content));
        addRowButton(r2, tonalButton("Exact Alarms", action("exact_alarm")));
        addRowButton(r2, tonalButton("Battery Settings", action("battery")));

        LinearLayout r3 = newRow();
        frame.content.addView(r3, stack(frame.content));
        addRowButton(r3, tonalButton("App Settings", action("app_settings")));
        addRowButton(r3, tonalButton("Location Settings", action("location_settings")));

        LinearLayout r4 = newRow();
        frame.content.addView(r4, stack(frame.content));
        addRowButton(r4, tonalButton("Notification Access", action("notification_access")));
        addRowButton(r4, tonalButton("Accessibility", action("accessibility_settings")));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            frame.content.addView(tonalButton("Notification Permission", action("notifications")), stack(frame.content));
        }
    }

    private void buildLogPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Log", true);
        logPill = frame.pill;

        LinearLayout enableGroup = addToggleGroup(frame.content);
        logEnabledSwitch = addGroupedToggle(enableGroup, "Enable logging");

        logMaxLinesField = addField(frame.content, "Retention (lines)", InputType.TYPE_CLASS_NUMBER);

        LinearLayout buttons = newRow();
        frame.content.addView(buttons, stack(frame.content));
        addRowButton(buttons, tonalButton("Refresh", action("refresh_log")));
        addRowButton(buttons, neutralButton("Clear", action("clear_log")));

        LinearLayout saveRow = newRow();
        frame.content.addView(saveRow, stack(frame.content));
        addRowButton(saveRow, tonalButton("Save Log Settings", action("save_log")));

        logView = new TextView(this);
        logView.setText("");
        logView.setTextColor(COLOR_LOG_FG);
        logView.setTextSize(11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setBackground(roundedFill(COLOR_LOG_BG, FIELD_CORNER, 0, 0));
        logView.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        logView.setLineSpacing(0, 1.3f);
        applyFixedTextLineCount(logView, LOG_VISIBLE_LINES);
        logView.setVerticalScrollBarEnabled(true);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                boolean canScroll = logViewCanScroll();
                if (!canScroll) {
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    logTouchStartY = event.getY();
                    logTouchStartScrollY = logView.getScrollY();
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    int targetScrollY = logTouchStartScrollY + Math.round(logTouchStartY - event.getY());
                    logView.scrollTo(0, clamp(targetScrollY, 0, logViewMaxScrollY()));
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                }
                return false;
            }
        });
        frame.content.addView(logView, stack(frame.content));
    }

    private void refreshNotificationHistory() {
        if (notificationHistoryPill == null) {
            return;
        }
        int count = NotificationHistoryStore.count(this);
        setPillState(
                notificationHistoryPill,
                Integer.toString(count),
                count > 0 ? COLOR_PRIMARY_CONTAINER : COLOR_NEUTRAL_CONTAINER,
                count > 0 ? COLOR_PRIMARY_ON_CONTAINER : COLOR_NEUTRAL_ON_CONTAINER);
        if (notificationHistoryList == null) {
            return;
        }

        notificationHistoryList.removeAllViews();
        List<NotificationHistoryStore.Entry> entries = NotificationHistoryStore.read(this, 30);
        if (entries.isEmpty()) {
            addHistoryEmptyRow("No notifications yet");
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                addHistorySeparator();
            }
            addHistoryEntry(entries.get(i));
        }
        if (count > entries.size()) {
            addHistorySeparator();
            addHistoryEmptyRow("Showing newest " + entries.size() + " of " + count);
        }
    }

    private void addHistoryEntry(NotificationHistoryStore.Entry entry) {
        LinearLayout item = newColumn();
        item.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));

        LinearLayout meta = newRow();
        item.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView source = historyText(entry.source.toUpperCase(Locale.US), 10, COLOR_PRIMARY, true);
        source.setLetterSpacing(0.12f);
        source.setSingleLine(true);
        source.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(source, inRow(meta, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView time = historyText(formatHistoryTime(entry.timestampMillis), 11, COLOR_TEXT_FAINT, false);
        time.setSingleLine(true);
        meta.addView(time, inRow(meta, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        TextView title = historyText(historyDisplayTitle(entry), 14, COLOR_TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(title, topMarginParams(6));

        String detail = historyDetail(entry);
        if (hasText(detail)) {
            TextView body = historyText(detail, 12, COLOR_TEXT_DIM, false);
            body.setMaxLines(3);
            body.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(body, topMarginParams(6));
        }

        Bitmap image = decodeHistoryImage(entry);
        if (image != null) {
            AspectImageView imageView = new AspectImageView(this);
            imageView.setImageBitmap(image);
            imageView.setBackground(roundedFill(COLOR_SURFACE, FIELD_CORNER, 1, COLOR_BORDER));
            item.addView(imageView, topMarginParams(8));
        }

        setHistorySwipeToClear(item, entry);
        notificationHistoryList.addView(item, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addHistoryEmptyRow(String text) {
        TextView empty = historyText(text, 13, COLOR_TEXT_DIM, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        notificationHistoryList.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addHistorySeparator() {
        View hairline = new View(this);
        hairline.setBackgroundColor(COLOR_FIELD_BORDER);
        notificationHistoryList.addView(hairline, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)));
    }

    private TextView historyText(String text, int sp, int color, boolean medium) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif-medium", medium ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    private LinearLayout.LayoutParams topMarginParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(topDp), 0, 0);
        return lp;
    }

    private String historyDisplayTitle(NotificationHistoryStore.Entry entry) {
        if (hasText(entry.title)) {
            return entry.title;
        }
        if (hasText(entry.message)) {
            return entry.message;
        }
        return "Notification";
    }

    private String historyDetail(NotificationHistoryStore.Entry entry) {
        StringBuilder detail = new StringBuilder();
        if (hasText(entry.title) && hasText(entry.message)) {
            detail.append(entry.message);
        }
        return detail.toString();
    }

    private Bitmap decodeHistoryImage(NotificationHistoryStore.Entry entry) {
        File file = NotificationHistoryStore.imageFile(this, entry);
        if (file == null) {
            return null;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        int targetWidth = Math.max(dp(160), getResources().getDisplayMetrics().widthPixels - dp(GAP * 6));
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, targetWidth);
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private int sampleSize(int width, int targetWidth) {
        int sample = 1;
        while ((width / (sample * 2)) >= targetWidth) {
            sample *= 2;
        }
        return sample;
    }

    private String formatHistoryTime(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return "Unknown";
        }
        return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date(timestampMillis));
    }

    private String formatBackupDate(long timestampMillis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(timestampMillis));
    }

    private void setHistorySwipeToClear(final View item, final NotificationHistoryStore.Entry entry) {
        final float[] startX = new float[1];
        final float[] startY = new float[1];
        final boolean[] swiping = new boolean[1];
        item.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View view, MotionEvent event) {
                float dx = event.getRawX() - startX[0];
                float dy = event.getRawY() - startY[0];
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX[0] = event.getRawX();
                    startY[0] = event.getRawY();
                    swiping[0] = false;
                    view.animate().cancel();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!swiping[0] && Math.abs(dx) > dp(10) && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                        swiping[0] = true;
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (swiping[0]) {
                        float maxTranslation = view.getWidth() * 0.65f;
                        float translation = Math.max(-maxTranslation, Math.min(dx, maxTranslation));
                        view.setTranslationX(translation);
                        view.setAlpha(1f - Math.min(0.45f, Math.abs(translation) / Math.max(1f, view.getWidth())));
                        return true;
                    }
                    if (Math.abs(dy) > dp(10) && Math.abs(dy) > Math.abs(dx)) {
                        return false;
                    }
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    if (swiping[0] && Math.abs(dx) > dp(96)) {
                        float targetX = dx > 0 ? view.getWidth() : -view.getWidth();
                        view.animate()
                                .translationX(targetX)
                                .alpha(0f)
                                .setDuration(150)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        NotificationHistoryStore.remove(MainActivity.this, entry);
                                        refreshNotificationHistory();
                                    }
                                })
                                .start();
                    } else {
                        view.animate().translationX(0f).alpha(1f).setDuration(140).start();
                    }
                    swiping[0] = false;
                    return true;
                }
                return false;
            }
        });
    }

    private void applyFixedTextLineCount(TextView view, int lineCount) {
        int lineHeight = Math.max(1, view.getLineHeight());
        int height = view.getCompoundPaddingTop()
                + view.getCompoundPaddingBottom()
                + (lineHeight * lineCount);
        view.setMinLines(lineCount);
        view.setMaxLines(lineCount);
        view.setMinHeight(height);
        view.setMaxHeight(height);
    }

    private boolean logViewCanScroll() {
        if (logView == null || logView.getLayout() == null) {
            return false;
        }
        int scrollableHeight = logView.getLayout().getHeight() + logView.getPaddingTop() + logView.getPaddingBottom();
        return scrollableHeight > logView.getHeight();
    }

    private int logViewMaxScrollY() {
        if (logView == null || logView.getLayout() == null) {
            return 0;
        }
        int contentHeight = logView.getLayout().getHeight() + logView.getPaddingTop() + logView.getPaddingBottom();
        return Math.max(0, contentHeight - logView.getHeight());
    }

    // ============================================================
    //  Panel / card helpers
    // ============================================================

    private Panel addExpandablePanel(LinearLayout root, String title, boolean hasPill) {
        LinearLayout card = newColumn();
        card.setBackground(roundedFill(COLOR_SURFACE, CARD_CORNER, 1, COLOR_BORDER));
        card.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        root.addView(card, stack(root));

        final LinearLayout headerRow = newRow();
        headerRow.setMinimumHeight(dp(STATUS_ROW_MIN_HEIGHT));
        card.addView(headerRow, stack(card));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(17);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleView.setTextColor(COLOR_TEXT);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setIncludeFontPadding(false);
        headerRow.addView(titleView, inRow(headerRow, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView panelPill = null;
        if (hasPill) {
            panelPill = pill("DISABLED", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
            headerRow.addView(panelPill, inRow(headerRow,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0f));
        }

        final TextView indicator = new TextView(this);
        indicator.setText("▾");
        indicator.setTextSize(14);
        indicator.setTypeface(Typeface.DEFAULT_BOLD);
        indicator.setTextColor(COLOR_TEXT_FAINT);
        indicator.setGravity(Gravity.CENTER);
        indicator.setIncludeFontPadding(false);
        indicator.setRotation(-90f);
        headerRow.addView(indicator, inRow(headerRow, dp(CHEVRON_SIZE), dp(CHEVRON_SIZE), 0f));

        final LinearLayout content = newColumn();
        content.setVisibility(View.GONE);
        card.addView(content, stack(card));

        headerRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean expanded = content.getVisibility() == View.VISIBLE;
                animatePanel(content, !expanded);
                indicator.animate().rotation(expanded ? -90f : 0f).setDuration(180).start();
            }
        });

        Panel panel = new Panel(content, panelPill, indicator);
        panels.add(panel);
        return panel;
    }

    private void collapseAllPanels() {
        for (Panel panel : panels) {
            panel.content.animate().cancel();
            panel.content.setVisibility(View.GONE);
            ViewGroup.LayoutParams lp = panel.content.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                panel.content.setLayoutParams(lp);
            }
            panel.indicator.animate().cancel();
            panel.indicator.setRotation(-90f);
        }
    }

    private void animatePanel(final View content, boolean expand) {
        content.animate().cancel();
        int startHeight = content.getVisibility() == View.VISIBLE ? content.getHeight() : 0;
        int endHeight;
        if (expand) {
            content.setVisibility(View.VISIBLE);
            content.measure(
                    View.MeasureSpec.makeMeasureSpec(((View) content.getParent()).getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            endHeight = content.getMeasuredHeight();
        } else {
            endHeight = 0;
        }

        final ViewGroup.LayoutParams lp = content.getLayoutParams();
        ValueAnimator animator = ValueAnimator.ofInt(startHeight, endHeight);
        animator.setDuration(PANEL_ANIMATION_MS);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                lp.height = (Integer) animation.getAnimatedValue();
                content.setLayoutParams(lp);
            }
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (expand) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    content.setLayoutParams(lp);
                } else {
                    content.setVisibility(View.GONE);
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    content.setLayoutParams(lp);
                }
            }
        });
        animator.start();
    }

    private TextView pill(String text, int bg, int fg) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(10);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLetterSpacing(0.12f);
        v.setTextColor(fg);
        v.setBackground(roundedFill(bg, PILL_CORNER, 0, 0));
        v.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        v.setGravity(Gravity.CENTER);
        v.setWidth(dp(PILL_WIDTH));
        v.setSingleLine(true);
        v.setIncludeFontPadding(false);
        return v;
    }

    private GradientDrawable roundedFill(int color, int cornerDp, int strokeDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(cornerDp));
        if (strokeDp > 0) {
            d.setStroke(dp(strokeDp), strokeColor);
        }
        return d;
    }

    // ============================================================
    //  Buttons
    // ============================================================

    private void addRowButton(LinearLayout row, Button button) {
        row.addView(button, inRow(row, 0, dp(BUTTON_MIN_HEIGHT), 1f));
    }

    private void applyButtonState(Button button, boolean enabled, int enabledBg, int enabledFg) {
        if (button == null) {
            return;
        }
        button.setEnabled(enabled);
        button.setTextColor(enabled ? enabledFg : COLOR_TEXT_FAINT);
        int bg = enabled ? enabledBg : COLOR_FIELD_BG;
        int ripple = enabled ? COLOR_RIPPLE_LIGHT : 0x00000000;
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(ripple),
                roundedFill(bg, BUTTON_CORNER, 0, 0),
                null));
    }

    private Button primaryButton(String text, View.OnClickListener listener) {
        return styledButton(text, listener, COLOR_PRIMARY, Color.WHITE, COLOR_RIPPLE_DARK);
    }

    private Button tonalButton(String text, View.OnClickListener listener) {
        return styledButton(text, listener, COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER, COLOR_RIPPLE_LIGHT);
    }

    private Button neutralButton(String text, View.OnClickListener listener) {
        return styledButton(text, listener, COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER, COLOR_RIPPLE_LIGHT);
    }

    private Button styledButton(String text, View.OnClickListener listener, int bg, int fg, int ripple) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setMinHeight(dp(BUTTON_MIN_HEIGHT));
        b.setMinimumHeight(dp(BUTTON_MIN_HEIGHT));
        b.setIncludeFontPadding(false);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        b.setBackground(new RippleDrawable(
                ColorStateList.valueOf(ripple),
                roundedFill(bg, BUTTON_CORNER, 0, 0),
                null));
        b.setOnClickListener(listener);
        return b;
    }

    // ============================================================
    //  Log colorization
    // ============================================================

    private SpannableStringBuilder colorizeLog(String logText) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (logText == null || logText.isEmpty()) {
            return builder;
        }
        String[] lines = logText.split("\\n", -1);
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            int lineStart = builder.length();
            builder.append(line).append('\n');
            int lineEnd = builder.length();
            builder.setSpan(new ForegroundColorSpan(COLOR_LOG_FG), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            int tagOpen = line.indexOf('[');
            int tagClose = line.indexOf(']', tagOpen + 1);
            if (line.length() >= 23) {
                int dateEnd = Math.min(lineStart + 23, lineStart + line.length());
                builder.setSpan(new ForegroundColorSpan(0xFF8FB5A0), lineStart, dateEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (tagOpen >= 0 && tagClose > tagOpen) {
                int tagColor = logTagColor(line.substring(tagOpen + 1, tagClose));
                builder.setSpan(new ForegroundColorSpan(tagColor), lineStart + tagOpen, lineStart + tagClose + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }

    private int logTagColor(String tag) {
        if ("network".equals(tag)) return 0xFF8EC5FF;
        if ("service".equals(tag)) return 0xFFFFD479;
        if ("location".equals(tag)) return 0xFFB9F78E;
        if ("gps".equals(tag)) return 0xFF7DE3D1;
        if ("alarm".equals(tag)) return 0xFFC6A5FF;
        if ("boot".equals(tag)) return 0xFFFFA3A3;
        if ("ui".equals(tag)) return 0xFFFFB5DF;
        if ("notification".equals(tag)) return 0xFFFFC27A;
        if ("alert".equals(tag)) return 0xFFFF6F91;
        if ("battery".equals(tag)) return 0xFFFFE082;
        if ("accessibility".equals(tag)) return 0xFFD4A5FF;
        if ("reboot".equals(tag)) return 0xFFFF8A65;
        if ("remote".equals(tag)) return 0xFF8BD3FF;
        if ("settings".equals(tag)) return 0xFFA7C7E7;
        return 0xFFE3EBD9;
    }

    // ============================================================
    //  Fields, toggles, status rows
    // ============================================================

    private EditText addField(LinearLayout root, String label, int inputType) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(COLOR_LABEL);
        labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelView.setLetterSpacing(0.02f);
        labelView.setIncludeFontPadding(false);
        root.addView(labelView, stack(root));

        EditText field = new EditText(this);
        field.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setTextSize(15);
        field.setTextColor(COLOR_TEXT);
        field.setHintTextColor(COLOR_TEXT_FAINT);
        field.setMinHeight(dp(FIELD_MIN_HEIGHT));
        field.setMinimumHeight(dp(FIELD_MIN_HEIGHT));
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        root.addView(field, stack(root));
        return field;
    }

    private void configureAppPickerField(final EditText field) {
        field.setFocusable(false);
        field.setFocusableInTouchMode(false);
        field.setCursorVisible(false);
        field.setInputType(InputType.TYPE_NULL);
        field.setHint("Tap to select an app");
        field.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAppPicker(field);
            }
        });
    }

    private void showAppPicker(final EditText targetField) {
        AppPickerDialog.show(this, appPackageFromField(targetField), new AppPickerDialog.Listener() {
            @Override
            public void onAppSelected(String packageName, String label) {
                targetField.setText(appDisplayText(packageName, label));
                LogStore.append(MainActivity.this, "ui", "Selected app package=" + packageName + " label=" + label);
                Toast.makeText(MainActivity.this, label, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void configureTonePickerField(final EditText field) {
        field.setFocusable(false);
        field.setFocusableInTouchMode(false);
        field.setCursorVisible(false);
        field.setInputType(InputType.TYPE_NULL);
        field.setHint("Tap to select a tone");
        field.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTonePicker(field);
            }
        });
    }

    private void showTonePicker(final EditText targetField) {
        TonePickerDialog.show(this, text(targetField), new TonePickerDialog.Listener() {
            @Override
            public void onToneSelected(String title, String typeLabel) {
                targetField.setText(title);
                LogStore.append(MainActivity.this, "ui", "Selected alarm tone title=" + title + " type=" + typeLabel);
                Toast.makeText(MainActivity.this, title, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private LinearLayout addToggleGroup(LinearLayout parent) {
        LinearLayout group = newColumn();
        group.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        parent.addView(group, stack(parent));
        return group;
    }

    @SuppressWarnings("deprecation")
    private Switch addGroupedToggle(LinearLayout group, String text) {
        if (group.getChildCount() > 0) {
            View hairline = new View(this);
            hairline.setBackgroundColor(COLOR_FIELD_BORDER);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            group.addView(hairline, hlp);
        }

        LinearLayout row = newRow();
        row.setMinimumHeight(dp(FIELD_MIN_HEIGHT));
        row.setPadding(dp(GAP), 0, dp(GAP), 0);

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(14);
        label.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(label, labelLp);

        final Switch sw = makeSwitch();
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.setMargins(dp(GAP), 0, 0, 0);
        row.addView(sw, swLp);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sw.toggle();
            }
        });

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        group.addView(row, rowLp);

        return sw;
    }

    @SuppressWarnings("deprecation")
    private Switch makeSwitch() {
        Switch sw = new Switch(this);
        ColorStateList trackTint = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{COLOR_SWITCH_TRACK_ON, COLOR_SWITCH_TRACK_OFF});
        ColorStateList thumbTint = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{COLOR_PRIMARY, 0xFFFFFFFF});
        sw.setTrackTintList(trackTint);
        sw.setThumbTintList(thumbTint);
        return sw;
    }

    private void addSubsectionLabel(LinearLayout panel, String title) {
        TextView t = new TextView(this);
        t.setText(title.toUpperCase());
        t.setTextSize(11);
        t.setTextColor(COLOR_TEXT_FAINT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.16f);
        t.setIncludeFontPadding(false);
        panel.addView(t, stack(panel));
    }

    private void addStatusRow(LinearLayout root, String label, boolean ok) {
        LinearLayout row = newRow();
        row.setMinimumHeight(dp(STATUS_ROW_MIN_HEIGHT));

        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ok ? COLOR_OK : COLOR_BAD);
        dot.setBackground(d);
        row.addView(dot, inRow(row, dp(STATUS_DOT_SIZE), dp(STATUS_DOT_SIZE), 0f));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(COLOR_TEXT);
        labelView.setTextSize(13);
        labelView.setIncludeFontPadding(false);
        row.addView(labelView, inRow(row, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(ok ? "READY" : "NEEDED");
        valueView.setTextColor(ok ? COLOR_OK : COLOR_BAD);
        valueView.setTextSize(10);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setLetterSpacing(0.10f);
        valueView.setIncludeFontPadding(false);
        row.addView(valueView, inRow(row,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        root.addView(row, stack(root));
    }

    // ============================================================
    //  System bars & insets
    // ============================================================

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void applyDynamicInsets(final LinearLayout root) {
        final int basePadding = dp(GAP);
        final int topBuffer = dp(4);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                int topInset;
                int bottomInset;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
                    topInset = systemBars.top;
                    bottomInset = systemBars.bottom;
                } else {
                    topInset = insets.getSystemWindowInsetTop();
                    bottomInset = insets.getSystemWindowInsetBottom();
                }
                view.setPadding(basePadding, topInset + topBuffer, basePadding, bottomInset);
                return insets;
            }
        });
    }

    // ============================================================
    //  Refresh / state
    // ============================================================

    private void refreshStatusAndLog() {
        if (statusContainer == null || logView == null) {
            return;
        }
        Config config = Config.get(this);
        WifiSnapshot wifi = WifiInfoReader.read(this);
        boolean ssidMatches = PatternMatcher.simpleMatch(config.ssidPattern(), wifi.ssid, config.caseSensitiveSsid());

        boolean tracking = config.isTrackingEnabled();
        setPillState(
                statusPill,
                tracking ? "ACTIVE" : "IDLE",
                tracking ? COLOR_PRIMARY_CONTAINER : COLOR_NEUTRAL_CONTAINER,
                tracking ? COLOR_PRIMARY_ON_CONTAINER : COLOR_NEUTRAL_ON_CONTAINER);
        setEnabledPill(highPriorityPill,
                switchValue(highPriorityEnabledSwitch, config.highPriorityEnabled())
                        || switchValue(highPriorityRemoteEnabledSwitch, config.highPriorityRemoteEnabled()));
        setEnabledPill(batteryAlertPill, switchValue(batteryAlertEnabledSwitch, config.batteryAlertEnabled()));
        setEnabledPill(rebootPill, switchValue(rebootAutomationEnabledSwitch, config.rebootAutomationEnabled()));
        setRemoteLinkPill(RemoteLinkStateStore.isConnected(this));
        setEnabledPill(logPill, switchValue(logEnabledSwitch, config.logEnabled()));
        setSettingsBackupPill(config.settingsLastExportMillis());
        applyButtonState(startTrackingButton, !tracking, COLOR_PRIMARY, Color.WHITE);
        applyButtonState(stopTrackingButton, tracking, COLOR_DANGER, Color.WHITE);

        if (wifiBadge != null) {
            if (wifi.connected) {
                setPillState(
                        wifiBadge,
                        ssidMatches ? "SSID MATCH" : "NO MATCH",
                        ssidMatches ? COLOR_PRIMARY_CONTAINER : COLOR_DANGER_CONTAINER,
                        ssidMatches ? COLOR_PRIMARY_ON_CONTAINER : COLOR_DANGER_ON_CONTAINER);
            } else {
                setPillState(wifiBadge, "NO WI-FI", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
            }
        }

        if (wifiSummary != null) {
            StringBuilder summary = new StringBuilder();
            if (wifi.connected) {
                summary.append("SSID   ").append(wifi.displaySsid);
                if (!wifi.displayBssid.isEmpty()) {
                    summary.append('\n').append("BSSID  ").append(wifi.displayBssid);
                }
            } else {
                summary.append("Not connected to Wi-Fi");
            }
            if (!wifi.detail.isEmpty()) {
                summary.append('\n').append(wifi.detail);
            }
            wifiSummary.setText(summary.toString());
        }

        boolean fineLocation = PermissionState.hasFineLocation(this);
        boolean backgroundLocation = PermissionState.hasBackgroundLocation(this);
        boolean locationServices = PermissionState.locationServicesEnabled(this);
        boolean nearbyWifi = PermissionState.hasNearbyWifi(this);
        boolean exactAlarmRequested = switchValue(useExactAlarmsSwitch, config.useExactAlarms())
                || switchValue(batteryAlertUseExactAlarmsSwitch, config.batteryAlertUseExactAlarms());
        boolean exactAlarms = !exactAlarmRequested || AlarmScheduler.canScheduleExact(this);
        boolean batteryUnrestricted = PermissionState.ignoringBatteryOptimizations(this);
        boolean notifications = PermissionState.notificationsEnabled(this);
        boolean notificationAccess = PermissionState.notificationListenerEnabled(this);
        boolean accessibility = PermissionState.accessibilityServiceEnabled(this);
        boolean hiddenWifiMonitorNeedsAccessibility = tracking
                && switchValue(postOnWifiChangeSwitch, config.postOnWifiChange())
                && !switchValue(showWifiMonitorNotificationSwitch, config.showWifiMonitorNotification())
                && !accessibility;
        if (wifiMonitorWarning != null) {
            wifiMonitorWarning.setText("Hidden Wi-Fi monitor needs Accessibility enabled, or turn on the visible monitor notification.");
            wifiMonitorWarning.setVisibility(hiddenWifiMonitorNeedsAccessibility ? View.VISIBLE : View.GONE);
        }
        boolean allPermissionsOk = fineLocation
                && backgroundLocation
                && locationServices
                && nearbyWifi
                && exactAlarms
                && batteryUnrestricted
                && notifications
                && notificationAccess
                && accessibility;
        setPillState(
                permissionsPill,
                allPermissionsOk ? "ALL OK" : "ATTENTION",
                allPermissionsOk ? COLOR_PRIMARY_CONTAINER : COLOR_DANGER_CONTAINER,
                allPermissionsOk ? COLOR_PRIMARY_ON_CONTAINER : COLOR_DANGER_ON_CONTAINER);

        statusContainer.removeAllViews();
        addStatusRow(statusContainer, "Fine location", fineLocation);
        addStatusRow(statusContainer, "Background location", backgroundLocation);
        addStatusRow(statusContainer, "Device location services", locationServices);
        addStatusRow(statusContainer, "Nearby Wi-Fi", nearbyWifi);
        addStatusRow(statusContainer, exactAlarmRequested ? "Exact alarms" : "Exact alarms not required", exactAlarms);
        addStatusRow(statusContainer, "Battery unrestricted", batteryUnrestricted);
        addStatusRow(statusContainer, "Notifications", notifications);
        addStatusRow(statusContainer, "Notification access", notificationAccess);
        addStatusRow(statusContainer, "Accessibility monitor", accessibility);
        if (hiddenWifiMonitorNeedsAccessibility) {
            addStatusRow(statusContainer, "Hidden Wi-Fi monitor", false);
        }

        refreshNotificationHistory();
        logView.setText(colorizeLog(LogStore.readTail(this, Math.min(config.logMaxLines(), 300))));
    }

    private void setRemoteLinkPill(boolean connected) {
        setPillState(
                remoteLinkPill,
                connected ? "CONNECTED" : "DISCONNECTED",
                connected ? COLOR_PRIMARY_CONTAINER : COLOR_DANGER_CONTAINER,
                connected ? COLOR_PRIMARY_ON_CONTAINER : COLOR_DANGER_ON_CONTAINER);
    }

    private void registerRemoteLinkStateReceiver() {
        if (remoteLinkStateReceiver != null) {
            return;
        }
        remoteLinkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshStatusAndLog();
            }
        };
        IntentFilter filter = new IntentFilter(RemoteLinkStateStore.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(remoteLinkStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(remoteLinkStateReceiver, filter);
        }
    }

    private void unregisterRemoteLinkStateReceiver() {
        if (remoteLinkStateReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(remoteLinkStateReceiver);
        } catch (RuntimeException ignored) {
        }
        remoteLinkStateReceiver = null;
    }

    private void registerNotificationHistoryReceiver() {
        if (notificationHistoryReceiver != null) {
            return;
        }
        notificationHistoryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshNotificationHistory();
            }
        };
        IntentFilter filter = new IntentFilter(NotificationHistoryStore.ACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationHistoryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notificationHistoryReceiver, filter);
        }
    }

    private void unregisterNotificationHistoryReceiver() {
        if (notificationHistoryReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(notificationHistoryReceiver);
        } catch (RuntimeException ignored) {
        }
        notificationHistoryReceiver = null;
    }

    private void setEnabledPill(TextView target, boolean enabled) {
        setPillState(
                target,
                enabled ? "ENABLED" : "DISABLED",
                enabled ? COLOR_PRIMARY_CONTAINER : COLOR_NEUTRAL_CONTAINER,
                enabled ? COLOR_PRIMARY_ON_CONTAINER : COLOR_NEUTRAL_ON_CONTAINER);
    }

    private void setSettingsBackupPill(long timestampMillis) {
        setPillState(
                settingsBackupPill,
                timestampMillis > 0L ? formatBackupDate(timestampMillis) : "Never",
                timestampMillis > 0L ? COLOR_PRIMARY_CONTAINER : COLOR_NEUTRAL_CONTAINER,
                timestampMillis > 0L ? COLOR_PRIMARY_ON_CONTAINER : COLOR_NEUTRAL_ON_CONTAINER);
    }

    private void setPillState(TextView target, String text, int bg, int fg) {
        if (target == null) {
            return;
        }
        target.setText(text);
        target.setTextColor(fg);
        target.setBackground(roundedFill(bg, PILL_CORNER, 0, 0));
    }

    private boolean switchValue(Switch sw, boolean fallback) {
        return sw == null ? fallback : sw.isChecked();
    }

    // ============================================================
    //  Config / actions
    // ============================================================

    private void loadConfigIntoFields() {
        loadingConfig = true;
        try {
            Config config = Config.get(this);
            serverBaseUrlField.setText(config.serverBaseUrl());
            trackPathField.setText(config.trackPath());
            ssidPatternField.setText(config.ssidPattern());
            highBatteryIntervalField.setText(Integer.toString(config.highBatteryIntervalMinutes()));
            lowBatteryIntervalField.setText(Integer.toString(config.lowBatteryIntervalMinutes()));
            batteryThresholdField.setText(Integer.toString(config.batteryThresholdPercent()));
            locationTimeoutField.setText(Integer.toString(config.locationTimeoutSeconds()));
            desiredAccuracyField.setText(Integer.toString(config.desiredAccuracyMeters()));
            maxCachedLocationField.setText(Integer.toString(config.maxCachedLocationMinutes()));
            httpTimeoutField.setText(Integer.toString(config.httpTimeoutSeconds()));
            fallbackLatitudeField.setText(Double.toString(config.fallbackLatitude()));
            fallbackLongitudeField.setText(Double.toString(config.fallbackLongitude()));
            logEnabledSwitch.setChecked(config.logEnabled());
            logMaxLinesField.setText(Integer.toString(config.logMaxLines()));
            highPriorityEnabledSwitch.setChecked(config.highPriorityEnabled());
            highPriorityPackageField.setText(appDisplayText(config.highPriorityPackage()));
            highPriorityTextFilterField.setText(config.highPriorityTextFilter());
            highPriorityRemoteEnabledSwitch.setChecked(config.highPriorityRemoteEnabled());
            highPriorityRemoteTextFilterField.setText(config.highPriorityRemoteTextFilter());
            highPriorityRemoteDedupeSecondsField.setText(Integer.toString(config.highPriorityRemoteDedupeSeconds()));
            highPriorityToneTitleField.setText(config.highPriorityToneTitle());
            highPriorityPlaySecondsField.setText(Integer.toString(config.highPriorityPlaySeconds()));
            highPriorityDedupeSecondsField.setText(Integer.toString(config.highPriorityDedupeSeconds()));
            highPriorityRaiseAlarmVolumeSwitch.setChecked(config.highPriorityRaiseAlarmVolume());
            highPriorityAlarmVolumePercentField.setText(Integer.toString(config.highPriorityAlarmVolumePercent()));
            batteryAlertEnabledSwitch.setChecked(config.batteryAlertEnabled());
            batteryAlertThresholdField.setText(Integer.toString(config.batteryAlertThresholdPercent()));
            batteryAlertCheckIntervalField.setText(Integer.toString(config.batteryAlertCheckIntervalMinutes()));
            batteryAlertVibrateSecondsField.setText(Integer.toString(config.batteryAlertVibrateSeconds()));
            batteryAlertUseExactAlarmsSwitch.setChecked(config.batteryAlertUseExactAlarms());
            batteryAlertAllowIdleAlarmsSwitch.setChecked(config.batteryAlertAllowIdleAlarms());
            rebootAutomationEnabledSwitch.setChecked(config.rebootAutomationEnabled());
            rebootNotificationTriggerEnabledSwitch.setChecked(config.rebootNotificationTriggerEnabled());
            rebootRemoteTriggerEnabledSwitch.setChecked(config.rebootRemoteTriggerEnabled());
            rebootScheduleEnabledSwitch.setChecked(config.rebootScheduleEnabled());
            rebootTriggerPackageField.setText(appDisplayText(config.rebootTriggerPackage()));
            rebootTriggerTitleField.setText(config.rebootTriggerTitle());
            rebootTriggerTextField.setText(config.rebootTriggerText());
            rebootScheduleHourField.setText(Integer.toString(config.rebootScheduleHour()));
            rebootScheduleMinuteField.setText(Integer.toString(config.rebootScheduleMinute()));
            rebootWifiPatternField.setText(config.rebootWifiPattern());
            rebootOnlyWhenWifiNotMatchingSwitch.setChecked(config.rebootOnlyWhenWifiNotMatching());
            rebootPinSequenceField.setText(config.rebootPinSequence());
            rebootDelayedTestSecondsField.setText(Integer.toString(config.rebootDelayedTestSeconds()));
            rebootPowerDialogWaitMsField.setText(Integer.toString(config.rebootPowerDialogWaitMs()));
            rebootStepWaitMsField.setText(Integer.toString(config.rebootStepWaitMs()));
            remoteLinkEnabledSwitch.setChecked(config.remoteLinkEnabled());
            remoteLinkEndpointField.setText(config.remoteLinkEndpoint());
            remoteLinkUsernameField.setText(config.remoteLinkUsername());
            remoteLinkPasswordField.setText(config.remoteLinkPassword());
            remoteLinkHeartbeatSecondsField.setText(Integer.toString(config.remoteLinkHeartbeatSeconds()));
            remoteLinkAcceptAnySslCertSwitch.setChecked(config.remoteLinkAcceptAnySslCert());
            remoteLinkShowNotificationSwitch.setChecked(config.remoteLinkShowNotification());
            useExactAlarmsSwitch.setChecked(config.useExactAlarms());
            allowIdleAlarmsSwitch.setChecked(config.allowIdleAlarms());
            postOnStartupSwitch.setChecked(config.postOnStartup());
            postOnWifiChangeSwitch.setChecked(config.postOnWifiChange());
            showWifiMonitorNotificationSwitch.setChecked(config.showWifiMonitorNotification());
            useGpsProviderSwitch.setChecked(config.useGpsProvider());
            useNetworkProviderSwitch.setChecked(config.useNetworkProvider());
            requestGpsOnSsidMismatchSwitch.setChecked(config.requestGpsOnSsidMismatch());
            useFallbackOnSsidMatchSwitch.setChecked(config.useFallbackOnSsidMatch());
            useCachedBeforeFreshSwitch.setChecked(config.useCachedBeforeFresh());
            includeExtendedFieldsSwitch.setChecked(config.includeExtendedFields());
            caseSensitiveSsidSwitch.setChecked(config.caseSensitiveSsid());
        } finally {
            loadingConfig = false;
        }
    }

    private void wireLiveSettings() {
        bindSaveGpsConfig(useExactAlarmsSwitch);
        bindSaveGpsConfig(allowIdleAlarmsSwitch);
        bindSaveGpsConfig(postOnStartupSwitch);
        bindSaveGpsConfig(postOnWifiChangeSwitch);
        bindSaveGpsConfig(showWifiMonitorNotificationSwitch);
        bindSaveGpsConfig(useGpsProviderSwitch);
        bindSaveGpsConfig(useNetworkProviderSwitch);
        bindSaveGpsConfig(requestGpsOnSsidMismatchSwitch);
        bindSaveGpsConfig(useFallbackOnSsidMatchSwitch);
        bindSaveGpsConfig(useCachedBeforeFreshSwitch);
        bindSaveGpsConfig(includeExtendedFieldsSwitch);
        bindSaveGpsConfig(caseSensitiveSsidSwitch);

        bindSaveHighPriorityConfig(highPriorityEnabledSwitch);
        bindSaveHighPriorityConfig(highPriorityRemoteEnabledSwitch);
        bindSaveHighPriorityConfig(highPriorityRaiseAlarmVolumeSwitch);

        bindSaveBatteryAlertConfig(batteryAlertEnabledSwitch);
        bindSaveBatteryAlertConfig(batteryAlertUseExactAlarmsSwitch);
        bindSaveBatteryAlertConfig(batteryAlertAllowIdleAlarmsSwitch);

        bindSaveRebootConfig(rebootAutomationEnabledSwitch);
        bindSaveRebootConfig(rebootNotificationTriggerEnabledSwitch);
        bindSaveRebootConfig(rebootRemoteTriggerEnabledSwitch);
        bindSaveRebootConfig(rebootScheduleEnabledSwitch);
        bindSaveRebootConfig(rebootOnlyWhenWifiNotMatchingSwitch);

        bindSaveRemoteLinkConfig(remoteLinkEnabledSwitch);
        bindSaveRemoteLinkConfig(remoteLinkAcceptAnySslCertSwitch);
        bindSaveRemoteLinkConfig(remoteLinkShowNotificationSwitch);

        logEnabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (loadingConfig) {
                    return;
                }
                Config.get(MainActivity.this).saveLogConfig(isChecked, text(logMaxLinesField));
                refreshStatusAndLog();
            }
        });
    }

    private void bindSaveGpsConfig(Switch sw) {
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (loadingConfig) {
                    return;
                }
                saveGpsConfigOnly();
            }
        });
    }

    private void bindSaveHighPriorityConfig(Switch sw) {
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (loadingConfig) {
                    return;
                }
                saveHighPriorityConfigOnly();
            }
        });
    }

    private void bindSaveBatteryAlertConfig(Switch sw) {
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (loadingConfig) {
                    return;
                }
                saveBatteryAlertConfigOnly();
            }
        });
    }

    private void bindSaveRebootConfig(Switch sw) {
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (loadingConfig) {
                    return;
                }
                saveRebootConfigOnly();
            }
        });
    }

    private void bindSaveRemoteLinkConfig(Switch sw) {
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (loadingConfig) {
                    return;
                }
                saveRemoteLinkConfigLive();
            }
        });
    }

    private void saveConfig(boolean toast) {
        Config.get(this).saveGpsConfig(
                text(serverBaseUrlField),
                text(trackPathField),
                text(ssidPatternField),
                text(highBatteryIntervalField),
                text(lowBatteryIntervalField),
                text(batteryThresholdField),
                text(locationTimeoutField),
                text(desiredAccuracyField),
                text(maxCachedLocationField),
                text(httpTimeoutField),
                text(fallbackLatitudeField),
                text(fallbackLongitudeField),
                useExactAlarmsSwitch.isChecked(),
                allowIdleAlarmsSwitch.isChecked(),
                postOnStartupSwitch.isChecked(),
                postOnWifiChangeSwitch.isChecked(),
                showWifiMonitorNotificationSwitch.isChecked(),
                useGpsProviderSwitch.isChecked(),
                useNetworkProviderSwitch.isChecked(),
                requestGpsOnSsidMismatchSwitch.isChecked(),
                useFallbackOnSsidMatchSwitch.isChecked(),
                useCachedBeforeFreshSwitch.isChecked(),
                includeExtendedFieldsSwitch.isChecked(),
                caseSensitiveSsidSwitch.isChecked(),
                text(logMaxLinesField));
        Config.get(this).saveHighPriorityConfig(
                highPriorityEnabledSwitch.isChecked(),
                appPackageFromField(highPriorityPackageField),
                text(highPriorityTextFilterField),
                highPriorityRemoteEnabledSwitch.isChecked(),
                text(highPriorityRemoteTextFilterField),
                text(highPriorityRemoteDedupeSecondsField),
                text(highPriorityToneTitleField),
                text(highPriorityPlaySecondsField),
                text(highPriorityDedupeSecondsField),
                highPriorityRaiseAlarmVolumeSwitch.isChecked(),
                text(highPriorityAlarmVolumePercentField));
        Config.get(this).saveBatteryAlertConfig(
                batteryAlertEnabledSwitch.isChecked(),
                text(batteryAlertThresholdField),
                text(batteryAlertCheckIntervalField),
                text(batteryAlertVibrateSecondsField),
                batteryAlertUseExactAlarmsSwitch.isChecked(),
                batteryAlertAllowIdleAlarmsSwitch.isChecked());
        Config.get(this).saveRebootConfig(
                rebootAutomationEnabledSwitch.isChecked(),
                rebootNotificationTriggerEnabledSwitch.isChecked(),
                rebootRemoteTriggerEnabledSwitch.isChecked(),
                rebootScheduleEnabledSwitch.isChecked(),
                appPackageFromField(rebootTriggerPackageField),
                text(rebootTriggerTitleField),
                text(rebootTriggerTextField),
                text(rebootScheduleHourField),
                text(rebootScheduleMinuteField),
                text(rebootWifiPatternField),
                rebootOnlyWhenWifiNotMatchingSwitch.isChecked(),
                text(rebootPinSequenceField),
                text(rebootDelayedTestSecondsField),
                text(rebootPowerDialogWaitMsField),
                text(rebootStepWaitMsField));
        Config.get(this).saveRemoteLinkConfig(
                remoteLinkEnabledSwitch.isChecked(),
                text(remoteLinkEndpointField),
                text(remoteLinkUsernameField),
                text(remoteLinkPasswordField),
                text(remoteLinkHeartbeatSecondsField),
                remoteLinkAcceptAnySslCertSwitch.isChecked(),
                remoteLinkShowNotificationSwitch.isChecked());
        Config.get(this).saveLogConfig(
                logEnabledSwitch.isChecked(),
                text(logMaxLinesField));
        LogStore.append(this, "ui", "Configuration saved");
        NetworkMonitorService.sync(this);
        BatteryAlertManager.sync(this, "config-save");
        RebootManager.sync(this, "config-save");
        RemoteLinkManager.restart(this, "config-save");
        if (toast) {
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        }
        refreshStatusAndLog();
    }

    private void saveGpsConfigOnly() {
        Config.get(this).saveGpsConfig(
                text(serverBaseUrlField),
                text(trackPathField),
                text(ssidPatternField),
                text(highBatteryIntervalField),
                text(lowBatteryIntervalField),
                text(batteryThresholdField),
                text(locationTimeoutField),
                text(desiredAccuracyField),
                text(maxCachedLocationField),
                text(httpTimeoutField),
                text(fallbackLatitudeField),
                text(fallbackLongitudeField),
                useExactAlarmsSwitch.isChecked(),
                allowIdleAlarmsSwitch.isChecked(),
                postOnStartupSwitch.isChecked(),
                postOnWifiChangeSwitch.isChecked(),
                showWifiMonitorNotificationSwitch.isChecked(),
                useGpsProviderSwitch.isChecked(),
                useNetworkProviderSwitch.isChecked(),
                requestGpsOnSsidMismatchSwitch.isChecked(),
                useFallbackOnSsidMatchSwitch.isChecked(),
                useCachedBeforeFreshSwitch.isChecked(),
                includeExtendedFieldsSwitch.isChecked(),
                caseSensitiveSsidSwitch.isChecked(),
                text(logMaxLinesField));
        NetworkMonitorService.sync(this);
        RebootManager.sync(this, "gps-config-live");
        refreshStatusAndLog();
    }

    private void saveGpsSettings() {
        saveGpsConfigOnly();
        LogStore.append(this, "ui", "GPS settings saved");
        Toast.makeText(this, "GPS settings saved", Toast.LENGTH_SHORT).show();
    }

    private void saveHighPriorityConfigOnly() {
        Config.get(this).saveHighPriorityConfig(
                highPriorityEnabledSwitch.isChecked(),
                appPackageFromField(highPriorityPackageField),
                text(highPriorityTextFilterField),
                highPriorityRemoteEnabledSwitch.isChecked(),
                text(highPriorityRemoteTextFilterField),
                text(highPriorityRemoteDedupeSecondsField),
                text(highPriorityToneTitleField),
                text(highPriorityPlaySecondsField),
                text(highPriorityDedupeSecondsField),
                highPriorityRaiseAlarmVolumeSwitch.isChecked(),
                text(highPriorityAlarmVolumePercentField));
        refreshStatusAndLog();
    }

    private void saveHighPrioritySettings() {
        saveHighPriorityConfigOnly();
        LogStore.append(this, "ui", "High priority settings saved");
        Toast.makeText(this, "High priority settings saved", Toast.LENGTH_SHORT).show();
    }

    private void saveBatteryAlertConfigOnly() {
        Config.get(this).saveBatteryAlertConfig(
                batteryAlertEnabledSwitch.isChecked(),
                text(batteryAlertThresholdField),
                text(batteryAlertCheckIntervalField),
                text(batteryAlertVibrateSecondsField),
                batteryAlertUseExactAlarmsSwitch.isChecked(),
                batteryAlertAllowIdleAlarmsSwitch.isChecked());
        BatteryAlertManager.sync(this, "battery-config-live");
        refreshStatusAndLog();
    }

    private void saveBatteryAlertSettings() {
        saveBatteryAlertConfigOnly();
        LogStore.append(this, "ui", "Battery alert settings saved");
        Toast.makeText(this, "Battery alert settings saved", Toast.LENGTH_SHORT).show();
    }

    private void startTracking() {
        saveConfig(false);
        Config config = Config.get(this);
        config.setTrackingEnabled(true);
        NetworkStateStore.seedIfMissing(this, WifiInfoReader.read(this), "manual-start");
        NetworkMonitorService.sync(this);
        LogStore.append(this, "ui", "Tracking enabled");
        try {
            SystemTaskService.startTask(this, TaskIds.GPS_POST, "manual-start", true);
            Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            LogStore.append(this, "ui", "Start tracking failed: " + e.getMessage());
            AlarmScheduler.scheduleGpsPost(this, "manual-start-fallback");
            Toast.makeText(this, "Scheduled; immediate run failed", Toast.LENGTH_LONG).show();
        }
        refreshStatusAndLog();
    }

    private void stopTracking() {
        Config.get(this).setTrackingEnabled(false);
        AlarmScheduler.cancelGpsPost(this);
        NetworkMonitorService.sync(this);
        LogStore.append(this, "ui", "Tracking disabled");
        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show();
        refreshStatusAndLog();
    }

    private void sendNow() {
        saveConfig(false);
        LogStore.append(this, "ui", "Manual send requested");
        try {
            SystemTaskService.startTask(this, TaskIds.GPS_POST, "manual-send", false);
            Toast.makeText(this, "Send started", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            LogStore.append(this, "ui", "Manual send failed to start: " + e.getMessage());
            Toast.makeText(this, "Could not start send", Toast.LENGTH_LONG).show();
        }
        refreshStatusAndLog();
    }

    private void requestCommonPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        List<String> permissions = new ArrayList<>();
        if (!PermissionState.hasFineLocation(this)) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!PermissionState.hasCoarseLocation(this)) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionState.hasNearbyWifi(this)) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (permissions.isEmpty()) {
            Toast.makeText(this, "Common permissions already granted", Toast.LENGTH_SHORT).show();
            refreshStatusAndLog();
            return;
        }
        requestPermissions(permissions.toArray(new String[0]), REQUEST_COMMON_PERMISSIONS);
    }

    private void requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "Background location follows foreground permission on this Android version", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQUEST_BACKGROUND_LOCATION);
            return;
        }
        openAppSettings();
        Toast.makeText(this, "Set Location to Allow all the time", Toast.LENGTH_LONG).show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void openExactAlarmSettings() {
        openIntent(AlarmScheduler.exactAlarmSettingsIntent(this));
    }

    private void openBatterySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            openIntent(intent);
        } else {
            openAppSettings();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        openIntent(intent);
    }

    private void openLocationSettings() {
        openIntent(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    private void openNotificationListenerSettings() {
        ComponentName component = new ComponentName(this, HighPriorityNotificationListener.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            detail.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString());
            try {
                startActivity(detail);
                return;
            } catch (RuntimeException e) {
                LogStore.append(this, "ui", "Notification listener detail settings failed: " + e.getMessage());
            }
        }
        openIntent(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void openAccessibilitySettings() {
        openIntent(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void testHighPriorityAlert() {
        saveConfig(false);
        LogStore.append(this, "ui", "Manual high-priority alert test requested");
        HighPriorityAlertPlayer.play(this, "manual-test");
        Toast.makeText(this, "Alert test started", Toast.LENGTH_SHORT).show();
        refreshStatusAndLog();
    }

    private void testBatteryAlert() {
        saveConfig(false);
        LogStore.append(this, "ui", "Manual battery alert test requested");
        BatteryAlertManager.sendTestNotification(this);
        Toast.makeText(this, "Battery alert test sent", Toast.LENGTH_SHORT).show();
        refreshStatusAndLog();
    }

    private void testRebootNow() {
        saveConfig(false);
        LogStore.append(this, "ui", "Manual reboot test requested");
        boolean started = RebootManager.requestReboot(this, "manual-test");
        Toast.makeText(this, started ? "Reboot automation started" : "Enable Accessibility first", Toast.LENGTH_LONG).show();
        refreshStatusAndLog();
    }

    private void testRebootDelayed() {
        saveConfig(false);
        LogStore.append(this, "ui", "Delayed reboot lock test requested");
        RebootManager.scheduleDelayedTest(this, "manual-delayed-test");
        Toast.makeText(this, "Lock the phone now", Toast.LENGTH_LONG).show();
        refreshStatusAndLog();
    }

    private void saveRemoteLinkConfigOnly() {
        Config.get(this).saveRemoteLinkConfig(
                remoteLinkEnabledSwitch.isChecked(),
                text(remoteLinkEndpointField),
                text(remoteLinkUsernameField),
                text(remoteLinkPasswordField),
                text(remoteLinkHeartbeatSecondsField),
                remoteLinkAcceptAnySslCertSwitch.isChecked(),
                remoteLinkShowNotificationSwitch.isChecked());
        LogStore.append(this, "ui", "Remote Link configuration saved");
    }

    private void saveRemoteLinkConfigLive() {
        saveRemoteLinkConfigOnly();
        RemoteLinkManager.restart(this, "remote-link-config-live");
        refreshStatusAndLog();
    }

    private void saveRemoteLinkSettings() {
        saveRemoteLinkConfigLive();
        Toast.makeText(this, "Remote Link settings saved", Toast.LENGTH_SHORT).show();
    }

    private void reconnectRemoteLink() {
        saveRemoteLinkConfigOnly();
        LogStore.append(this, "ui", "Manual Remote Link reconnect requested");
        RemoteLinkManager.restart(this, "manual-reconnect");
        Toast.makeText(this, "Remote Link reconnect requested", Toast.LENGTH_SHORT).show();
        refreshStatusAndLog();
    }

    private void pingRemoteLink() {
        LogStore.append(this, "ui", "Manual Remote Link ping requested");
        boolean sent = RemoteLinkManager.ping(this, "manual-ping");
        Toast.makeText(this, sent ? "Remote Link ping sent" : "Remote Link is not connected", Toast.LENGTH_SHORT).show();
        refreshStatusAndLog();
    }

    private void saveRebootConfigOnly() {
        Config.get(this).saveRebootConfig(
                rebootAutomationEnabledSwitch.isChecked(),
                rebootNotificationTriggerEnabledSwitch.isChecked(),
                rebootRemoteTriggerEnabledSwitch.isChecked(),
                rebootScheduleEnabledSwitch.isChecked(),
                appPackageFromField(rebootTriggerPackageField),
                text(rebootTriggerTitleField),
                text(rebootTriggerTextField),
                text(rebootScheduleHourField),
                text(rebootScheduleMinuteField),
                text(rebootWifiPatternField),
                rebootOnlyWhenWifiNotMatchingSwitch.isChecked(),
                text(rebootPinSequenceField),
                text(rebootDelayedTestSecondsField),
                text(rebootPowerDialogWaitMsField),
                text(rebootStepWaitMsField));
        RebootManager.sync(this, "reboot-config-live");
        refreshStatusAndLog();
    }

    private void saveRebootSettings() {
        saveRebootConfigOnly();
        LogStore.append(this, "ui", "Reboot settings saved");
        Toast.makeText(this, "Reboot settings saved", Toast.LENGTH_SHORT).show();
    }

    private void saveLogSettings() {
        Config.get(this).saveLogConfig(
                logEnabledSwitch.isChecked(),
                text(logMaxLinesField));
        LogStore.append(this, "ui", "Log settings saved");
        refreshStatusAndLog();
        Toast.makeText(this, "Log settings saved", Toast.LENGTH_SHORT).show();
    }

    private void exportSettingsXml() {
        saveConfig(false);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/xml");
        intent.putExtra(Intent.EXTRA_TITLE, "system-manager-settings-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".xml");
        try {
            startActivityForResult(intent, REQUEST_EXPORT_SETTINGS);
        } catch (RuntimeException e) {
            LogStore.append(this, "settings", "Settings export picker failed: " + e.getMessage());
            Toast.makeText(this, "Could not open export picker", Toast.LENGTH_LONG).show();
        }
    }

    private void importSettingsXml() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/xml", "text/xml", "text/*"});
        try {
            startActivityForResult(intent, REQUEST_IMPORT_SETTINGS);
        } catch (RuntimeException e) {
            LogStore.append(this, "settings", "Settings import picker failed: " + e.getMessage());
            Toast.makeText(this, "Could not open import picker", Toast.LENGTH_LONG).show();
        }
    }

    private void handleSettingsExport(Uri uri) {
        Config config = Config.get(this);
        long previousExportMillis = config.settingsLastExportMillis();
        long exportMillis = System.currentTimeMillis();
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) {
                throw new IllegalStateException("Output stream unavailable");
            }
            config.setSettingsLastExportMillis(exportMillis);
            int count = config.exportSettingsXml(output);
            LogStore.append(this, "settings", "Settings exported entries=" + count);
            Toast.makeText(this, "Settings exported", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            config.setSettingsLastExportMillis(previousExportMillis);
            LogStore.append(this, "settings", "Settings export failed: " + e.getMessage());
            Toast.makeText(this, "Settings export failed", Toast.LENGTH_LONG).show();
        }
        refreshStatusAndLog();
    }

    private void handleSettingsImport(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IllegalStateException("Input stream unavailable");
            }
            int count = Config.get(this).importSettingsXml(input);
            loadConfigIntoFields();
            syncAfterSettingsImport();
            LogStore.append(this, "settings", "Settings imported entries=" + count);
            Toast.makeText(this, "Settings imported", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            LogStore.append(this, "settings", "Settings import failed: " + e.getMessage());
            Toast.makeText(this, "Settings import failed", Toast.LENGTH_LONG).show();
        }
        refreshStatusAndLog();
    }

    private void syncAfterSettingsImport() {
        Config config = Config.get(this);
        if (config.isTrackingEnabled()) {
            NetworkStateStore.seedIfMissing(this, WifiInfoReader.read(this), "settings-import");
            AlarmScheduler.scheduleGpsPost(this, "settings-import");
        } else {
            AlarmScheduler.cancelGpsPost(this);
        }
        NetworkMonitorService.sync(this);
        BatteryAlertManager.sync(this, "settings-import");
        RebootManager.sync(this, "settings-import");
        RemoteLinkManager.restart(this, "settings-import");
    }

    private void openIntent(Intent intent) {
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_LONG).show();
            LogStore.append(this, "ui", "Settings intent failed: " + e.getMessage());
        }
    }

    private void clearLog() {
        LogStore.replaceWithSingleEntry(this, "ui", "Log cleared");
        refreshStatusAndLog();
        if (logView != null) {
            logView.scrollTo(0, 0);
            logView.post(new Runnable() {
                @Override
                public void run() {
                    logView.scrollTo(0, 0);
                }
            });
        }
    }

    private void clearNotificationHistory() {
        NotificationHistoryStore.clear(this);
        LogStore.append(this, "ui", "Notification history cleared");
        Toast.makeText(this, "Notification history cleared", Toast.LENGTH_SHORT).show();
        refreshNotificationHistory();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_SETTINGS) {
            handleSettingsExport(uri);
        } else if (requestCode == REQUEST_IMPORT_SETTINGS) {
            handleSettingsImport(uri);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        LogStore.append(this, "ui", "Permission result request=" + requestCode);
        refreshStatusAndLog();
    }

    private View.OnClickListener action(final String command) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ("start".equals(command)) {
                    startTracking();
                } else if ("stop".equals(command)) {
                    stopTracking();
                } else if ("send".equals(command)) {
                    sendNow();
                } else if ("save".equals(command)) {
                    saveConfig(true);
                } else if ("save_gps".equals(command)) {
                    saveGpsSettings();
                } else if ("save_high_priority".equals(command)) {
                    saveHighPrioritySettings();
                } else if ("save_battery_alert".equals(command)) {
                    saveBatteryAlertSettings();
                } else if ("save_reboot".equals(command)) {
                    saveRebootSettings();
                } else if ("save_remote_link".equals(command)) {
                    saveRemoteLinkSettings();
                } else if ("save_log".equals(command)) {
                    saveLogSettings();
                } else if ("export_settings".equals(command)) {
                    exportSettingsXml();
                } else if ("import_settings".equals(command)) {
                    importSettingsXml();
                } else if ("common_permissions".equals(command)) {
                    requestCommonPermissions();
                } else if ("background_location".equals(command)) {
                    requestBackgroundLocation();
                } else if ("exact_alarm".equals(command)) {
                    openExactAlarmSettings();
                } else if ("battery".equals(command)) {
                    openBatterySettings();
                } else if ("app_settings".equals(command)) {
                    openAppSettings();
                } else if ("location_settings".equals(command)) {
                    openLocationSettings();
                } else if ("notification_access".equals(command)) {
                    openNotificationListenerSettings();
                } else if ("accessibility_settings".equals(command)) {
                    openAccessibilitySettings();
                } else if ("test_high_priority_alert".equals(command)) {
                    testHighPriorityAlert();
                } else if ("test_battery_alert".equals(command)) {
                    testBatteryAlert();
                } else if ("test_reboot_now".equals(command)) {
                    testRebootNow();
                } else if ("test_reboot_delayed".equals(command)) {
                    testRebootDelayed();
                } else if ("remote_link_reconnect".equals(command)) {
                    reconnectRemoteLink();
                } else if ("remote_link_ping".equals(command)) {
                    pingRemoteLink();
                } else if ("notifications".equals(command)) {
                    requestNotificationPermission();
                } else if ("refresh_notification_history".equals(command)) {
                    refreshNotificationHistory();
                } else if ("clear_notification_history".equals(command)) {
                    clearNotificationHistory();
                } else if ("refresh_log".equals(command)) {
                    refreshStatusAndLog();
                } else if ("clear_log".equals(command)) {
                    clearLog();
                }
            }
        };
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String text(EditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String appDisplayText(String packageName) {
        return appDisplayText(packageName, appLabel(packageName));
    }

    private String appDisplayText(String packageName, String label) {
        String cleanPackage = packageName == null ? "" : packageName.trim();
        if (cleanPackage.isEmpty()) {
            return "";
        }
        String cleanLabel = label == null ? "" : label.trim();
        if (cleanLabel.isEmpty() || cleanLabel.equals(cleanPackage)) {
            return cleanPackage;
        }
        return cleanLabel + " (" + cleanPackage + ")";
    }

    private String appLabel(String packageName) {
        String cleanPackage = packageName == null ? "" : packageName.trim();
        if (cleanPackage.isEmpty()) {
            return "";
        }
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo info = packageManager.getApplicationInfo(cleanPackage, 0);
            CharSequence rawLabel = info.loadLabel(packageManager);
            return rawLabel == null ? "" : rawLabel.toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return "";
        }
    }

    private static String appPackageFromField(EditText field) {
        return appPackageFromText(text(field));
    }

    private static String appPackageFromText(String value) {
        String text = value == null ? "" : value.trim();
        if (text.endsWith(")")) {
            int open = text.lastIndexOf(" (");
            if (open >= 0 && open + 2 < text.length() - 1) {
                return text.substring(open + 2, text.length() - 1).trim();
            }
        }
        return text;
    }
}
