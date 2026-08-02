package com.jpitsg.sysman;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
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
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.LruCache;
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
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_COMMON_PERMISSIONS = 10;
    private static final int REQUEST_BACKGROUND_LOCATION = 11;
    private static final int REQUEST_NOTIFICATIONS = 12;
    private static final int REQUEST_BLUETOOTH_ADVERTISE = 13;
    private static final int REQUEST_EXPORT_SETTINGS = 20;
    private static final int REQUEST_IMPORT_SETTINGS = 21;
    private static final int REQUEST_SAVE_NOTIFICATION_IMAGE = 22;
    private static final int REQUEST_IMPORT_VPN_PROFILE = 23;
    private static final int REQUEST_IMPORT_VPN_CERT = 24;
    private static final int REQUEST_VPN_CONSENT = 25;

    /** Single spacing unit used for every margin and every padding in the UI. */
    private static final int GAP = Ui.GAP;

    private static final int CARD_CORNER = Ui.CARD_CORNER;
    private static final int FIELD_CORNER = Ui.FIELD_CORNER;
    private static final int BUTTON_CORNER = Ui.BUTTON_CORNER;
    private static final int GROUP_CORNER = Ui.GROUP_CORNER;
    private static final int PILL_CORNER = Ui.PILL_CORNER;
    private static final int FIELD_MIN_HEIGHT = Ui.FIELD_MIN_HEIGHT;
    private static final int BUTTON_MIN_HEIGHT = Ui.BUTTON_MIN_HEIGHT;
    private static final int TOGGLE_ROW_MIN_HEIGHT = 48;
    private static final int STATUS_ROW_MIN_HEIGHT = 36;
    private static final int PILL_WIDTH = 118;
    private static final int PANEL_ANIMATION_MS = 180;
    private static final int CHEVRON_SIZE = 32;
    private static final int STATUS_DOT_SIZE = 8;
    private static final int LOG_VISIBLE_LINES = 16;
    private static final int NOTIFICATION_HISTORY_PAGE_SIZE = 25;
    private static final long LIVE_SAVE_DELAY_MS = 600L;

    private static final int COLOR_BG = Ui.COLOR_BG;
    private static final int COLOR_SURFACE = Ui.COLOR_SURFACE;
    private static final int COLOR_GROUPED = Ui.COLOR_GROUPED;
    private static final int COLOR_BORDER = Ui.COLOR_BORDER;
    private static final int COLOR_PRIMARY = Ui.COLOR_PRIMARY;
    private static final int COLOR_PRIMARY_CONTAINER = Ui.COLOR_PRIMARY_CONTAINER;
    private static final int COLOR_PRIMARY_ON_CONTAINER = Ui.COLOR_PRIMARY_ON_CONTAINER;
    private static final int COLOR_NEUTRAL_CONTAINER = Ui.COLOR_NEUTRAL_CONTAINER;
    private static final int COLOR_NEUTRAL_ON_CONTAINER = Ui.COLOR_NEUTRAL_ON_CONTAINER;
    private static final int COLOR_DANGER = Ui.COLOR_DANGER;
    private static final int COLOR_DANGER_CONTAINER = Ui.COLOR_DANGER_CONTAINER;
    private static final int COLOR_DANGER_ON_CONTAINER = Ui.COLOR_DANGER_ON_CONTAINER;
    private static final int COLOR_FIELD_BG = Ui.COLOR_FIELD_BG;
    private static final int COLOR_FIELD_BORDER = Ui.COLOR_FIELD_BORDER;
    private static final int COLOR_TEXT = Ui.COLOR_TEXT;
    private static final int COLOR_TEXT_DIM = Ui.COLOR_TEXT_DIM;
    private static final int COLOR_TEXT_FAINT = Ui.COLOR_TEXT_FAINT;
    private static final int COLOR_LABEL = Ui.COLOR_LABEL;
    private static final int COLOR_OK = Ui.COLOR_OK;
    private static final int COLOR_BAD = Ui.COLOR_BAD;
    private static final int COLOR_LOG_BG = 0xFF14201D;
    private static final int COLOR_LOG_FG = 0xFFBFE0CB;
    private static final int COLOR_RIPPLE_DARK = Ui.COLOR_RIPPLE_DARK;
    private static final int COLOR_RIPPLE_LIGHT = Ui.COLOR_RIPPLE_LIGHT;
    private static final int COLOR_SWITCH_TRACK_OFF = 0xFFCED4D2;
    private static final int COLOR_SWITCH_TRACK_ON = 0x991E6F4F;

    private LinearLayout statusContainer;
    private TextView statusPill;
    private TextView highPriorityPill;
    private TextView batteryAlertPill;
    private TextView volumeControlPill;
    private TextView rebootPill;
    private TextView remoteLinkPill;
    private TextView permissionsPill;
    private TextView logPill;
    private TextView notificationHistoryPill;
    private TextView notificationBackupPill;
    private View notificationBackupStatusCard;
    private View notificationBackupDot;
    private TextView notificationBackupStatus;
    private TextView notificationBackupCountPill;
    private TextView settingsBackupPill;
    private TextView wifiBadge;
    private TextView wifiSummary;
    private TextView wifiMonitorWarning;
    private LinearLayout notificationHistoryList;
    private LinearLayout notificationHistoryPageRow;
    private Button notificationHistoryPrevButton;
    private Button notificationHistoryNextButton;
    private TextView notificationHistoryPageLabel;
    private int notificationHistoryPage;
    private volatile boolean notificationRefreshInFlight;
    private volatile boolean pendingNotificationRefresh;
    private volatile boolean pendingNotificationForce;
    private ScrollView contentScrollView;
    private LinearLayout volumeRuleList;
    private NotificationHistoryStore.Entry pendingImageSaveEntry;
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
    private EditText highPriorityTitleFilterField;
    private EditText highPriorityTitleExcludeField;
    private EditText highPriorityTextFilterField;
    private EditText highPriorityTextExcludeField;
    private EditText highPriorityRemoteTitleFilterField;
    private EditText highPriorityRemoteTitleExcludeField;
    private EditText highPriorityRemoteTextFilterField;
    private EditText highPriorityRemoteTextExcludeField;
    private EditText highPriorityRemoteDedupeSecondsField;
    private EditText highPriorityToneTitleField;
    private EditText highPriorityPlaySecondsField;
    private EditText highPriorityDedupeSecondsField;
    private EditText highPriorityAlarmVolumePercentField;
    private EditText batteryAlertThresholdField;
    private EditText batteryAlertCheckIntervalField;
    private EditText batteryAlertVibrateSecondsField;
    private Button volumeRuleTimeButton;
    private VolumeInput volumeRuleMediaInput;
    private VolumeInput volumeRuleRingInput;
    private VolumeInput volumeRuleNotificationInput;
    private VolumeInput volumeRuleAlarmInput;
    private Button volumeRuleDndUnchangedButton;
    private Button volumeRuleDndEnableButton;
    private Button volumeRuleDndDisableButton;
    private int volumeRuleHour = -1;
    private int volumeRuleMinute = -1;
    private int volumeRuleDndMode = Config.DND_UNCHANGED;
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
    private TextView openVpnPill;
    private Panel openVpnPanel;
    private LinearLayout openVpnProfileSummary;
    private LinearLayout openVpnEditRow;
    private LinearLayout openVpnSlotList;
    private LinearLayout vpnAuthSection;
    private LinearLayout vpnUserPassBlock;
    private LinearLayout vpnPassphraseBlock;
    private LinearLayout vpnTapSection;
    private TextView openVpnStatusText;
    private TextView openVpnEngineVersionText;
    private Button vpnConnectButton;
    private Button vpnDisconnectButton;
    private EditText vpnUsernameField;
    private EditText vpnPasswordField;
    private EditText vpnKeyPassphraseField;
    private EditText vpnTapStaticIpField;
    private EditText vpnTapNetmaskField;
    private EditText vpnTapGatewayField;
    private Switch vpnRemoteCommandEnabledSwitch;
    private String pendingVpnCertSlot;
    private boolean pendingVpnConnectAfterConsent;
    private BroadcastReceiver openVpnStateReceiver;
    private static volatile String cachedVpnEngineVersion;

    private TextView beaconPill;
    private LinearLayout beaconStatusList;
    private LinearLayout beaconRuleList;
    private TextView beaconUuidValue;
    private EditText beaconMajorField;
    private EditText beaconMinorField;
    private EditText beaconMeasuredPowerField;
    private EditText beaconRuleBatteryField;
    private EditText beaconRuleIntervalField;
    private Button beaconTxUltraLowButton;
    private Button beaconTxLowButton;
    private Button beaconTxMediumButton;
    private Button beaconTxHighButton;
    private int beaconTxPowerDbm = Config.BEACON_TX_POWER_HIGH;
    private BroadcastReceiver beaconStateReceiver;

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
    private Switch gpsUseRemoteLinkSwitch;
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
    private Switch taskServiceNotificationSwitch;
    private Switch vpnNotificationSwitch;
    private Switch beaconNotificationSwitch;
    private Switch beaconEnabledSwitch;
    private Switch logEnabledSwitch;
    private Switch clearNotificationsOnOpenSwitch;
    private Switch notificationBackupEnabledSwitch;
    private Switch notificationBackupIncludeSysmgrSwitch;
    private final List<Panel> panels = new ArrayList<>();
    private final Handler liveSaveHandler = new Handler(Looper.getMainLooper());
    private final List<LiveSaveGroup> liveSaveGroups = new ArrayList<>();
    private LiveSaveGroup gpsSettingsSave;
    private LiveSaveGroup highPrioritySettingsSave;
    private LiveSaveGroup batteryAlertSettingsSave;
    private LiveSaveGroup rebootSettingsSave;
    private LiveSaveGroup remoteLinkSettingsSave;
    private LiveSaveGroup vpnSettingsSave;
    private LiveSaveGroup beaconSettingsSave;
    private Toast settingsFeedbackToast;
    private BroadcastReceiver remoteLinkStateReceiver;
    private BroadcastReceiver notificationHistoryReceiver;
    private BroadcastReceiver notificationBackupReceiver;
    private boolean loadingConfig;
    private volatile WifiSnapshot cachedWifi;
    private volatile boolean statusIoInFlight;
    private volatile boolean statusIoRequested;
    private String renderedNotificationHistoryKey;
    private final LruCache<String, Bitmap> notificationImageCache =
            new LruCache<String, Bitmap>(notificationImageCacheSizeKb()) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return Math.max(1, bitmap.getAllocationByteCount() / 1024);
                }
            };

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

    private static final class VolumeInput {
        final SeekBar slider;
        final Switch unchangedSwitch;
        final TextView valueView;

        VolumeInput(SeekBar slider, Switch unchangedSwitch, TextView valueView) {
            this.slider = slider;
            this.unchangedSwitch = unchangedSwitch;
            this.valueView = valueView;
        }
    }

    private interface LiveSaveAction {
        boolean save();
    }

    /** Coalesces typing within one section while switches still persist immediately. */
    private final class LiveSaveGroup implements Runnable {
        private final LiveSaveAction saveAction;
        private boolean pending;

        LiveSaveGroup(LiveSaveAction saveAction) {
            this.saveAction = saveAction;
        }

        void schedule() {
            if (loadingConfig) {
                return;
            }
            pending = true;
            liveSaveHandler.removeCallbacks(this);
            liveSaveHandler.postDelayed(this, LIVE_SAVE_DELAY_MS);
        }

        boolean saveNow() {
            return saveNow(true);
        }

        boolean saveNow(boolean showToast) {
            if (loadingConfig) {
                return false;
            }
            pending = false;
            liveSaveHandler.removeCallbacks(this);
            boolean saved = saveAction.save();
            if (!saved) {
                // Keep the section dirty so a dependent action cannot proceed
                // with stale persisted values after a failed debounce.
                pending = true;
            }
            if (saved && showToast) {
                showSettingSavedToast();
            }
            return saved;
        }

        boolean isPending() {
            return pending;
        }

        void cancel() {
            pending = false;
            liveSaveHandler.removeCallbacks(this);
        }

        @Override
        public void run() {
            if (!pending) {
                return;
            }
            if (loadingConfig) {
                liveSaveHandler.postDelayed(this, LIVE_SAVE_DELAY_MS);
                return;
            }
            saveNow();
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
        LogStore.append(this, "ui", "MainActivity opened");
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Coming back to the app always starts from the same place: every panel
        // shut and the page at the top. onStart rather than onResume so a
        // permission dialog, which only pauses the activity, leaves the screen
        // as the user left it.
        collapseAllPanels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerRemoteLinkStateReceiver();
        registerNotificationHistoryReceiver();
        registerNotificationBackupReceiver();
        registerOpenVpnStateReceiver();
        registerBeaconStateReceiver();
        NotificationCleaner.clearOnAppOpen(this);
        OpenVpnManager.syncStateOnLaunch(this);
        refreshStatusAndLog();
        refreshNotificationHistory();
        refreshNotificationBackupStatus();
    }

    @Override
    protected void onPause() {
        flushPendingLiveSaves();
        unregisterNotificationHistoryReceiver();
        unregisterNotificationBackupReceiver();
        unregisterRemoteLinkStateReceiver();
        unregisterOpenVpnStateReceiver();
        unregisterBeaconStateReceiver();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        for (LiveSaveGroup group : liveSaveGroups) {
            group.cancel();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        configureSystemBars();
        View decor = getWindow().getDecorView();
        decor.requestApplyInsets();
        decor.requestLayout();
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
        contentScrollView = scrollView;
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
        buildNotificationBackupPanel(root);
        buildHighPriorityPanel(root);
        buildBatteryAlertPanel(root);
        buildVolumeControlPanel(root);
        buildOpenVpnPanel(root);
        buildBeaconPanel(root);
        buildRebootPanel(root);
        buildSettingsTransferPanel(root);
        buildPermissionsPanel(root);
        buildLogPanel(root);

        setContentView(scrollView);
        BatteryAlertManager.sync(this, "activity-open");
        RemoteLinkManager.sync(this, "activity-open");
        VolumeControlManager.sync(this, "activity-open");
        OpenVpnManager.sync(this, "activity-open");
        BeaconManager.sync(this, "activity-open");
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
    }

    // ============================================================
    //  Feature panels
    // ============================================================

    private void buildNotificationHistoryPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Notification History", true);
        notificationHistoryPill = frame.pill;

        notificationHistoryList = newColumn();
        notificationHistoryList.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        notificationHistoryList.setClipToOutline(true);
        frame.content.addView(notificationHistoryList, stack(frame.content));

        notificationHistoryPageRow = newRow();
        frame.content.addView(notificationHistoryPageRow, stack(frame.content));
        notificationHistoryPrevButton = neutralButton("Prev", action("notification_history_prev"));
        notificationHistoryPageRow.addView(notificationHistoryPrevButton,
                inRow(notificationHistoryPageRow, 0, dp(BUTTON_MIN_HEIGHT), 1f));
        notificationHistoryPageLabel = new TextView(this);
        notificationHistoryPageLabel.setGravity(Gravity.CENTER);
        notificationHistoryPageLabel.setTextSize(12);
        notificationHistoryPageLabel.setTextColor(COLOR_TEXT_DIM);
        notificationHistoryPageLabel.setIncludeFontPadding(false);
        notificationHistoryPageLabel.setLineSpacing(0, 1.1f);
        notificationHistoryPageRow.addView(notificationHistoryPageLabel,
                inRow(notificationHistoryPageRow, 0, dp(BUTTON_MIN_HEIGHT), 1.6f));
        notificationHistoryNextButton = neutralButton("Next", action("notification_history_next"));
        notificationHistoryPageRow.addView(notificationHistoryNextButton,
                inRow(notificationHistoryPageRow, 0, dp(BUTTON_MIN_HEIGHT), 1f));

        LinearLayout optionsGroup = addToggleGroup(frame.content);
        clearNotificationsOnOpenSwitch = addGroupedToggle(optionsGroup, "Clear Android notifications on app open");

        LinearLayout buttons = newRow();
        frame.content.addView(buttons, stack(frame.content));
        addRowButton(buttons, tonalButton("Refresh", action("refresh_notification_history")));
        addRowButton(buttons, neutralButton("Clear", action("clear_notification_history")));
    }

    private void buildNotificationBackupPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Notification Backup", true);
        notificationBackupPill = frame.pill;

        LinearLayout backupGroup = addToggleGroup(frame.content);
        notificationBackupEnabledSwitch = addGroupedToggle(backupGroup, "Send to server");
        notificationBackupIncludeSysmgrSwitch = addGroupedToggle(backupGroup, "Include own notifications");

        LinearLayout statusCard = newRow();
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        statusCard.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        statusCard.setMinimumHeight(dp(STATUS_ROW_MIN_HEIGHT));
        statusCard.setVisibility(View.GONE);
        notificationBackupStatusCard = statusCard;

        notificationBackupDot = new View(this);
        setDotColor(notificationBackupDot, COLOR_TEXT_FAINT);
        statusCard.addView(notificationBackupDot, inRow(statusCard, dp(STATUS_DOT_SIZE), dp(STATUS_DOT_SIZE), 0f));

        notificationBackupStatus = new TextView(this);
        notificationBackupStatus.setTextSize(13);
        notificationBackupStatus.setTextColor(COLOR_TEXT);
        notificationBackupStatus.setIncludeFontPadding(false);
        notificationBackupStatus.setLineSpacing(0, 1.1f);
        notificationBackupStatus.setPadding(dp(GAP), 0, dp(GAP), 0);
        statusCard.addView(notificationBackupStatus, inRow(statusCard, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        notificationBackupCountPill = new TextView(this);
        notificationBackupCountPill.setTextSize(11);
        notificationBackupCountPill.setTypeface(Typeface.DEFAULT_BOLD);
        notificationBackupCountPill.setLetterSpacing(0.06f);
        notificationBackupCountPill.setIncludeFontPadding(false);
        Ui.marqueeLabel(notificationBackupCountPill);
        notificationBackupCountPill.setPadding(dp(GAP), dp(4), dp(GAP), dp(4));
        notificationBackupCountPill.setVisibility(View.GONE);
        statusCard.addView(notificationBackupCountPill, inRow(statusCard,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        frame.content.addView(statusCard, stack(frame.content));
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
        gpsUseRemoteLinkSwitch = addGroupedToggle(group, "Send GPS over Remote Link");
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
        useExactAlarmsSwitch = addGroupedToggle(group, "Use exact GPS, volume, and reboot alarms");
        allowIdleAlarmsSwitch = addGroupedToggle(group, "Allow GPS, volume, and reboot alarms while idle");
        postOnStartupSwitch = addGroupedToggle(group, "Send once after boot");
        postOnWifiChangeSwitch = addGroupedToggle(group, "Send when Wi-Fi changes");
        // The Wi-Fi monitor's own notification toggle lives with the other
        // service notifications, under Permissions.
    }

    private void buildHighPriorityPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "High Priority Alerts", true);
        highPriorityPill = frame.pill;

        addSubsectionLabel(frame.content, "Package Based Alerts");
        LinearLayout packageGroup = addToggleGroup(frame.content);
        highPriorityEnabledSwitch = addGroupedToggle(packageGroup, "Enable package-based alerts");
        highPriorityPackageField = addField(frame.content, "Notification app", InputType.TYPE_CLASS_TEXT);
        configureAppPickerField(highPriorityPackageField);
        highPriorityTitleFilterField = addField(frame.content, "Title contains", InputType.TYPE_CLASS_TEXT);
        highPriorityTitleExcludeField = addField(frame.content, "Title does not contain", InputType.TYPE_CLASS_TEXT);
        highPriorityTextFilterField = addField(frame.content, "Message contains", InputType.TYPE_CLASS_TEXT);
        highPriorityTextExcludeField = addField(frame.content, "Message does not contain", InputType.TYPE_CLASS_TEXT);
        highPriorityDedupeSecondsField = addField(frame.content, "Duplicate window (seconds)", InputType.TYPE_CLASS_NUMBER);

        addSubsectionLabel(frame.content, "Remote Link Alerts");
        LinearLayout socketGroup = addToggleGroup(frame.content);
        highPriorityRemoteEnabledSwitch = addGroupedToggle(socketGroup, "Enable Remote Link alerts");
        highPriorityRemoteTitleFilterField = addField(frame.content, "Title contains", InputType.TYPE_CLASS_TEXT);
        highPriorityRemoteTitleExcludeField = addField(frame.content, "Title does not contain", InputType.TYPE_CLASS_TEXT);
        highPriorityRemoteTextFilterField = addField(frame.content, "Message contains", InputType.TYPE_CLASS_TEXT);
        highPriorityRemoteTextExcludeField = addField(frame.content, "Message does not contain", InputType.TYPE_CLASS_TEXT);
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

    }

    private void buildVolumeControlPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Volume Control", true);
        volumeControlPill = frame.pill;

        addSubsectionLabel(frame.content, "Add Rule");
        volumeRuleTimeButton = addPickerButton(frame.content, "Time", "Select Time", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showVolumeRuleTimePicker();
            }
        });
        volumeRuleMediaInput = addVolumeInput(frame.content, "Media");
        volumeRuleRingInput = addVolumeInput(frame.content, "Ring");
        volumeRuleNotificationInput = addVolumeInput(frame.content, "Notification");
        volumeRuleAlarmInput = addVolumeInput(frame.content, "Alarm");
        addDndModeInput(frame.content);

        LinearLayout addRow = newRow();
        frame.content.addView(addRow, stack(frame.content));
        addRowButton(addRow, tonalButton("Add Rule", action("add_volume_rule")));

        addSubsectionLabel(frame.content, "Rules");
        volumeRuleList = newColumn();
        volumeRuleList.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        frame.content.addView(volumeRuleList, stack(frame.content));
    }

    private void buildOpenVpnPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "OpenVPN", true);
        openVpnPanel = frame;
        openVpnPill = frame.pill;

        addSubsectionLabel(frame.content, "Profile");
        openVpnProfileSummary = newColumn();
        openVpnProfileSummary.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        frame.content.addView(openVpnProfileSummary, stack(frame.content));

        LinearLayout importRow = newRow();
        frame.content.addView(importRow, stack(frame.content));
        addRowButton(importRow, tonalButton("Import Profile", action("vpn_import_profile")));
        addRowButton(importRow, neutralButton("Clear Profile", action("vpn_clear_profile")));

        openVpnEditRow = newRow();
        frame.content.addView(openVpnEditRow, stack(frame.content));
        addRowButton(openVpnEditRow, tonalButton("Edit Profile", action("vpn_edit_profile")));

        addSubsectionLabel(frame.content, "Files");
        openVpnSlotList = newColumn();
        openVpnSlotList.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        frame.content.addView(openVpnSlotList, stack(frame.content));

        vpnAuthSection = newColumn();
        frame.content.addView(vpnAuthSection, stack(frame.content));
        addSubsectionLabel(vpnAuthSection, "Authentication");
        vpnUserPassBlock = newColumn();
        vpnAuthSection.addView(vpnUserPassBlock, stack(vpnAuthSection));
        vpnUsernameField = addField(vpnUserPassBlock, "Username", InputType.TYPE_CLASS_TEXT);
        vpnPasswordField = addField(vpnUserPassBlock, "Password",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        vpnPassphraseBlock = newColumn();
        vpnAuthSection.addView(vpnPassphraseBlock, stack(vpnAuthSection));
        vpnKeyPassphraseField = addField(vpnPassphraseBlock, "Private key passphrase",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        vpnTapSection = newColumn();
        frame.content.addView(vpnTapSection, stack(frame.content));
        addSubsectionLabel(vpnTapSection, "TAP Addressing");
        vpnTapStaticIpField = addField(vpnTapSection, "Static IP (blank = server-assigned)", InputType.TYPE_CLASS_TEXT);
        vpnTapNetmaskField = addField(vpnTapSection, "Netmask", InputType.TYPE_CLASS_TEXT);
        vpnTapGatewayField = addField(vpnTapSection, "Gateway (optional)", InputType.TYPE_CLASS_TEXT);

        addSubsectionLabel(frame.content, "Remote Control");
        LinearLayout remoteGroup = addToggleGroup(frame.content);
        vpnRemoteCommandEnabledSwitch = addGroupedToggle(remoteGroup, "Allow VPN control from Remote Link");

        addSubsectionLabel(frame.content, "Status");
        openVpnStatusText = historyText("Off", 13, COLOR_TEXT_DIM, false);
        openVpnStatusText.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        openVpnStatusText.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        frame.content.addView(openVpnStatusText, stack(frame.content));

        LinearLayout controlRow = newRow();
        frame.content.addView(controlRow, stack(frame.content));
        vpnConnectButton = primaryButton("Connect", action("vpn_connect"));
        vpnDisconnectButton = neutralButton("Disconnect", action("vpn_disconnect"));
        addRowButton(controlRow, vpnConnectButton);
        addRowButton(controlRow, vpnDisconnectButton);

        openVpnEngineVersionText = historyText("OpenVPN engine: resolving…", 11, COLOR_TEXT_FAINT, false);
        frame.content.addView(openVpnEngineVersionText, stack(frame.content));
    }

    private void buildBeaconPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Beacon", true);
        beaconPill = frame.pill;

        LinearLayout enableGroup = addToggleGroup(frame.content);
        beaconEnabledSwitch = addGroupedToggle(enableGroup, "Enable beacon");

        addSubsectionLabel(frame.content, "Identity");
        addFieldLabel(frame.content, "Proximity UUID — match receivers on this");
        beaconUuidValue = historyText("", 13, COLOR_TEXT, false);
        beaconUuidValue.setTypeface(Typeface.MONOSPACE);
        beaconUuidValue.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        beaconUuidValue.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        beaconUuidValue.setMinHeight(dp(FIELD_MIN_HEIGHT));
        beaconUuidValue.setGravity(Gravity.CENTER_VERTICAL);
        beaconUuidValue.setTextIsSelectable(true);
        frame.content.addView(beaconUuidValue, stack(frame.content));

        LinearLayout uuidRow = newRow();
        frame.content.addView(uuidRow, stack(frame.content));
        addRowButton(uuidRow, tonalButton("Copy UUID", action("beacon_copy_uuid")));
        addRowButton(uuidRow, neutralButton("New UUID", action("beacon_new_uuid")));

        beaconMajorField = addField(frame.content, "Major (0-65535)", InputType.TYPE_CLASS_NUMBER);
        beaconMinorField = addField(frame.content, "Minor (0-65535)", InputType.TYPE_CLASS_NUMBER);

        addSubsectionLabel(frame.content, "Signal");
        addBeaconTxPowerInput(frame.content);
        beaconMeasuredPowerField = addField(frame.content, "Measured power at 1 m (dBm)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

        addSubsectionLabel(frame.content, "Battery Rules");
        beaconRuleList = newColumn();
        beaconRuleList.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        beaconRuleList.setClipToOutline(true);
        frame.content.addView(beaconRuleList, stack(frame.content));

        addSubsectionLabel(frame.content, "Add Battery Rule");
        beaconRuleBatteryField = addField(frame.content, "Battery at or above (%)", InputType.TYPE_CLASS_NUMBER);
        beaconRuleIntervalField = addField(frame.content, "Broadcast every (seconds, 0 = don't broadcast)",
                InputType.TYPE_CLASS_NUMBER);
        LinearLayout addRuleRow = newRow();
        frame.content.addView(addRuleRow, stack(frame.content));
        addRowButton(addRuleRow, tonalButton("Add Rule", action("add_beacon_rule")));

        addSubsectionLabel(frame.content, "Status");
        beaconStatusList = newColumn();
        beaconStatusList.setBackground(roundedFill(COLOR_GROUPED, GROUP_CORNER, 1, COLOR_BORDER));
        beaconStatusList.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        frame.content.addView(beaconStatusList, stack(frame.content));

        LinearLayout buttonRow = newRow();
        frame.content.addView(buttonRow, stack(frame.content));
        addRowButton(buttonRow, tonalButton("Bluetooth Settings", action("bluetooth_settings")));
    }

    private void addBeaconTxPowerInput(LinearLayout root) {
        addFieldLabel(root, "Transmit power");
        LinearLayout row = newRow();
        root.addView(row, stack(root));
        beaconTxUltraLowButton = tonalButton("Ultra", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setBeaconTxPower(Config.BEACON_TX_POWER_ULTRA_LOW);
            }
        });
        beaconTxLowButton = tonalButton("Low", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setBeaconTxPower(Config.BEACON_TX_POWER_LOW);
            }
        });
        beaconTxMediumButton = tonalButton("Medium", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setBeaconTxPower(Config.BEACON_TX_POWER_MEDIUM);
            }
        });
        beaconTxHighButton = tonalButton("High", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setBeaconTxPower(Config.BEACON_TX_POWER_HIGH);
            }
        });
        addRowButton(row, beaconTxUltraLowButton);
        addRowButton(row, beaconTxLowButton);
        addRowButton(row, beaconTxMediumButton);
        addRowButton(row, beaconTxHighButton);
        updateBeaconTxPowerButtons();
    }

    private void setBeaconTxPower(int dbm) {
        int previous = beaconTxPowerDbm;
        beaconTxPowerDbm = dbm;
        updateBeaconTxPowerButtons();
        if (!loadingConfig && beaconSettingsSave != null) {
            if (!beaconSettingsSave.saveNow()) {
                beaconTxPowerDbm = previous;
                updateBeaconTxPowerButtons();
            }
        }
    }

    private void updateBeaconTxPowerButtons() {
        styleChoiceButton(beaconTxUltraLowButton, beaconTxPowerDbm == Config.BEACON_TX_POWER_ULTRA_LOW);
        styleChoiceButton(beaconTxLowButton, beaconTxPowerDbm == Config.BEACON_TX_POWER_LOW);
        styleChoiceButton(beaconTxMediumButton, beaconTxPowerDbm == Config.BEACON_TX_POWER_MEDIUM);
        styleChoiceButton(beaconTxHighButton, beaconTxPowerDbm == Config.BEACON_TX_POWER_HIGH);
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
    }

    private void buildRemoteLinkPanel(LinearLayout root) {
        Panel frame = addExpandablePanel(root, "Remote Link", true);
        remoteLinkPill = frame.pill;

        LinearLayout enableGroup = addToggleGroup(frame.content);
        remoteLinkEnabledSwitch = addGroupedToggle(enableGroup, "Enable Remote Link");
        remoteLinkAcceptAnySslCertSwitch = addGroupedToggle(enableGroup, "Accept any SSL cert");
        // The Remote Link's own notification toggle lives with the other
        // service notifications, under Permissions.

        addSubsectionLabel(frame.content, "Connection");
        remoteLinkEndpointField = addField(frame.content, "Remote Link endpoint", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        remoteLinkUsernameField = addField(frame.content, "Username", InputType.TYPE_CLASS_TEXT);
        remoteLinkPasswordField = addField(frame.content, "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        remoteLinkHeartbeatSecondsField = addField(frame.content, "Heartbeat (seconds)", InputType.TYPE_CLASS_NUMBER);

        LinearLayout row = newRow();
        frame.content.addView(row, stack(frame.content));
        addRowButton(row, tonalButton("Reconnect", action("remote_link_reconnect")));
        addRowButton(row, tonalButton("Ping", action("remote_link_ping")));

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

        LinearLayout r5 = newRow();
        frame.content.addView(r5, stack(frame.content));
        addRowButton(r5, tonalButton("DND Access", action("dnd_access")));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addRowButton(r5, tonalButton("Notification Permission", action("notifications")));
        }

        LinearLayout r6 = newRow();
        frame.content.addView(r6, stack(frame.content));
        addRowButton(r6, tonalButton("Bluetooth Advertise", action("bluetooth_permission")));
        addRowButton(r6, tonalButton("Bluetooth Settings", action("bluetooth_settings")));

        addSubsectionLabel(frame.content, "Service Notifications");
        LinearLayout serviceNotifications = addToggleGroup(frame.content);
        taskServiceNotificationSwitch = addGroupedToggle(serviceNotifications, "Task runner");
        showWifiMonitorNotificationSwitch = addGroupedToggle(serviceNotifications, "Wi-Fi monitor");
        remoteLinkShowNotificationSwitch = addGroupedToggle(serviceNotifications, "Remote Link");
        vpnNotificationSwitch = addGroupedToggle(serviceNotifications, "VPN");
        beaconNotificationSwitch = addGroupedToggle(serviceNotifications, "Beacon");

        wifiMonitorWarning = historyText("", 12, COLOR_BAD, false);
        wifiMonitorWarning.setVisibility(View.GONE);
        frame.content.addView(wifiMonitorWarning, stack(frame.content));
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
        refreshNotificationHistory(false);
    }

    private void refreshNotificationHistory(boolean forceRender) {
        if (notificationHistoryPill == null) {
            return;
        }
        if (forceRender) {
            pendingNotificationForce = true;
        }
        if (notificationRefreshInFlight) {
            pendingNotificationRefresh = true;
            return;
        }
        notificationRefreshInFlight = true;
        final boolean force = pendingNotificationForce;
        pendingNotificationForce = false;
        // The history JSON can now grow without bound, so parse it off the main
        // thread; only the paged (<=25) render happens on the UI thread.
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<NotificationHistoryStore.Entry> all = NotificationHistoryStore.read(MainActivity.this, 0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        notificationRefreshInFlight = false;
                        applyNotificationHistory(all, force);
                        if (pendingNotificationRefresh) {
                            pendingNotificationRefresh = false;
                            refreshNotificationHistory(false);
                        }
                    }
                });
            }
        }, "SystemManagerNotifHistory").start();
    }

    private void applyNotificationHistory(List<NotificationHistoryStore.Entry> allEntries, boolean forceRender) {
        int count = allEntries.size();
        setPillState(
                notificationHistoryPill,
                Integer.toString(count),
                count > 0 ? COLOR_PRIMARY_CONTAINER : COLOR_NEUTRAL_CONTAINER,
                count > 0 ? COLOR_PRIMARY_ON_CONTAINER : COLOR_NEUTRAL_ON_CONTAINER);
        if (notificationHistoryList == null) {
            return;
        }

        int totalPages = Math.max(1, (count + NOTIFICATION_HISTORY_PAGE_SIZE - 1) / NOTIFICATION_HISTORY_PAGE_SIZE);
        if (notificationHistoryPage > totalPages - 1) {
            notificationHistoryPage = totalPages - 1;
        }
        if (notificationHistoryPage < 0) {
            notificationHistoryPage = 0;
        }
        int start = notificationHistoryPage * NOTIFICATION_HISTORY_PAGE_SIZE;
        int end = Math.min(start + NOTIFICATION_HISTORY_PAGE_SIZE, count);
        List<NotificationHistoryStore.Entry> entries = start < end
                ? allEntries.subList(start, end)
                : new ArrayList<NotificationHistoryStore.Entry>();
        updateNotificationPagination(count, totalPages);

        String renderKey = notificationHistoryPage + "@" + notificationHistoryRenderKey(entries, count);
        if (!forceRender && renderKey.equals(renderedNotificationHistoryKey)) {
            return;
        }

        notificationHistoryList.removeAllViews();
        if (entries.isEmpty()) {
            addHistoryEmptyRow("No notifications yet");
            renderedNotificationHistoryKey = renderKey;
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                addHistorySeparator();
            }
            addHistoryEntry(entries.get(i));
        }
        renderedNotificationHistoryKey = renderKey;
    }

    private void updateNotificationPagination(int count, int totalPages) {
        if (notificationHistoryPageRow == null) {
            return;
        }
        if (totalPages <= 1) {
            notificationHistoryPageRow.setVisibility(View.GONE);
            return;
        }
        notificationHistoryPageRow.setVisibility(View.VISIBLE);
        int start = notificationHistoryPage * NOTIFICATION_HISTORY_PAGE_SIZE + 1;
        int end = Math.min(start + NOTIFICATION_HISTORY_PAGE_SIZE - 1, count);
        notificationHistoryPageLabel.setText("Page " + (notificationHistoryPage + 1) + " of " + totalPages
                + "\n" + start + "–" + end + " of " + count);
        applyButtonState(notificationHistoryPrevButton, notificationHistoryPage > 0,
                COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
        applyButtonState(notificationHistoryNextButton, notificationHistoryPage < totalPages - 1,
                COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
    }

    private void scrollNotificationHistoryToTop() {
        final ScrollView sv = contentScrollView;
        if (sv == null) {
            return;
        }
        // The Notification History panel is the first panel, so the top of the
        // scroll content is the top of that box.
        sv.post(new Runnable() {
            @Override
            public void run() {
                sv.smoothScrollTo(0, 0);
            }
        });
    }

    private String notificationHistoryRenderKey(
            List<NotificationHistoryStore.Entry> entries, int totalCount) {
        StringBuilder key = new StringBuilder(16 + entries.size() * 64);
        key.append(totalCount);
        for (NotificationHistoryStore.Entry entry : entries) {
            key.append('|')
                    .append(entry.id.length())
                    .append(':')
                    .append(entry.id)
                    .append(':')
                    .append(entry.timestampMillis)
                    .append(':')
                    .append(entry.highPriority ? '1' : '0')
                    .append(':')
                    .append(entry.imageFileName);
        }
        return key.toString();
    }

    private void refreshVolumeControlPanel() {
        if (volumeControlPill == null) {
            return;
        }
        List<Config.VolumeRule> rules = Config.get(this).volumeRules();
        String label = rules.size() == 1 ? "1 RULE" : rules.size() + " RULES";
        setPillState(
                volumeControlPill,
                label,
                rules.isEmpty() ? COLOR_NEUTRAL_CONTAINER : COLOR_PRIMARY_CONTAINER,
                rules.isEmpty() ? COLOR_NEUTRAL_ON_CONTAINER : COLOR_PRIMARY_ON_CONTAINER);
        if (volumeRuleList == null) {
            return;
        }

        volumeRuleList.removeAllViews();
        if (rules.isEmpty()) {
            TextView empty = historyText("No volume rules configured", 13, COLOR_TEXT_DIM, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
            volumeRuleList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) {
                View hairline = new View(this);
                hairline.setBackgroundColor(COLOR_FIELD_BORDER);
                volumeRuleList.addView(hairline, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
            addVolumeRuleRow(rules.get(i));
        }
    }

    private void addVolumeRuleRow(final Config.VolumeRule rule) {
        LinearLayout item = newColumn();
        item.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));

        LinearLayout top = newRow();
        item.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView time = historyText(rule.displayTime(), 16, COLOR_TEXT, true);
        top.addView(time, inRow(top, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button delete = neutralButton("Delete", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteVolumeRule(rule);
            }
        });
        top.addView(delete, inRow(top, dp(96), dp(BUTTON_MIN_HEIGHT), 0f));

        TextView detail = historyText(volumeRuleDetail(rule), 12, COLOR_TEXT_DIM, false);
        detail.setLineSpacing(0, 1.2f);
        item.addView(detail, topMarginParams(6));

        volumeRuleList.addView(item, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String volumeRuleDetail(Config.VolumeRule rule) {
        return "Media " + Config.volumeDisplay(rule.mediaPercent)
                + "   Ring " + Config.volumeDisplay(rule.ringPercent)
                + "\nNotification " + Config.volumeDisplay(rule.notificationPercent)
                + "   Alarm " + Config.volumeDisplay(rule.alarmPercent)
                + "\nDo Not Disturb " + Config.dndDisplay(rule.dndMode);
    }

    // ============================================================
    //  OpenVPN panel
    // ============================================================

    private void refreshOpenVpnPanel() {
        if (openVpnPill == null) {
            return;
        }
        boolean hasProfile = OpenVpnProfileStore.hasProfile(this);
        OpenVpnProfileStore.Meta meta = hasProfile ? OpenVpnProfileStore.readMeta(this) : null;
        String simpleState = OpenVpnStateStore.simpleState(this);
        boolean connectedOrConnecting = OpenVpnStateStore.SIMPLE_CONNECTED.equals(simpleState)
                || OpenVpnStateStore.SIMPLE_CONNECTING.equals(simpleState);

        setOpenVpnPill(hasProfile, simpleState);
        renderOpenVpnSummary(meta);
        renderOpenVpnSlots(meta);
        openVpnEditRow.setVisibility(hasProfile ? View.VISIBLE : View.GONE);

        boolean showAuth = meta != null && (meta.authUserPass || meta.keyEncrypted);
        vpnAuthSection.setVisibility(showAuth ? View.VISIBLE : View.GONE);
        if (showAuth) {
            vpnUserPassBlock.setVisibility(meta.authUserPass ? View.VISIBLE : View.GONE);
            vpnPassphraseBlock.setVisibility(meta.keyEncrypted ? View.VISIBLE : View.GONE);
        }
        vpnTapSection.setVisibility(meta != null && meta.isTap() ? View.VISIBLE : View.GONE);

        openVpnStatusText.setText(openVpnStatusLine(simpleState));

        boolean profileReady = hasProfile && meta != null && meta.allSlotsSatisfied();
        applyButtonState(vpnConnectButton, profileReady && !connectedOrConnecting, COLOR_PRIMARY, Color.WHITE);
        applyButtonState(vpnDisconnectButton, connectedOrConnecting, COLOR_DANGER, Color.WHITE);

        resolveVpnEngineVersion();
    }

    private void setOpenVpnPill(boolean hasProfile, String simpleState) {
        if (hasProfile && OpenVpnStateStore.SIMPLE_CONNECTED.equals(simpleState)) {
            setPillState(openVpnPill, "ENABLED", COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
        } else if (hasProfile && OpenVpnStateStore.SIMPLE_CONNECTING.equals(simpleState)) {
            setPillState(openVpnPill, "CONNECTING", COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
        } else if (hasProfile && OpenVpnStateStore.SIMPLE_ERROR.equals(simpleState)) {
            setPillState(openVpnPill, "ERROR", COLOR_DANGER_CONTAINER, COLOR_DANGER_ON_CONTAINER);
        } else {
            setPillState(openVpnPill, "DISABLED", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
        }
    }

    private String openVpnStatusLine(String simpleState) {
        if (OpenVpnStateStore.SIMPLE_CONNECTED.equals(simpleState)) {
            return "Connected · ↓" + OpenVpnService.formatBytes(OpenVpnStateStore.rxBytes(this))
                    + " ↑" + OpenVpnService.formatBytes(OpenVpnStateStore.txBytes(this));
        }
        if (OpenVpnStateStore.SIMPLE_CONNECTING.equals(simpleState)) {
            return "Connecting… (" + OpenVpnStateStore.label(OpenVpnStateStore.state(this)) + ")";
        }
        if (OpenVpnStateStore.SIMPLE_ERROR.equals(simpleState)) {
            String lastError = OpenVpnStateStore.lastError(this);
            return "Error: " + (lastError.isEmpty() ? "unknown" : lastError);
        }
        return "Off";
    }

    private void renderOpenVpnSummary(final OpenVpnProfileStore.Meta meta) {
        openVpnProfileSummary.removeAllViews();
        openVpnProfileSummary.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        if (meta == null || !meta.hasProfile()) {
            TextView empty = historyText("No profile imported", 13, COLOR_TEXT_DIM, false);
            empty.setGravity(Gravity.CENTER);
            openVpnProfileSummary.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        openVpnProfileSummary.addView(
                historyText(meta.remoteHost + ":" + meta.remotePort + "  " + meta.remoteProto, 16, COLOR_TEXT, true),
                matchWrapParams());
        openVpnProfileSummary.addView(
                historyText("dev " + meta.devType + "   " + meta.cipherSummary, 12, COLOR_TEXT_DIM, false),
                topMarginParams(4));

        long now = System.currentTimeMillis();
        if (meta.certNotAfterMillis > 0L) {
            boolean bad = meta.certNotAfterMillis < now
                    || meta.certNotAfterMillis - now < 30L * 24 * 60 * 60 * 1000;
            TextView cert = historyText("Client cert expires " + formatBackupDate(meta.certNotAfterMillis),
                    11, bad ? COLOR_BAD : COLOR_TEXT_DIM, false);
            openVpnProfileSummary.addView(cert, topMarginParams(4));
        }
        if (!meta.warnings.isEmpty()) {
            TextView warn = historyText(meta.warnings.size() + " warning(s) — tap to view", 11, COLOR_TEXT_DIM, false);
            warn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    OpenVpnImportDialog.showReport(MainActivity.this, meta);
                }
            });
            openVpnProfileSummary.addView(warn, topMarginParams(4));
        }
        String validation;
        if (!meta.validatedWithVersion.isEmpty()) {
            validation = "Validated with " + meta.validatedWithVersion + " · imported " + formatBackupDate(meta.importedAtMillis);
        } else if (!meta.validationFailure.isEmpty()) {
            validation = "Validation FAILED: " + meta.validationFailure;
        } else if (!meta.allSlotsSatisfied()) {
            validation = "Validation pending — import the missing files";
        } else {
            validation = "Validation pending";
        }
        openVpnProfileSummary.addView(historyText(validation, 11, COLOR_TEXT_FAINT, false), topMarginParams(4));
    }

    private void renderOpenVpnSlots(OpenVpnProfileStore.Meta meta) {
        openVpnSlotList.removeAllViews();
        openVpnSlotList.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        if (meta == null || meta.requiredSlots.isEmpty()) {
            TextView empty = historyText(meta == null ? "Import a profile to see its files"
                    : "Profile references no external files", 13, COLOR_TEXT_DIM, false);
            empty.setGravity(Gravity.CENTER);
            openVpnSlotList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        boolean first = true;
        for (String slot : meta.requiredSlots) {
            if (!first) {
                View hairline = new View(this);
                hairline.setBackgroundColor(COLOR_FIELD_BORDER);
                openVpnSlotList.addView(hairline, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
            first = false;
            addVpnSlotRow(slot, meta.satisfiedSlots.get(slot));
        }
    }

    private void addVpnSlotRow(final String slotId, String satisfiedBy) {
        LinearLayout row = newRow();
        row.setMinimumHeight(dp(STATUS_ROW_MIN_HEIGHT));

        boolean inline = "inline".equals(satisfiedBy);
        boolean imported = "file".equals(satisfiedBy);
        boolean satisfied = inline || imported;

        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(satisfied ? COLOR_OK : COLOR_BAD);
        dot.setBackground(d);
        row.addView(dot, inRow(row, dp(STATUS_DOT_SIZE), dp(STATUS_DOT_SIZE), 0f));

        TextView label = new TextView(this);
        label.setText(vpnSlotLabel(slotId));
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(13);
        label.setIncludeFontPadding(false);
        row.addView(label, inRow(row, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView status = new TextView(this);
        status.setText(inline ? "INLINE" : imported ? "IMPORTED ✓" : "MISSING — TAP TO IMPORT");
        status.setTextColor(satisfied ? COLOR_OK : COLOR_BAD);
        status.setTextSize(10);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setLetterSpacing(0.08f);
        status.setIncludeFontPadding(false);
        // "MISSING — TAP TO IMPORT" outruns the row on a narrow screen, and
        // this badge has no ellipsize to fall back on.
        Ui.marqueeLabel(status);
        row.addView(status, inRow(row, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        final boolean isInline = inline;
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isInline) {
                    Toast.makeText(MainActivity.this, "Provided inline by the profile", Toast.LENGTH_SHORT).show();
                } else {
                    importVpnCertSlot(slotId);
                }
            }
        });
        openVpnSlotList.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String vpnSlotLabel(String slotId) {
        switch (slotId) {
            case "ca": return "CA certificate";
            case "cert": return "Client certificate";
            case "key": return "Private key";
            case "tls-auth": return "TLS auth key";
            case "tls-crypt": return "TLS crypt key";
            case "tls-crypt-v2": return "TLS crypt v2 key";
            case "pkcs12": return "PKCS#12 bundle";
            case "crl-verify": return "Revocation list";
            case "extra-certs": return "Extra certificates";
            default: return slotId;
        }
    }

    private void resolveVpnEngineVersion() {
        if (openVpnEngineVersionText == null) {
            return;
        }
        String cached = cachedVpnEngineVersion;
        if (cached != null) {
            openVpnEngineVersionText.setText("Engine: " + cached);
            return;
        }
        boolean expanded = openVpnPanel != null && openVpnPanel.content.getVisibility() == View.VISIBLE;
        if (!expanded && !OpenVpnProfileStore.hasProfile(this)) {
            return;
        }
        OpenVpnHoldTester.resolveVersionAsync(this, new OpenVpnHoldTester.VersionCallback() {
            @Override
            public void onVersion(final String version) {
                cachedVpnEngineVersion = version;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (openVpnEngineVersionText != null) {
                            openVpnEngineVersionText.setText("Engine: " + version);
                        }
                    }
                });
            }
        });
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void registerOpenVpnStateReceiver() {
        if (openVpnStateReceiver != null) {
            return;
        }
        openVpnStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshStatusAndLog();
            }
        };
        IntentFilter filter = new IntentFilter(OpenVpnStateStore.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(openVpnStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(openVpnStateReceiver, filter);
        }
    }

    private void unregisterOpenVpnStateReceiver() {
        if (openVpnStateReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(openVpnStateReceiver);
        } catch (RuntimeException ignored) {
        }
        openVpnStateReceiver = null;
    }

    private void registerBeaconStateReceiver() {
        if (beaconStateReceiver != null) {
            return;
        }
        beaconStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshStatusAndLog();
            }
        };
        IntentFilter filter = new IntentFilter(BeaconStateStore.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(beaconStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(beaconStateReceiver, filter);
        }
    }

    private void unregisterBeaconStateReceiver() {
        if (beaconStateReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(beaconStateReceiver);
        } catch (RuntimeException ignored) {
        }
        beaconStateReceiver = null;
    }

    // ---- OpenVPN import / connect ------------------------------------------

    private void importVpnProfile() {
        String simpleState = OpenVpnStateStore.simpleState(this);
        if (OpenVpnStateStore.SIMPLE_CONNECTED.equals(simpleState)
                || OpenVpnStateStore.SIMPLE_CONNECTING.equals(simpleState)) {
            OpenVpnConfirmDialog.show(this, "Replace VPN profile?",
                    "The VPN is currently active and will be disconnected before importing a new profile.",
                    "Disconnect & Import", true, new OpenVpnConfirmDialog.Listener() {
                        @Override
                        public void onConfirm() {
                            OpenVpnManager.disconnect(MainActivity.this, "reimport");
                            launchVpnProfilePicker();
                        }
                    });
        } else {
            launchVpnProfilePicker();
        }
    }

    private void launchVpnProfilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "text/plain", "*/*"});
        try {
            startActivityForResult(intent, REQUEST_IMPORT_VPN_PROFILE);
        } catch (RuntimeException e) {
            LogStore.append(this, "vpn", "Profile import picker failed: " + e.getMessage());
            Toast.makeText(this, "Could not open import picker", Toast.LENGTH_LONG).show();
        }
    }

    private void importVpnCertSlot(String slot) {
        pendingVpnCertSlot = slot;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "text/plain", "*/*"});
        try {
            startActivityForResult(intent, REQUEST_IMPORT_VPN_CERT);
        } catch (RuntimeException e) {
            pendingVpnCertSlot = null;
            LogStore.append(this, "vpn", "Cert import picker failed: " + e.getMessage());
            Toast.makeText(this, "Could not open import picker", Toast.LENGTH_LONG).show();
        }
    }

    private void handleVpnProfileImport(Uri uri) {
        final byte[] bytes = readUriBytes(uri, 512 * 1024);
        if (bytes == null) {
            Toast.makeText(this, "Could not read profile", Toast.LENGTH_LONG).show();
            return;
        }
        final String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        new Thread(new Runnable() {
            @Override
            public void run() {
                OpenVpnValidationResult parsed;
                try {
                    parsed = OpenVpnProfileValidator.validate(MainActivity.this, text);
                } catch (RuntimeException e) {
                    parsed = new OpenVpnValidationResult();
                    parsed.error("Could not parse profile: " + e.getMessage());
                }
                final OpenVpnValidationResult result = parsed;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        presentVpnImportResult(result, bytes);
                    }
                });
            }
        }, "VpnImportParse").start();
    }

    private void presentVpnImportResult(final OpenVpnValidationResult result, final byte[] originalBytes) {
        OpenVpnImportDialog.show(this, result, new OpenVpnImportDialog.Listener() {
            @Override
            public void onKeep() {
                OpenVpnProfileStore.commitProfile(MainActivity.this, result, originalBytes);
                LogStore.append(MainActivity.this, "vpn", "Profile imported dev=" + result.devType
                        + " warnings=" + result.warnings.size());
                Toast.makeText(MainActivity.this, "Profile imported", Toast.LENGTH_SHORT).show();
                maybeRunHoldTest();
                refreshStatusAndLog();
            }

            @Override
            public void onDiscard() {
                LogStore.append(MainActivity.this, "vpn", "Profile import discarded");
            }
        });
    }

    private void handleVpnCertImport(Uri uri) {
        String slot = pendingVpnCertSlot;
        pendingVpnCertSlot = null;
        if (slot == null) {
            return;
        }
        byte[] bytes = readUriBytes(uri, 512 * 1024);
        if (bytes == null) {
            Toast.makeText(this, "Could not read file", Toast.LENGTH_LONG).show();
            return;
        }
        OpenVpnValidationResult check = new OpenVpnValidationResult();
        OpenVpnProfileValidator.checkSlotMaterial(check, slot, bytes);
        if (!check.ok()) {
            LogStore.append(this, "vpn", "Cert import rejected slot=" + slot + ": " + check.errors.get(0));
            Toast.makeText(this, check.errors.get(0), Toast.LENGTH_LONG).show();
            return;
        }
        OpenVpnProfileStore.writeSlot(this, slot, bytes);
        // If the key turned out encrypted, reflect that in prefs-driven UI via meta.
        if ("key".equals(slot) && check.keyEncrypted) {
            LogStore.append(this, "vpn", "Imported key is passphrase-protected");
        }
        LogStore.append(this, "vpn", "Imported cert slot=" + slot);
        Toast.makeText(this, vpnSlotLabel(slot) + " imported", Toast.LENGTH_SHORT).show();
        maybeRunHoldTest();
        refreshStatusAndLog();
    }

    private void maybeRunHoldTest() {
        if (!OpenVpnProfileStore.readMeta(this).allSlotsSatisfied()) {
            return;
        }
        OpenVpnHoldTester.runHoldTestAsync(this, new OpenVpnHoldTester.HoldCallback() {
            @Override
            public void onResult(final boolean passed, final String version, final String openssl,
                                 final String failureTail) {
                OpenVpnProfileStore.updateAfterHoldTest(MainActivity.this, passed, version, openssl, failureTail);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (passed) {
                            Toast.makeText(MainActivity.this, "Validated with " + version, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Validation failed: " + failureTail, Toast.LENGTH_LONG).show();
                        }
                        refreshStatusAndLog();
                    }
                });
            }
        });
    }

    private void editVpnProfile() {
        if (!OpenVpnProfileStore.hasProfile(this)) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String text = OpenVpnProfileStore.readProfileText(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        OpenVpnEditDialog.show(MainActivity.this, text, new OpenVpnEditDialog.Listener() {
                            @Override
                            public void onSave(String edited) {
                                saveEditedVpnProfile(edited);
                            }
                        });
                    }
                });
            }
        }, "VpnProfileRead").start();
    }

    private void saveEditedVpnProfile(final String edited) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                OpenVpnProfileStore.writeProfileText(MainActivity.this, edited);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        LogStore.append(MainActivity.this, "vpn", "Profile edited manually");
                        Toast.makeText(MainActivity.this, "Profile saved", Toast.LENGTH_SHORT).show();
                        maybeRunHoldTest();
                        refreshStatusAndLog();
                    }
                });
            }
        }, "VpnProfileWrite").start();
    }

    private void vpnClearProfile() {
        OpenVpnConfirmDialog.show(this, "Clear VPN profile?",
                "This deletes the imported profile and all certificate files. Saved username/password are kept.",
                "Clear", true, new OpenVpnConfirmDialog.Listener() {
                    @Override
                    public void onConfirm() {
                        OpenVpnManager.disconnect(MainActivity.this, "clear-profile");
                        OpenVpnProfileStore.clear(MainActivity.this);
                        LogStore.append(MainActivity.this, "vpn", "Profile cleared");
                        Toast.makeText(MainActivity.this, "Profile cleared", Toast.LENGTH_SHORT).show();
                        refreshStatusAndLog();
                    }
                });
    }

    private void vpnConnect() {
        if (!OpenVpnProfileStore.hasProfile(this)) {
            Toast.makeText(this, "Import a profile first", Toast.LENGTH_LONG).show();
            return;
        }
        OpenVpnProfileStore.Meta meta = OpenVpnProfileStore.readMeta(this);
        if (!meta.allSlotsSatisfied()) {
            Toast.makeText(this, "Import the missing certificate files first", Toast.LENGTH_LONG).show();
            return;
        }
        if (meta.keyEncrypted && Config.get(this).vpnKeyPassphrase().isEmpty()) {
            Toast.makeText(this, "Enter the private key passphrase", Toast.LENGTH_LONG).show();
            return;
        }
        if (meta.authUserPass && Config.get(this).vpnUsername().isEmpty()) {
            Toast.makeText(this, "Enter the VPN username", Toast.LENGTH_LONG).show();
            return;
        }
        Intent consent = OpenVpnManager.consentIntentOrNull(this);
        if (consent == null) {
            OpenVpnManager.connect(this, "ui-connect");
            Toast.makeText(this, "VPN connect requested", Toast.LENGTH_SHORT).show();
            refreshStatusAndLog();
            return;
        }
        pendingVpnConnectAfterConsent = true;
        try {
            startActivityForResult(consent, REQUEST_VPN_CONSENT);
        } catch (RuntimeException e) {
            pendingVpnConnectAfterConsent = false;
            LogStore.append(this, "vpn", "VPN consent launch failed: " + e.getMessage());
            Toast.makeText(this, "Could not request VPN permission", Toast.LENGTH_LONG).show();
        }
    }

    private void vpnDisconnect() {
        OpenVpnManager.disconnect(this, "ui-disconnect");
        Toast.makeText(this, "VPN disconnect requested", Toast.LENGTH_SHORT).show();
        refreshStatusAndLog();
    }

    private boolean saveVpnConfigOnly() {
        boolean tapSettingsVisible = vpnTapSection != null
                && vpnTapSection.getVisibility() == View.VISIBLE;
        if (tapSettingsVisible
                && (!requireIpv4(vpnTapStaticIpField, "VPN static IP", true)
                        || !requireSubnetMask(vpnTapNetmaskField, "VPN netmask")
                        || !requireIpv4(vpnTapGatewayField, "VPN gateway", true))) {
            return false;
        }
        Config.get(this).saveVpnConfig(
                text(vpnUsernameField),
                text(vpnPasswordField),
                text(vpnKeyPassphraseField),
                text(vpnTapStaticIpField),
                text(vpnTapNetmaskField),
                text(vpnTapGatewayField),
                vpnRemoteCommandEnabledSwitch.isChecked());
        refreshStatusAndLog();
        return true;
    }

    private boolean saveVpnTogglesOnly() {
        Config.get(this).saveVpnRemoteCommandConfig(vpnRemoteCommandEnabledSwitch.isChecked());
        refreshStatusAndLog();
        return true;
    }

    private boolean saveBeaconConfigOnly() {
        if (!requireInteger(beaconMajorField, "Beacon major", 0, 65535)
                || !requireInteger(beaconMinorField, "Beacon minor", 0, 65535)
                || !requireInteger(beaconMeasuredPowerField, "Beacon measured power", -127, 0)) {
            return false;
        }
        Config.get(this).saveBeaconConfig(
                beaconEnabledSwitch.isChecked(),
                text(beaconMajorField),
                text(beaconMinorField),
                text(beaconMeasuredPowerField),
                beaconTxPowerDbm);
        // refresh() starts the service when enabled and stops it when not, and
        // additionally makes a running service rebuild its advertisement.
        BeaconManager.refresh(this, "settings-saved");
        refreshStatusAndLog();
        return true;
    }

    private byte[] readUriBytes(Uri uri, int maxBytes) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return null;
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    LogStore.append(this, "vpn", "Import aborted; file exceeds " + maxBytes + " bytes");
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Exception e) {
            LogStore.append(this, "vpn", "Import read failed: " + e.getMessage());
            return null;
        }
    }

    private void addHistoryEntry(NotificationHistoryStore.Entry entry) {
        LinearLayout item = newColumn();
        item.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        item.setBackgroundColor(entry.highPriority ? COLOR_DANGER_CONTAINER : COLOR_FIELD_BG);

        LinearLayout meta = newRow();
        item.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView source = historyText(entry.source.toUpperCase(Locale.US), 10,
                entry.highPriority ? COLOR_DANGER_ON_CONTAINER : COLOR_PRIMARY, true);
        source.setLetterSpacing(0.12f);
        source.setSingleLine(true);
        source.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(source, inRow(meta, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView time = historyText(formatHistoryTime(entry.timestampMillis), 11, COLOR_TEXT_FAINT, false);
        time.setSingleLine(true);
        meta.addView(time, inRow(meta, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        TextView title = historyText(historyDisplayTitle(entry), 14,
                entry.highPriority ? COLOR_DANGER_ON_CONTAINER : COLOR_TEXT, true);
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

            LinearLayout imageActions = newRow();
            item.addView(imageActions, topMarginParams(8));
            addRowButton(imageActions, tonalButton("Save", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    saveNotificationImage(entry);
                }
            }));
            addRowButton(imageActions, tonalButton("Share", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    shareNotificationImage(entry);
                }
            }));
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

        Bitmap cached = notificationImageCache.get(entry.imageFileName);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        if (cached != null) {
            notificationImageCache.remove(entry.imageFileName);
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
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (decoded != null) {
            notificationImageCache.put(entry.imageFileName, decoded);
        }
        return decoded;
    }

    private void shareNotificationImage(NotificationHistoryStore.Entry entry) {
        File file = NotificationHistoryStore.imageFile(this, entry);
        if (file == null) {
            Toast.makeText(this, "Image is no longer available", Toast.LENGTH_LONG).show();
            return;
        }
        Uri uri = NotificationImageProvider.uriFor(entry.imageFileName);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(NotificationImageProvider.mimeOf(file));
        share.putExtra(Intent.EXTRA_STREAM, uri);
        // ClipData (with the read grant) lets the system share sheet render the
        // image preview; without it some OEM sheets show a placeholder "?".
        share.setClipData(ClipData.newUri(getContentResolver(), "Notification image", uri));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(share, "Share image"));
            LogStore.append(this, "history", "Notification image shared source=" + entry.source);
        } catch (RuntimeException e) {
            LogStore.append(this, "history", "Notification image share failed: " + e.getMessage());
            Toast.makeText(this, "Could not share image", Toast.LENGTH_LONG).show();
        }
    }

    private void saveNotificationImage(NotificationHistoryStore.Entry entry) {
        File file = NotificationHistoryStore.imageFile(this, entry);
        if (file == null) {
            Toast.makeText(this, "Image is no longer available", Toast.LENGTH_LONG).show();
            return;
        }
        pendingImageSaveEntry = entry;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_TITLE, historyImageFileName(entry));
        try {
            startActivityForResult(intent, REQUEST_SAVE_NOTIFICATION_IMAGE);
        } catch (RuntimeException e) {
            pendingImageSaveEntry = null;
            LogStore.append(this, "history", "Notification image save picker failed: " + e.getMessage());
            Toast.makeText(this, "Could not open image save picker", Toast.LENGTH_LONG).show();
        }
    }

    private void handleNotificationImageSave(Uri uri) {
        NotificationHistoryStore.Entry entry = pendingImageSaveEntry;
        pendingImageSaveEntry = null;
        File file = NotificationHistoryStore.imageFile(this, entry);
        if (file == null) {
            Toast.makeText(this, "Image is no longer available", Toast.LENGTH_LONG).show();
            return;
        }
        try (InputStream input = new FileInputStream(file);
             OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IllegalStateException("Output stream unavailable");
            }
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            LogStore.append(this, "history", "Notification image saved source=" + entry.source);
            Toast.makeText(this, "Image saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            LogStore.append(this, "history", "Notification image save failed: " + e.getMessage());
            Toast.makeText(this, "Image save failed", Toast.LENGTH_LONG).show();
        }
    }

    private String historyImageFileName(NotificationHistoryStore.Entry entry) {
        String label = hasText(entry.title) ? entry.title : entry.message;
        label = label == null ? "" : label.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-");
        label = label.replaceAll("^-+|-+$", "");
        if (label.length() > 36) {
            label = label.substring(0, 36).replaceAll("-+$", "");
        }
        if (label.isEmpty()) {
            label = "notification";
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date(entry.timestampMillis > 0L ? entry.timestampMillis : System.currentTimeMillis()));
        return "system-manager-" + timestamp + "-" + label + ".jpg";
    }

    private static int notificationImageCacheSizeKb() {
        long suggestedKb = Runtime.getRuntime().maxMemory() / (16L * 1024L);
        return (int) Math.max(4L * 1024L, Math.min(16L * 1024L, suggestedKb));
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
                                        notificationImageCache.remove(entry.imageFileName);
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
    //  Beacon panel
    // ============================================================

    private void refreshBeaconPanel() {
        if (beaconPill == null) {
            return;
        }
        Config config = Config.get(this);
        boolean enabled = switchValue(beaconEnabledSwitch, config.beaconEnabled());
        String state = enabled ? BeaconStateStore.state(this) : BeaconStateStore.STATE_OFF;
        // Before the service has had a chance to report, show the reason it
        // would fail rather than a stale state.
        if (enabled && !BeaconService.isActive()) {
            String blocking = BeaconManager.blockingState(this);
            if (blocking != null) {
                state = blocking;
            }
        }
        renderBeaconPill(enabled, state);
        renderBeaconStatus(config, enabled, state);
        renderBeaconRules(config);
        if (beaconUuidValue != null) {
            beaconUuidValue.setText(config.beaconUuid().toString());
        }
    }

    private void renderBeaconPill(boolean enabled, String state) {
        if (!enabled) {
            setPillState(beaconPill, "DISABLED", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
            return;
        }
        if (BeaconStateStore.STATE_ADVERTISING.equals(state)) {
            setPillState(beaconPill, "BROADCASTING", COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
            return;
        }
        if (BeaconStateStore.STATE_PAUSED.equals(state)) {
            setPillState(beaconPill, "PAUSED", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
            return;
        }
        setPillState(beaconPill, "ATTENTION", COLOR_DANGER_CONTAINER, COLOR_DANGER_ON_CONTAINER);
    }

    private void renderBeaconStatus(Config config, boolean enabled, String state) {
        if (beaconStatusList == null) {
            return;
        }
        beaconStatusList.removeAllViews();

        boolean advertising = enabled && BeaconStateStore.STATE_ADVERTISING.equals(state);
        boolean healthy = advertising || BeaconStateStore.STATE_PAUSED.equals(state);
        String detail = enabled ? BeaconStateStore.detail(this) : "";
        String stateText = BeaconStateStore.label(state);
        if (!enabled) {
            stateText = "Off";
        } else if (!detail.isEmpty() && !advertising) {
            stateText = stateText + " — " + detail;
        }
        addInfoRow(beaconStatusList, "State", stateText,
                enabled ? (healthy ? COLOR_OK : COLOR_BAD) : COLOR_TEXT_DIM);

        // Android hands out a rotating private address and won't tell an
        // ordinary app what it is, so say so plainly instead of showing the
        // placeholder the adapter returns.
        String address = BeaconAdvertiser.localAddress(this);
        addInfoRow(beaconStatusList, "Broadcast MAC",
                address.isEmpty() ? "Randomised by Android" : address,
                address.isEmpty() ? COLOR_TEXT_DIM : COLOR_TEXT);

        int liveInterval = BeaconStateStore.intervalSeconds(this);
        int requestedInterval = BeaconStateStore.requestedIntervalSeconds(this);
        String frequency;
        if (advertising) {
            frequency = Config.beaconIntervalDisplay(liveInterval);
            if (liveInterval != requestedInterval && requestedInterval > 0) {
                frequency = frequency + " (asked for " + requestedInterval + "s)";
            }
        } else {
            frequency = "Not broadcasting";
        }
        addInfoRow(beaconStatusList, "Frequency", frequency, advertising ? COLOR_TEXT : COLOR_TEXT_DIM);

        Config.BeaconRule activeRule = enabled ? config.beaconRuleById(BeaconStateStore.ruleId(this)) : null;
        addInfoRow(beaconStatusList, "Active rule",
                activeRule == null ? "None" : activeRule.displayThreshold() + " → " + activeRule.displayInterval(),
                activeRule == null ? COLOR_TEXT_DIM : COLOR_TEXT);

        int battery = BeaconStateStore.batteryPercent(this);
        if (battery < 0) {
            battery = BatteryReader.batteryPercent(this);
        }
        addInfoRow(beaconStatusList, "Battery", battery < 0 ? "Unknown" : battery + "%", COLOR_TEXT);

        addInfoRow(beaconStatusList, "Transmit power",
                Config.beaconTxPowerDisplay(beaconTxPowerDbm), COLOR_TEXT);
        addInfoRow(beaconStatusList, "Major / minor",
                config.beaconMajor() + " / " + config.beaconMinor(), COLOR_TEXT);

        if (advertising) {
            long since = BeaconStateStore.advertisingSinceMillis(this);
            if (since > 0L) {
                addInfoRow(beaconStatusList, "Broadcasting for",
                        formatDuration(System.currentTimeMillis() - since), COLOR_TEXT);
            }
            if (BeaconStateStore.legacyFallback(this)) {
                addInfoRow(beaconStatusList, "Radio mode",
                        "Legacy fallback — interval approximate", COLOR_BAD);
            }
        }
    }

    private void renderBeaconRules(Config config) {
        if (beaconRuleList == null) {
            return;
        }
        List<Config.BeaconRule> rules = config.beaconRules();
        beaconRuleList.removeAllViews();
        if (rules.isEmpty()) {
            TextView empty = historyText("No rules — the beacon stays silent", 13, COLOR_TEXT_DIM, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
            beaconRuleList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        String activeRuleId = switchValue(beaconEnabledSwitch, config.beaconEnabled())
                ? BeaconStateStore.ruleId(this)
                : "";
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) {
                View hairline = new View(this);
                hairline.setBackgroundColor(COLOR_FIELD_BORDER);
                beaconRuleList.addView(hairline, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
            addBeaconRuleRow(rules.get(i), rules.get(i).id.equals(activeRuleId));
        }
    }

    private void addBeaconRuleRow(final Config.BeaconRule rule, boolean active) {
        LinearLayout item = newColumn();
        item.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));

        LinearLayout top = newRow();
        item.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView threshold = historyText(rule.displayThreshold(), 16, COLOR_TEXT, true);
        top.addView(threshold, inRow(top, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (active) {
            TextView activePill = new TextView(this);
            activePill.setText("ACTIVE");
            activePill.setTextSize(10);
            activePill.setTypeface(Typeface.DEFAULT_BOLD);
            activePill.setLetterSpacing(0.12f);
            activePill.setTextColor(COLOR_PRIMARY_ON_CONTAINER);
            activePill.setBackground(roundedFill(COLOR_PRIMARY_CONTAINER, PILL_CORNER, 0, 0));
            activePill.setPadding(dp(GAP), dp(6), dp(GAP), dp(6));
            activePill.setIncludeFontPadding(false);
            Ui.marqueeLabel(activePill);
            top.addView(activePill, inRow(top,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0f));
        }

        Button delete = neutralButton("Delete", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteBeaconRule(rule);
            }
        });
        top.addView(delete, inRow(top, dp(96), dp(BUTTON_MIN_HEIGHT), 0f));

        TextView detail = historyText(rule.displayInterval(), 12,
                rule.broadcasts() ? COLOR_TEXT_DIM : COLOR_BAD, false);
        detail.setLineSpacing(0, 1.2f);
        item.addView(detail, topMarginParams(6));

        beaconRuleList.addView(item, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addBeaconRule() {
        try {
            Config.BeaconRule rule = Config.get(this).addBeaconRule(
                    text(beaconRuleBatteryField), text(beaconRuleIntervalField));
            beaconRuleBatteryField.setText("");
            beaconRuleIntervalField.setText("");
            LogStore.append(this, "beacon", "Rule added " + rule.displayThreshold()
                    + " → " + rule.displayInterval());
            BeaconManager.refresh(this, "rule-added");
            refreshStatusAndLog();
            Toast.makeText(this, "Rule added", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void deleteBeaconRule(Config.BeaconRule rule) {
        Config.get(this).removeBeaconRule(rule.id);
        LogStore.append(this, "beacon", "Rule removed " + rule.displayThreshold());
        BeaconManager.refresh(this, "rule-removed");
        refreshStatusAndLog();
        Toast.makeText(this, "Rule deleted", Toast.LENGTH_SHORT).show();
    }

    private void copyBeaconUuid() {
        String uuid = Config.get(this).beaconUuid().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Clipboard unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Beacon UUID", uuid));
        Toast.makeText(this, "Beacon UUID copied", Toast.LENGTH_SHORT).show();
    }

    private void regenerateBeaconUuid() {
        OpenVpnConfirmDialog.show(this, "Generate a new UUID?",
                "Receivers matching the current UUID will stop seeing this phone until you update them.",
                "Generate", true, new OpenVpnConfirmDialog.Listener() {
                    @Override
                    public void onConfirm() {
                        String uuid = Config.get(MainActivity.this).regenerateBeaconUuid().toString();
                        LogStore.append(MainActivity.this, "beacon", "Beacon UUID regenerated");
                        BeaconManager.refresh(MainActivity.this, "uuid-regenerated");
                        refreshStatusAndLog();
                        Toast.makeText(MainActivity.this, "New UUID: " + uuid, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openBluetoothSettings() {
        openIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void requestBluetoothAdvertise() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(this, "Bluetooth advertising needs no runtime permission on this Android version",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (PermissionState.hasBluetoothAdvertise(this)) {
            Toast.makeText(this, "Bluetooth advertise already granted", Toast.LENGTH_SHORT).show();
            refreshStatusAndLog();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE}, REQUEST_BLUETOOTH_ADVERTISE);
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0L) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
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
        titleView.setPadding(dp(4), 0, 0, 0);
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
        indicator.setTextSize(20);
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

    /**
     * Shuts every panel and returns the page to the top, without animating —
     * this runs while the window is off screen, so there is nothing to watch.
     */
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
        final ScrollView scrollView = contentScrollView;
        if (scrollView != null) {
            scrollView.scrollTo(0, 0);
            // The collapse changes the content height, so the position has to
            // be reasserted once the new layout has been through a pass.
            scrollView.post(new Runnable() {
                @Override
                public void run() {
                    scrollView.scrollTo(0, 0);
                }
            });
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
        v.setIncludeFontPadding(false);
        // The pill is a fixed width, so a long state word would otherwise be
        // cut off mid-letter.
        Ui.marqueeLabel(v);
        return v;
    }

    private GradientDrawable roundedFill(int color, int cornerDp, int strokeDp, int strokeColor) {
        return Ui.roundedFill(color, dp(cornerDp), strokeDp > 0 ? dp(strokeDp) : 0, strokeColor);
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
        return Ui.button(this, text, bg, fg, ripple, listener);
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
        if ("volume".equals(tag)) return 0xFFB8F2C2;
        if ("vpn".equals(tag)) return 0xFFA8B4FF;
        return 0xFFE3EBD9;
    }

    // ============================================================
    //  Fields, toggles, status rows
    // ============================================================

    private EditText addField(LinearLayout root, String label, int inputType) {
        addFieldLabel(root, label);

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

    private void addFieldLabel(LinearLayout root, String label) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(COLOR_LABEL);
        labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelView.setLetterSpacing(0.02f);
        labelView.setIncludeFontPadding(false);
        root.addView(labelView, stack(root));
    }

    private Button addPickerButton(LinearLayout root, String label, String text, View.OnClickListener listener) {
        addFieldLabel(root, label);
        Button button = tonalButton(text, listener);
        root.addView(button, stack(root));
        return button;
    }

    private VolumeInput addVolumeInput(LinearLayout root, String label) {
        LinearLayout box = newColumn();
        box.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        box.setPadding(dp(GAP), dp(GAP), dp(GAP), dp(GAP));
        root.addView(box, stack(root));

        LinearLayout top = newRow();
        box.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setIncludeFontPadding(false);
        top.addView(title, inRow(top, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView valueView = new TextView(this);
        valueView.setTextColor(COLOR_TEXT_DIM);
        valueView.setTextSize(12);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setIncludeFontPadding(false);
        top.addView(valueView, inRow(top, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        LinearLayout control = newRow();
        box.addView(control, topMarginParams(8));

        final SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(50);
        control.addView(slider, inRow(control, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout unchanged = newRow();
        unchanged.setPadding(dp(GAP), 0, 0, 0);
        TextView unchangedLabel = new TextView(this);
        unchangedLabel.setText("Unchanged");
        unchangedLabel.setTextColor(COLOR_TEXT_DIM);
        unchangedLabel.setTextSize(12);
        unchangedLabel.setSingleLine(true);
        unchangedLabel.setIncludeFontPadding(false);
        unchanged.addView(unchangedLabel, inRow(unchanged, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        final Switch unchangedSwitch = makeSwitch();
        unchangedSwitch.setChecked(true);
        unchanged.addView(unchangedSwitch, inRow(unchanged, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));
        control.addView(unchanged, inRow(control, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        final VolumeInput input = new VolumeInput(slider, unchangedSwitch, valueView);
        unchanged.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                unchangedSwitch.toggle();
            }
        });
        unchangedSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateVolumeInput(input);
            }
        });
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && unchangedSwitch.isChecked()) {
                    unchangedSwitch.setChecked(false);
                }
                updateVolumeInput(input);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        updateVolumeInput(input);
        return input;
    }

    private void addDndModeInput(LinearLayout root) {
        addFieldLabel(root, "Do Not Disturb");
        LinearLayout row = newRow();
        root.addView(row, stack(root));
        volumeRuleDndUnchangedButton = tonalButton("Unchanged", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setVolumeRuleDndMode(Config.DND_UNCHANGED);
            }
        });
        volumeRuleDndEnableButton = tonalButton("Enable", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setVolumeRuleDndMode(Config.DND_ENABLE);
            }
        });
        volumeRuleDndDisableButton = tonalButton("Disable", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setVolumeRuleDndMode(Config.DND_DISABLE);
            }
        });
        addRowButton(row, volumeRuleDndUnchangedButton);
        addRowButton(row, volumeRuleDndEnableButton);
        addRowButton(row, volumeRuleDndDisableButton);
        updateVolumeRuleDndButtons();
    }

    private void showVolumeRuleTimePicker() {
        int initialHour = volumeRuleHour >= 0 ? volumeRuleHour : 17;
        int initialMinute = volumeRuleMinute >= 0 ? volumeRuleMinute : 0;
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(android.widget.TimePicker view, int hourOfDay, int minute) {
                        setVolumeRuleTime(hourOfDay, minute);
                    }
                },
                initialHour,
                initialMinute,
                true);
        dialog.show();
    }

    private void setVolumeRuleTime(int hour, int minute) {
        volumeRuleHour = clamp(hour, 0, 23);
        volumeRuleMinute = clamp(minute, 0, 59);
        if (volumeRuleTimeButton != null) {
            volumeRuleTimeButton.setText(formatRuleTime(volumeRuleHour, volumeRuleMinute));
        }
    }

    private void resetVolumeRuleInputs() {
        volumeRuleHour = -1;
        volumeRuleMinute = -1;
        if (volumeRuleTimeButton != null) {
            volumeRuleTimeButton.setText("Select Time");
        }
        resetVolumeInput(volumeRuleMediaInput);
        resetVolumeInput(volumeRuleRingInput);
        resetVolumeInput(volumeRuleNotificationInput);
        resetVolumeInput(volumeRuleAlarmInput);
        setVolumeRuleDndMode(Config.DND_UNCHANGED);
    }

    private void resetVolumeInput(VolumeInput input) {
        if (input == null) {
            return;
        }
        input.slider.setProgress(50);
        input.unchangedSwitch.setChecked(true);
        updateVolumeInput(input);
    }

    private int volumeInputValue(VolumeInput input) {
        if (input == null || input.unchangedSwitch.isChecked()) {
            return Config.VOLUME_UNCHANGED;
        }
        return input.slider.getProgress();
    }

    private void updateVolumeInput(VolumeInput input) {
        if (input == null) {
            return;
        }
        boolean unchanged = input.unchangedSwitch.isChecked();
        input.slider.setEnabled(!unchanged);
        input.slider.setAlpha(unchanged ? 0.45f : 1f);
        input.valueView.setText(unchanged ? "UNCHANGED" : input.slider.getProgress() + "%");
        input.valueView.setTextColor(unchanged ? COLOR_TEXT_FAINT : COLOR_PRIMARY);
    }

    private void setVolumeRuleDndMode(int mode) {
        if (mode == Config.DND_ENABLE || mode == Config.DND_DISABLE) {
            volumeRuleDndMode = mode;
        } else {
            volumeRuleDndMode = Config.DND_UNCHANGED;
        }
        updateVolumeRuleDndButtons();
    }

    private void updateVolumeRuleDndButtons() {
        styleChoiceButton(volumeRuleDndUnchangedButton, volumeRuleDndMode == Config.DND_UNCHANGED);
        styleChoiceButton(volumeRuleDndEnableButton, volumeRuleDndMode == Config.DND_ENABLE);
        styleChoiceButton(volumeRuleDndDisableButton, volumeRuleDndMode == Config.DND_DISABLE);
    }

    private void styleChoiceButton(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        int bg = selected ? COLOR_PRIMARY : COLOR_PRIMARY_CONTAINER;
        int fg = selected ? Color.WHITE : COLOR_PRIMARY_ON_CONTAINER;
        button.setTextColor(fg);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(selected ? COLOR_RIPPLE_DARK : COLOR_RIPPLE_LIGHT),
                roundedFill(bg, BUTTON_CORNER, 0, 0),
                null));
    }

    private String formatRuleTime(int hour, int minute) {
        return String.format(Locale.US, "%02d:%02d", hour, minute);
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

    /** Label/value row for read-only status cards, styled like {@link #addStatusRow}. */
    private void addInfoRow(LinearLayout root, String label, String value, int valueColor) {
        LinearLayout row = newRow();
        row.setMinimumHeight(dp(STATUS_ROW_MIN_HEIGHT));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(COLOR_LABEL);
        labelView.setTextSize(13);
        labelView.setIncludeFontPadding(false);
        row.addView(labelView, inRow(row, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(valueColor);
        valueView.setTextSize(13);
        valueView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valueView.setGravity(Gravity.END);
        valueView.setIncludeFontPadding(false);
        valueView.setLineSpacing(0, 1.15f);
        row.addView(valueView, inRow(row, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));

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
        refreshVolumeControlPanel();
        refreshOpenVpnPanel();
        refreshBeaconPanel();
        setEnabledPill(rebootPill, switchValue(rebootAutomationEnabledSwitch, config.rebootAutomationEnabled()));
        setRemoteLinkPill(RemoteLinkStateStore.isConnected(this));
        refreshNotificationBackupStatus();
        setEnabledPill(logPill, switchValue(logEnabledSwitch, config.logEnabled()));
        setSettingsBackupPill(config.settingsLastExportMillis());
        applyButtonState(startTrackingButton, !tracking, COLOR_PRIMARY, Color.WHITE);
        applyButtonState(stopTrackingButton, tracking, COLOR_DANGER, Color.WHITE);

        renderWifi(config, cachedWifi);

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
        boolean dndRuleConfigured = volumeRulesNeedDndAccess(config.volumeRules());
        boolean dndAccess = !dndRuleConfigured || PermissionState.notificationPolicyAccessGranted(this);
        boolean vpnProfileConfigured = OpenVpnProfileStore.hasProfile(this);
        boolean vpnConsent = !vpnProfileConfigured || PermissionState.vpnConsentGranted(this);
        boolean beaconEnabled = switchValue(beaconEnabledSwitch, config.beaconEnabled());
        boolean bluetoothAdvertise = !beaconEnabled || PermissionState.hasBluetoothAdvertise(this);
        boolean bluetoothOn = !beaconEnabled || BeaconAdvertiser.isBluetoothOn(this);
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
                && accessibility
                && dndAccess
                && vpnConsent
                && bluetoothAdvertise
                && bluetoothOn;
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
        if (dndRuleConfigured) {
            addStatusRow(statusContainer, "DND access", dndAccess);
        }
        if (vpnProfileConfigured) {
            addStatusRow(statusContainer, "VPN consent", vpnConsent);
        }
        if (beaconEnabled) {
            addStatusRow(statusContainer, "Bluetooth advertise", bluetoothAdvertise);
            addStatusRow(statusContainer, "Bluetooth on", bluetoothOn);
        }
        if (hiddenWifiMonitorNeedsAccessibility) {
            addStatusRow(statusContainer, "Hidden Wi-Fi monitor", false);
        }

        // Wi-Fi identity and the log file are read off the main thread and
        // applied when ready. The Wi-Fi lookup can block ~1.2s while identity
        // is resolving (e.g. right after a network change), which would freeze
        // the UI if done inline — this method is called on every state-change
        // broadcast.
        scheduleStatusIo();
    }

    private void renderWifi(Config config, WifiSnapshot wifi) {
        if (wifi == null) {
            wifi = WifiSnapshot.disconnected("reading Wi-Fi…");
        }
        boolean ssidMatches = PatternMatcher.simpleMatch(config.ssidPattern(), wifi.ssid, config.caseSensitiveSsid());
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
    }

    /**
     * Reads the Wi-Fi snapshot and log tail off the main thread and applies
     * them when done. Coalesces bursts via an in-flight guard; a refresh
     * requested mid-read triggers exactly one more read so the display stays
     * eventually consistent without a tight loop.
     */
    private void scheduleStatusIo() {
        if (statusIoInFlight) {
            statusIoRequested = true;
            return;
        }
        statusIoInFlight = true;
        statusIoRequested = false;
        final Config config = Config.get(this);
        final int maxLines = Math.min(config.logMaxLines(), 300);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final WifiSnapshot wifi = WifiInfoReader.read(MainActivity.this);
                final CharSequence log = colorizeLog(LogStore.readTail(MainActivity.this, maxLines));
                cachedWifi = wifi;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusIoInFlight = false;
                        if (statusContainer != null && logView != null) {
                            renderWifi(config, wifi);
                            logView.setText(log);
                        }
                        if (statusIoRequested) {
                            statusIoRequested = false;
                            scheduleStatusIo();
                        }
                    }
                });
            }
        }, "SystemManagerStatusIo").start();
    }

    private void seedWifiStateAsync(final String reason) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                NetworkStateStore.seedIfMissing(MainActivity.this, WifiInfoReader.read(MainActivity.this), reason);
            }
        }, "SystemManagerSeedWifi").start();
    }

    private boolean volumeRulesNeedDndAccess(List<Config.VolumeRule> rules) {
        if (rules == null) {
            return false;
        }
        for (Config.VolumeRule rule : rules) {
            if (rule.dndMode == Config.DND_ENABLE || rule.dndMode == Config.DND_DISABLE) {
                return true;
            }
        }
        return false;
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

    private void registerNotificationBackupReceiver() {
        if (notificationBackupReceiver != null) {
            return;
        }
        notificationBackupReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshNotificationBackupStatus();
            }
        };
        IntentFilter filter = new IntentFilter(NotificationBackupStateStore.ACTION_STATE_CHANGED);
        filter.addAction(NotificationBackupStore.ACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationBackupReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notificationBackupReceiver, filter);
        }
    }

    private void unregisterNotificationBackupReceiver() {
        if (notificationBackupReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(notificationBackupReceiver);
        } catch (RuntimeException ignored) {
        }
        notificationBackupReceiver = null;
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
        CharSequence previous = target.getText();
        boolean textChanged = previous == null || !text.contentEquals(previous);
        target.setText(text);
        target.setTextColor(fg);
        target.setBackground(roundedFill(bg, PILL_CORNER, 0, 0));
        if (textChanged) {
            // A state word that is longer than the last one has to be given a
            // fresh chance to scroll rather than sitting there clipped.
            Ui.rearmMarquee(target);
        }
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
            clearNotificationsOnOpenSwitch.setChecked(config.clearNotificationsOnOpen());
            notificationBackupEnabledSwitch.setChecked(config.notificationBackupEnabled());
            notificationBackupIncludeSysmgrSwitch.setChecked(config.notificationBackupIncludeSysmgr());
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
            gpsUseRemoteLinkSwitch.setChecked(config.gpsUseRemoteLink());
            logEnabledSwitch.setChecked(config.logEnabled());
            logMaxLinesField.setText(Integer.toString(config.logMaxLines()));
            highPriorityEnabledSwitch.setChecked(config.highPriorityEnabled());
            highPriorityPackageField.setText(appDisplayText(config.highPriorityPackage()));
            AlertTextFilter highPriorityFilter = config.highPriorityFilter();
            highPriorityTitleFilterField.setText(highPriorityFilter.titleContains);
            highPriorityTitleExcludeField.setText(highPriorityFilter.titleExcludes);
            highPriorityTextFilterField.setText(highPriorityFilter.messageContains);
            highPriorityTextExcludeField.setText(highPriorityFilter.messageExcludes);
            highPriorityRemoteEnabledSwitch.setChecked(config.highPriorityRemoteEnabled());
            AlertTextFilter highPriorityRemoteFilter = config.highPriorityRemoteFilter();
            highPriorityRemoteTitleFilterField.setText(highPriorityRemoteFilter.titleContains);
            highPriorityRemoteTitleExcludeField.setText(highPriorityRemoteFilter.titleExcludes);
            highPriorityRemoteTextFilterField.setText(highPriorityRemoteFilter.messageContains);
            highPriorityRemoteTextExcludeField.setText(highPriorityRemoteFilter.messageExcludes);
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
            resetVolumeRuleInputs();
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
            vpnUsernameField.setText(config.vpnUsername());
            vpnPasswordField.setText(config.vpnPassword());
            vpnKeyPassphraseField.setText(config.vpnKeyPassphrase());
            vpnTapStaticIpField.setText(config.vpnTapStaticIp());
            vpnTapNetmaskField.setText(config.vpnTapNetmask());
            vpnTapGatewayField.setText(config.vpnTapGateway());
            vpnRemoteCommandEnabledSwitch.setChecked(config.vpnRemoteCommandEnabled());
            beaconEnabledSwitch.setChecked(config.beaconEnabled());
            beaconUuidValue.setText(config.beaconUuid().toString());
            beaconMajorField.setText(Integer.toString(config.beaconMajor()));
            beaconMinorField.setText(Integer.toString(config.beaconMinor()));
            beaconMeasuredPowerField.setText(Integer.toString(config.beaconMeasuredPower()));
            beaconTxPowerDbm = config.beaconTxPowerDbm();
            updateBeaconTxPowerButtons();
            useExactAlarmsSwitch.setChecked(config.useExactAlarms());
            allowIdleAlarmsSwitch.setChecked(config.allowIdleAlarms());
            postOnStartupSwitch.setChecked(config.postOnStartup());
            postOnWifiChangeSwitch.setChecked(config.postOnWifiChange());
            showWifiMonitorNotificationSwitch.setChecked(config.showWifiMonitorNotification());
            taskServiceNotificationSwitch.setChecked(config.taskServiceNotificationEnabled());
            vpnNotificationSwitch.setChecked(config.vpnNotificationEnabled());
            beaconNotificationSwitch.setChecked(config.beaconNotificationEnabled());
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
        gpsSettingsSave = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveGpsConfigOnly();
            }
        });
        bindLiveEdits(gpsSettingsSave,
                serverBaseUrlField, trackPathField, ssidPatternField,
                highBatteryIntervalField, lowBatteryIntervalField, batteryThresholdField,
                locationTimeoutField, desiredAccuracyField, maxCachedLocationField,
                httpTimeoutField, fallbackLatitudeField, fallbackLongitudeField);
        bindLiveToggles(gpsSettingsSave, new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveGpsTogglesOnly();
            }
        },
                useExactAlarmsSwitch, allowIdleAlarmsSwitch, postOnStartupSwitch,
                postOnWifiChangeSwitch,
                useGpsProviderSwitch, useNetworkProviderSwitch,
                requestGpsOnSsidMismatchSwitch, useFallbackOnSsidMatchSwitch,
                useCachedBeforeFreshSwitch, includeExtendedFieldsSwitch,
                caseSensitiveSsidSwitch, gpsUseRemoteLinkSwitch);

        final LiveSaveGroup serviceNotifications = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveServiceNotificationConfigOnly();
            }
        });
        bindLiveToggles(serviceNotifications,
                taskServiceNotificationSwitch, vpnNotificationSwitch, beaconNotificationSwitch);

        final LiveSaveGroup wifiMonitorNotification = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                Config.get(MainActivity.this).saveWifiMonitorNotificationConfig(
                        showWifiMonitorNotificationSwitch.isChecked());
                NetworkMonitorService.sync(MainActivity.this);
                refreshStatusAndLog();
                return true;
            }
        });
        bindLiveToggles(wifiMonitorNotification, showWifiMonitorNotificationSwitch);

        highPrioritySettingsSave = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveHighPriorityConfigOnly();
            }
        });
        bindLiveEdits(highPrioritySettingsSave,
                highPriorityPackageField,
                highPriorityTitleFilterField, highPriorityTitleExcludeField,
                highPriorityTextFilterField, highPriorityTextExcludeField,
                highPriorityRemoteTitleFilterField, highPriorityRemoteTitleExcludeField,
                highPriorityRemoteTextFilterField, highPriorityRemoteTextExcludeField,
                highPriorityRemoteDedupeSecondsField, highPriorityToneTitleField,
                highPriorityPlaySecondsField, highPriorityDedupeSecondsField,
                highPriorityAlarmVolumePercentField);
        bindLiveToggles(highPrioritySettingsSave, new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveHighPriorityTogglesOnly();
            }
        },
                highPriorityEnabledSwitch, highPriorityRemoteEnabledSwitch,
                highPriorityRaiseAlarmVolumeSwitch);

        batteryAlertSettingsSave = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveBatteryAlertConfigOnly();
            }
        });
        bindLiveEdits(batteryAlertSettingsSave,
                batteryAlertThresholdField, batteryAlertCheckIntervalField,
                batteryAlertVibrateSecondsField);
        bindLiveToggles(batteryAlertSettingsSave, new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveBatteryAlertTogglesOnly();
            }
        },
                batteryAlertEnabledSwitch, batteryAlertUseExactAlarmsSwitch,
                batteryAlertAllowIdleAlarmsSwitch);

        rebootSettingsSave = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveRebootConfigOnly();
            }
        });
        bindLiveEdits(rebootSettingsSave,
                rebootTriggerPackageField, rebootTriggerTitleField, rebootTriggerTextField,
                rebootScheduleHourField, rebootScheduleMinuteField, rebootWifiPatternField,
                rebootPinSequenceField, rebootDelayedTestSecondsField,
                rebootPowerDialogWaitMsField, rebootStepWaitMsField);
        bindLiveToggles(rebootSettingsSave, new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveRebootTogglesOnly();
            }
        },
                rebootAutomationEnabledSwitch, rebootNotificationTriggerEnabledSwitch,
                rebootRemoteTriggerEnabledSwitch, rebootScheduleEnabledSwitch,
                rebootOnlyWhenWifiNotMatchingSwitch);

        remoteLinkSettingsSave = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveRemoteLinkConfigLive();
            }
        });
        bindLiveEdits(remoteLinkSettingsSave,
                remoteLinkEndpointField, remoteLinkUsernameField,
                remoteLinkPasswordField, remoteLinkHeartbeatSecondsField);
        bindLiveToggles(remoteLinkSettingsSave, new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveRemoteLinkTogglesOnly();
            }
        },
                remoteLinkEnabledSwitch, remoteLinkAcceptAnySslCertSwitch);

        final LiveSaveGroup remoteLinkNotification = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                Config.get(MainActivity.this).saveRemoteLinkNotificationConfig(
                        remoteLinkShowNotificationSwitch.isChecked());
                RemoteLinkManager.sync(MainActivity.this, "remote-link-notification-live");
                refreshStatusAndLog();
                return true;
            }
        });
        bindLiveToggles(remoteLinkNotification, remoteLinkShowNotificationSwitch);

        vpnSettingsSave = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveVpnConfigOnly();
            }
        });
        bindLiveEdits(vpnSettingsSave,
                vpnUsernameField, vpnPasswordField, vpnKeyPassphraseField,
                vpnTapStaticIpField, vpnTapNetmaskField, vpnTapGatewayField);
        bindLiveToggles(vpnSettingsSave, new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveVpnTogglesOnly();
            }
        }, vpnRemoteCommandEnabledSwitch);

        beaconSettingsSave = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveBeaconConfigOnly();
            }
        });
        bindLiveEdits(beaconSettingsSave,
                beaconMajorField, beaconMinorField, beaconMeasuredPowerField);

        beaconEnabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (loadingConfig) {
                    return;
                }
                boolean saved = beaconSettingsSave.saveNow();
                if (!saved && !isChecked) {
                    Config.get(MainActivity.this).setBeaconEnabled(false);
                    BeaconManager.refresh(MainActivity.this, "beacon-disabled-live");
                    refreshStatusAndLog();
                    showSettingSavedToast();
                    saved = true;
                }
                if (!saved) {
                    loadingConfig = true;
                    try {
                        buttonView.setChecked(!isChecked);
                    } finally {
                        loadingConfig = false;
                    }
                    return;
                }
                if (isChecked && !PermissionState.hasBluetoothAdvertise(MainActivity.this)) {
                    requestBluetoothAdvertise();
                }
            }
        });

        final LiveSaveGroup notificationBackupSettings = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveNotificationBackupConfigOnly();
            }
        });
        bindLiveToggles(notificationBackupSettings,
                notificationBackupEnabledSwitch, notificationBackupIncludeSysmgrSwitch);

        final LiveSaveGroup notificationHistorySettings = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                Config.get(MainActivity.this).saveNotificationHistoryConfig(
                        clearNotificationsOnOpenSwitch.isChecked());
                refreshStatusAndLog();
                return true;
            }
        });
        bindLiveToggles(notificationHistorySettings, clearNotificationsOnOpenSwitch);

        final LiveSaveGroup logSettings = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveLogConfigOnly();
            }
        });
        bindLiveEdits(logSettings, logMaxLinesField);
        bindLiveToggles(logSettings, new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveLogToggleOnly();
            }
        }, logEnabledSwitch);
    }

    private LiveSaveGroup liveSaveGroup(LiveSaveAction saveAction) {
        LiveSaveGroup group = new LiveSaveGroup(saveAction);
        liveSaveGroups.add(group);
        return group;
    }

    private void bindLiveEdits(final LiveSaveGroup group, EditText... fields) {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                group.schedule();
            }
        };
        for (EditText field : fields) {
            if (field != null) {
                field.addTextChangedListener(watcher);
            }
        }
    }

    private void bindLiveToggles(final LiveSaveGroup group, Switch... switches) {
        bindLiveToggles(group, null, switches);
    }

    private void bindLiveToggles(final LiveSaveGroup group,
                                 final LiveSaveAction disabledToggleSaveAction,
                                 Switch... switches) {
        for (Switch sw : switches) {
            if (sw == null) {
                continue;
            }
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (loadingConfig) {
                        return;
                    }
                    boolean saved = group.saveNow();
                    if (!saved && !isChecked && disabledToggleSaveAction != null) {
                        saved = disabledToggleSaveAction.save();
                        if (saved) {
                            showSettingSavedToast();
                        }
                    }
                    if (!saved) {
                        loadingConfig = true;
                        try {
                            buttonView.setChecked(!isChecked);
                        } finally {
                            loadingConfig = false;
                        }
                    }
                }
            });
        }
    }

    private boolean flushPendingLiveSaves() {
        boolean attempted = false;
        boolean allSaved = true;
        for (LiveSaveGroup group : liveSaveGroups) {
            if (!group.isPending()) {
                continue;
            }
            attempted = true;
            if (!group.saveNow(false)) {
                allSaved = false;
            }
        }
        if (attempted && allSaved) {
            showSettingSavedToast();
        }
        return allSaved;
    }

    private boolean flushLiveSaveGroup(LiveSaveGroup group) {
        if (group == null || !group.isPending()) {
            return true;
        }
        boolean saved = group.saveNow(false);
        if (saved) {
            showSettingSavedToast();
        }
        return saved;
    }

    private boolean flushSettingsForAction(String command) {
        if ("export_settings".equals(command)) {
            return flushPendingLiveSaves();
        }
        if ("start".equals(command) || "send".equals(command)) {
            return flushLiveSaveGroup(gpsSettingsSave);
        }
        if ("test_high_priority_alert".equals(command)) {
            return flushLiveSaveGroup(highPrioritySettingsSave);
        }
        if ("test_battery_alert".equals(command)) {
            return flushLiveSaveGroup(batteryAlertSettingsSave);
        }
        if ("test_reboot_now".equals(command) || "test_reboot_delayed".equals(command)) {
            return flushLiveSaveGroup(rebootSettingsSave);
        }
        if ("remote_link_reconnect".equals(command)) {
            return flushLiveSaveGroup(remoteLinkSettingsSave);
        }
        if ("vpn_connect".equals(command)) {
            return flushLiveSaveGroup(vpnSettingsSave);
        }
        return true;
    }

    private void showSettingSavedToast() {
        if (settingsFeedbackToast != null) {
            settingsFeedbackToast.cancel();
        }
        settingsFeedbackToast = Toast.makeText(
                getApplicationContext(), "Setting saved", Toast.LENGTH_SHORT);
        settingsFeedbackToast.show();
    }

    private boolean requireInteger(EditText field, String label, int min, int max) {
        try {
            int value = Integer.parseInt(text(field).trim());
            if (value < min || value > max) {
                return invalidSetting(field, label, "Use a value from " + min + " to " + max);
            }
            field.setError(null);
            return true;
        } catch (RuntimeException e) {
            return invalidSetting(field, label, "Use a value from " + min + " to " + max);
        }
    }

    private boolean requireDecimal(EditText field, String label, double min, double max) {
        try {
            double value = Double.parseDouble(text(field).trim());
            if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
                return invalidSetting(field, label, "Use a value from " + min + " to " + max);
            }
            field.setError(null);
            return true;
        } catch (RuntimeException e) {
            return invalidSetting(field, label, "Use a value from " + min + " to " + max);
        }
    }

    private boolean requireText(EditText field, String label) {
        if (text(field).trim().isEmpty()) {
            return invalidSetting(field, label, "This value is required");
        }
        field.setError(null);
        return true;
    }

    private boolean requireAppSelection(EditText field, String label) {
        if (appPackageFromField(field).trim().isEmpty()) {
            return invalidSetting(field, label, "Choose an app");
        }
        field.setError(null);
        return true;
    }

    private boolean requireEndpoint(EditText field, String label, String... allowedSchemes) {
        String value = text(field).trim();
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            boolean allowed = false;
            if (scheme != null) {
                for (String candidate : allowedSchemes) {
                    if (candidate.equalsIgnoreCase(scheme)) {
                        allowed = true;
                        break;
                    }
                }
            }
            int port = uri.getPort();
            if (!allowed
                    || uri.getHost() == null
                    || uri.getHost().trim().isEmpty()
                    || port == 0
                    || port > 65535) {
                return invalidSetting(field, label, "Use a complete server URL");
            }
            field.setError(null);
            return true;
        } catch (RuntimeException e) {
            return invalidSetting(field, label, "Use a complete server URL");
        }
    }

    private boolean requireIpv4(EditText field, String label, boolean optional) {
        String value = text(field).trim();
        if (optional && value.isEmpty()) {
            field.setError(null);
            return true;
        }
        if (strictIpv4Value(value) < 0L) {
            return invalidSetting(field, label, "Use a dotted IPv4 address");
        }
        field.setError(null);
        return true;
    }

    private boolean requireSubnetMask(EditText field, String label) {
        long mask = strictIpv4Value(text(field).trim());
        long inverted = (~mask) & 0xffffffffL;
        if (mask <= 0L || (inverted & (inverted + 1L)) != 0L) {
            return invalidSetting(field, label, "Use a contiguous subnet mask");
        }
        field.setError(null);
        return true;
    }

    private static long strictIpv4Value(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return -1L;
        }
        long result = 0L;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return -1L;
            }
            for (int i = 0; i < part.length(); i++) {
                char character = part.charAt(i);
                if (character < '0' || character > '9') {
                    return -1L;
                }
            }
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return -1L;
            }
            if (octet > 255) {
                return -1L;
            }
            result = (result << 8) | octet;
        }
        return result;
    }

    private boolean invalidSetting(EditText field, String label, String message) {
        field.setError(message);
        if (settingsFeedbackToast != null) {
            settingsFeedbackToast.cancel();
        }
        settingsFeedbackToast = Toast.makeText(
                getApplicationContext(), "Check " + label, Toast.LENGTH_SHORT);
        settingsFeedbackToast.show();
        return false;
    }

    /**
     * Saves the visibility choices and nudges each running service so it
     * re-posts on the right channel straight away.
     */
    private boolean saveServiceNotificationConfigOnly() {
        Config.get(this).saveServiceNotificationConfig(
                taskServiceNotificationSwitch.isChecked(),
                vpnNotificationSwitch.isChecked(),
                beaconNotificationSwitch.isChecked());
        SystemTaskService.refreshNotification();
        BeaconManager.sync(this, "service-notifications");
        OpenVpnService.refreshNotification(this);
        LogStore.append(this, "ui", "Service notification visibility saved"
                + " task=" + taskServiceNotificationSwitch.isChecked()
                + " vpn=" + vpnNotificationSwitch.isChecked()
                + " beacon=" + beaconNotificationSwitch.isChecked());
        refreshStatusAndLog();
        return true;
    }

    private boolean saveNotificationBackupConfigOnly() {
        boolean enabled = notificationBackupEnabledSwitch.isChecked();
        Config.get(this).saveNotificationBackupConfig(
                enabled, notificationBackupIncludeSysmgrSwitch.isChecked());
        LogStore.append(this, "ui", "Notification backup settings saved enabled=" + enabled);
        if (enabled) {
            // Bring the Remote Link up (if enabled) and ask the server whether
            // it is storing notifications, so we can tell the user right away.
            RemoteLinkManager.sync(this, "notification-backup-enable");
            RemoteLinkManager.probeBackup(this, "backup-toggle");
        }
        refreshNotificationBackupStatus();
        return true;
    }

    private void refreshNotificationBackupStatus() {
        if (notificationBackupPill == null) {
            return;
        }
        final Config config = Config.get(this);
        final boolean enabled = switchValue(notificationBackupEnabledSwitch, config.notificationBackupEnabled());
        final boolean linkEnabled = config.remoteLinkEnabled();
        final boolean serverChecked = NotificationBackupStateStore.isChecked(this);
        final boolean serverAvailable = NotificationBackupStateStore.isServerAvailable(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int queued = NotificationBackupStore.count(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        applyNotificationBackupStatus(enabled, linkEnabled, serverChecked, serverAvailable, queued);
                    }
                });
            }
        }, "SystemManagerBackupStatus").start();
    }

    private void applyNotificationBackupStatus(boolean enabled, boolean linkEnabled,
                                               boolean serverChecked, boolean serverAvailable, int queued) {
        if (notificationBackupPill == null) {
            return;
        }
        String pillText;
        int pillBg;
        int pillFg;
        String message;
        int dotColor;
        if (!enabled) {
            pillText = "DISABLED";
            pillBg = COLOR_NEUTRAL_CONTAINER;
            pillFg = COLOR_NEUTRAL_ON_CONTAINER;
            message = "";
            dotColor = COLOR_TEXT_FAINT;
        } else if (!linkEnabled) {
            pillText = "NO LINK";
            pillBg = COLOR_DANGER_CONTAINER;
            pillFg = COLOR_DANGER_ON_CONTAINER;
            message = "Turn on the Remote Link to send backups.";
            dotColor = COLOR_BAD;
        } else if (serverChecked && !serverAvailable) {
            pillText = "SERVER OFF";
            pillBg = COLOR_DANGER_CONTAINER;
            pillFg = COLOR_DANGER_ON_CONTAINER;
            message = "The server isn't storing notifications. Held on this device.";
            dotColor = COLOR_BAD;
        } else if (!serverChecked) {
            pillText = "CHECKING";
            pillBg = COLOR_NEUTRAL_CONTAINER;
            pillFg = COLOR_NEUTRAL_ON_CONTAINER;
            message = "Checking the server…";
            dotColor = COLOR_TEXT_FAINT;
        } else if (queued == 0) {
            pillText = "ACTIVE";
            pillBg = COLOR_PRIMARY_CONTAINER;
            pillFg = COLOR_PRIMARY_ON_CONTAINER;
            message = "All notifications backed up.";
            dotColor = COLOR_OK;
        } else {
            pillText = "ACTIVE";
            pillBg = COLOR_PRIMARY_CONTAINER;
            pillFg = COLOR_PRIMARY_ON_CONTAINER;
            message = "Sending to the server…";
            dotColor = COLOR_OK;
        }

        setPillState(notificationBackupPill, pillText, pillBg, pillFg);

        if (notificationBackupStatusCard != null) {
            notificationBackupStatusCard.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (!enabled) {
            return;
        }
        setDotColor(notificationBackupDot, dotColor);
        if (notificationBackupStatus != null) {
            notificationBackupStatus.setText(message);
        }
        if (notificationBackupCountPill != null) {
            if (queued > 0) {
                setPillState(notificationBackupCountPill,
                        queued == 1 ? "1 queued" : (queued + " queued"),
                        COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
                notificationBackupCountPill.setVisibility(View.VISIBLE);
            } else {
                notificationBackupCountPill.setVisibility(View.GONE);
            }
        }
    }

    private void setDotColor(View dot, int color) {
        if (dot == null) {
            return;
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        dot.setBackground(drawable);
    }

    private boolean saveGpsConfigOnly() {
        if (!requireEndpoint(serverBaseUrlField, "GPS server URL", "http", "https")
                || !requireText(trackPathField, "GPS track path")
                || !requireText(ssidPatternField, "GPS SSID pattern")
                || !requireInteger(highBatteryIntervalField, "High-battery interval", 1, 1440)
                || !requireInteger(lowBatteryIntervalField, "Low-battery interval", 1, 1440)
                || !requireInteger(batteryThresholdField, "GPS battery threshold", 1, 100)
                || !requireInteger(locationTimeoutField, "Location timeout", 5, 300)
                || !requireInteger(desiredAccuracyField, "Desired accuracy", 1, 10000)
                || !requireInteger(maxCachedLocationField, "Cached location age", 0, 1440)
                || !requireInteger(httpTimeoutField, "HTTP timeout", 1, 120)
                || !requireDecimal(fallbackLatitudeField, "Fallback latitude", -90d, 90d)
                || !requireDecimal(fallbackLongitudeField, "Fallback longitude", -180d, 180d)) {
            return false;
        }
        Config config = Config.get(this);
        int previousHighInterval = config.highBatteryIntervalMinutes();
        int previousLowInterval = config.lowBatteryIntervalMinutes();
        int previousBatteryThreshold = config.batteryThresholdPercent();
        boolean alarmModeChanged = config.useExactAlarms() != useExactAlarmsSwitch.isChecked()
                || config.allowIdleAlarms() != allowIdleAlarmsSwitch.isChecked();
        config.saveGpsConfig(
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
                gpsUseRemoteLinkSwitch.isChecked(),
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
                caseSensitiveSsidSwitch.isChecked());
        NetworkMonitorService.sync(this);
        boolean gpsScheduleChanged = previousHighInterval != config.highBatteryIntervalMinutes()
                || previousLowInterval != config.lowBatteryIntervalMinutes()
                || previousBatteryThreshold != config.batteryThresholdPercent()
                || alarmModeChanged;
        if (config.isTrackingEnabled() && gpsScheduleChanged) {
            AlarmScheduler.scheduleGpsPost(this, "gps-config-live");
        }
        if (alarmModeChanged) {
            // These two features share the GPS exact/idle alarm policy. Only
            // reschedule them here; applying the current volume rule would be
            // a surprising side effect of editing an unrelated GPS field.
            AlarmScheduler.scheduleNextVolumeRule(this, "alarm-policy-live");
            RebootManager.sync(this, "alarm-policy-live");
        }
        refreshStatusAndLog();
        return true;
    }

    private boolean saveGpsTogglesOnly() {
        Config config = Config.get(this);
        boolean alarmModeChanged = config.useExactAlarms() != useExactAlarmsSwitch.isChecked()
                || config.allowIdleAlarms() != allowIdleAlarmsSwitch.isChecked();
        config.saveGpsToggleConfig(
                gpsUseRemoteLinkSwitch.isChecked(),
                useExactAlarmsSwitch.isChecked(),
                allowIdleAlarmsSwitch.isChecked(),
                postOnStartupSwitch.isChecked(),
                postOnWifiChangeSwitch.isChecked(),
                useGpsProviderSwitch.isChecked(),
                useNetworkProviderSwitch.isChecked(),
                requestGpsOnSsidMismatchSwitch.isChecked(),
                useFallbackOnSsidMatchSwitch.isChecked(),
                useCachedBeforeFreshSwitch.isChecked(),
                includeExtendedFieldsSwitch.isChecked(),
                caseSensitiveSsidSwitch.isChecked());
        NetworkMonitorService.sync(this);
        if (config.isTrackingEnabled() && alarmModeChanged) {
            AlarmScheduler.scheduleGpsPost(this, "gps-toggle-live");
        }
        if (alarmModeChanged) {
            AlarmScheduler.scheduleNextVolumeRule(this, "alarm-policy-live");
            RebootManager.sync(this, "alarm-policy-live");
        }
        refreshStatusAndLog();
        return true;
    }

    private boolean saveHighPriorityConfigOnly() {
        if (!highPriorityEnabledSwitch.isChecked()) {
            highPriorityPackageField.setError(null);
        }
        if ((highPriorityEnabledSwitch.isChecked()
                    && !requireAppSelection(highPriorityPackageField, "High-priority app"))
                || !requireInteger(highPriorityDedupeSecondsField, "Alert duplicate window", 0, 3600)
                || !requireInteger(highPriorityRemoteDedupeSecondsField,
                        "Remote alert duplicate window", 0, 3600)
                || !requireInteger(highPriorityPlaySecondsField, "Alert play duration", 1, 300)
                || !requireInteger(highPriorityAlarmVolumePercentField, "Alert volume", 1, 100)) {
            return false;
        }
        Config.get(this).saveHighPriorityConfig(
                highPriorityEnabledSwitch.isChecked(),
                appPackageFromField(highPriorityPackageField),
                highPriorityFilterFromFields(),
                highPriorityRemoteEnabledSwitch.isChecked(),
                highPriorityRemoteFilterFromFields(),
                text(highPriorityRemoteDedupeSecondsField),
                text(highPriorityToneTitleField),
                text(highPriorityPlaySecondsField),
                text(highPriorityDedupeSecondsField),
                highPriorityRaiseAlarmVolumeSwitch.isChecked(),
                text(highPriorityAlarmVolumePercentField));
        refreshStatusAndLog();
        return true;
    }

    private boolean saveHighPriorityTogglesOnly() {
        Config.get(this).saveHighPriorityToggleConfig(
                highPriorityEnabledSwitch.isChecked(),
                highPriorityRemoteEnabledSwitch.isChecked(),
                highPriorityRaiseAlarmVolumeSwitch.isChecked());
        refreshStatusAndLog();
        return true;
    }

    private AlertTextFilter highPriorityFilterFromFields() {
        return new AlertTextFilter(
                text(highPriorityTitleFilterField),
                text(highPriorityTitleExcludeField),
                text(highPriorityTextFilterField),
                text(highPriorityTextExcludeField));
    }

    private AlertTextFilter highPriorityRemoteFilterFromFields() {
        return new AlertTextFilter(
                text(highPriorityRemoteTitleFilterField),
                text(highPriorityRemoteTitleExcludeField),
                text(highPriorityRemoteTextFilterField),
                text(highPriorityRemoteTextExcludeField));
    }

    private boolean saveBatteryAlertConfigOnly() {
        if (!requireInteger(batteryAlertThresholdField, "Battery alert threshold", 1, 100)
                || !requireInteger(batteryAlertCheckIntervalField,
                        "Battery alert interval", 1, 1440)
                || !requireInteger(batteryAlertVibrateSecondsField,
                        "Battery alert vibration", 0, 60)) {
            return false;
        }
        Config config = Config.get(this);
        boolean scheduleChanged = config.batteryAlertEnabled() != batteryAlertEnabledSwitch.isChecked()
                || config.batteryAlertCheckIntervalMinutes()
                != Integer.parseInt(text(batteryAlertCheckIntervalField).trim())
                || config.batteryAlertUseExactAlarms() != batteryAlertUseExactAlarmsSwitch.isChecked()
                || config.batteryAlertAllowIdleAlarms() != batteryAlertAllowIdleAlarmsSwitch.isChecked();
        config.saveBatteryAlertConfig(
                batteryAlertEnabledSwitch.isChecked(),
                text(batteryAlertThresholdField),
                text(batteryAlertCheckIntervalField),
                text(batteryAlertVibrateSecondsField),
                batteryAlertUseExactAlarmsSwitch.isChecked(),
                batteryAlertAllowIdleAlarmsSwitch.isChecked());
        if (scheduleChanged) {
            BatteryAlertManager.sync(this, "battery-config-live");
        }
        refreshStatusAndLog();
        return true;
    }

    private boolean saveBatteryAlertTogglesOnly() {
        Config config = Config.get(this);
        boolean scheduleChanged = config.batteryAlertEnabled() != batteryAlertEnabledSwitch.isChecked()
                || config.batteryAlertUseExactAlarms() != batteryAlertUseExactAlarmsSwitch.isChecked()
                || config.batteryAlertAllowIdleAlarms() != batteryAlertAllowIdleAlarmsSwitch.isChecked();
        config.saveBatteryAlertToggleConfig(
                batteryAlertEnabledSwitch.isChecked(),
                batteryAlertUseExactAlarmsSwitch.isChecked(),
                batteryAlertAllowIdleAlarmsSwitch.isChecked());
        if (scheduleChanged) {
            BatteryAlertManager.sync(this, "battery-toggle-live");
        }
        refreshStatusAndLog();
        return true;
    }

    private void addVolumeRule() {
        try {
            if (volumeRuleHour < 0 || volumeRuleMinute < 0) {
                throw new IllegalArgumentException("Pick a time");
            }
            Config.VolumeRule rule = Config.get(this).addVolumeRule(
                    volumeRuleHour,
                    volumeRuleMinute,
                    volumeInputValue(volumeRuleMediaInput),
                    volumeInputValue(volumeRuleRingInput),
                    volumeInputValue(volumeRuleNotificationInput),
                    volumeInputValue(volumeRuleAlarmInput),
                    volumeRuleDndMode);
            resetVolumeRuleInputs();
            LogStore.append(this, "volume", "Volume rule added time=" + rule.displayTime());
            VolumeControlManager.sync(this, "rule-added");
            Toast.makeText(this, "Volume rule added", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            LogStore.append(this, "volume", "Volume rule add failed: " + e.getMessage());
        }
        refreshStatusAndLog();
    }

    private void deleteVolumeRule(Config.VolumeRule rule) {
        Config.get(this).removeVolumeRule(rule.id);
        LogStore.append(this, "volume", "Volume rule deleted time=" + rule.displayTime());
        VolumeControlManager.sync(this, "rule-deleted");
        Toast.makeText(this, "Volume rule deleted", Toast.LENGTH_SHORT).show();
        refreshStatusAndLog();
    }

    private void startTracking() {
        Config config = Config.get(this);
        config.setTrackingEnabled(true);
        seedWifiStateAsync("manual-start");
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

    private void openDndAccessSettings() {
        openIntent(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    private void openAccessibilitySettings() {
        openIntent(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void testHighPriorityAlert() {
        LogStore.append(this, "ui", "Manual high-priority alert test requested");
        HighPriorityAlertPlayer.play(this, "manual-test");
        Toast.makeText(this, "Alert test started", Toast.LENGTH_SHORT).show();
        refreshStatusAndLog();
    }

    private void testBatteryAlert() {
        LogStore.append(this, "ui", "Manual battery alert test requested");
        BatteryAlertManager.sendTestNotification(this);
        Toast.makeText(this, "Battery alert test sent", Toast.LENGTH_SHORT).show();
        refreshStatusAndLog();
    }

    private void testRebootNow() {
        LogStore.append(this, "ui", "Manual reboot test requested");
        boolean started = RebootManager.requestReboot(this, "manual-test");
        Toast.makeText(this, started ? "Reboot automation started" : "Enable Accessibility first", Toast.LENGTH_LONG).show();
        refreshStatusAndLog();
    }

    private void testRebootDelayed() {
        LogStore.append(this, "ui", "Delayed reboot lock test requested");
        RebootManager.scheduleDelayedTest(this, "manual-delayed-test");
        Toast.makeText(this, "Lock the phone now", Toast.LENGTH_LONG).show();
        refreshStatusAndLog();
    }

    private boolean saveRemoteLinkConfigOnly() {
        if (!requireEndpoint(remoteLinkEndpointField, "Remote Link endpoint",
                "http", "https", "ws", "wss")
                || !requireInteger(remoteLinkHeartbeatSecondsField,
                        "Remote Link heartbeat", 10, 3600)) {
            return false;
        }
        Config.get(this).saveRemoteLinkConfig(
                remoteLinkEnabledSwitch.isChecked(),
                text(remoteLinkEndpointField),
                text(remoteLinkUsernameField),
                text(remoteLinkPasswordField),
                text(remoteLinkHeartbeatSecondsField),
                remoteLinkAcceptAnySslCertSwitch.isChecked(),
                remoteLinkShowNotificationSwitch.isChecked());
        LogStore.append(this, "ui", "Remote Link configuration saved");
        return true;
    }

    private boolean saveRemoteLinkConfigLive() {
        if (!saveRemoteLinkConfigOnly()) {
            return false;
        }
        RemoteLinkManager.restart(this, "remote-link-config-live");
        refreshStatusAndLog();
        return true;
    }

    private boolean saveRemoteLinkTogglesOnly() {
        Config.get(this).saveRemoteLinkToggleConfig(
                remoteLinkEnabledSwitch.isChecked(),
                remoteLinkAcceptAnySslCertSwitch.isChecked());
        RemoteLinkManager.restart(this, "remote-link-toggle-live");
        refreshStatusAndLog();
        return true;
    }

    private void reconnectRemoteLink() {
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

    private boolean saveRebootConfigOnly() {
        if (!rebootNotificationTriggerEnabledSwitch.isChecked()) {
            rebootTriggerPackageField.setError(null);
        }
        if ((rebootNotificationTriggerEnabledSwitch.isChecked()
                    && !requireAppSelection(rebootTriggerPackageField, "Reboot trigger app"))
                || !requireText(rebootTriggerTitleField, "Reboot trigger title")
                || !requireText(rebootTriggerTextField, "Reboot trigger text")
                || !requireText(rebootWifiPatternField, "Reboot SSID pattern")
                || !requireInteger(rebootScheduleHourField, "Reboot hour", 0, 23)
                || !requireInteger(rebootScheduleMinuteField, "Reboot minute", 0, 59)
                || !requireInteger(rebootDelayedTestSecondsField,
                        "Reboot test delay", 5, 300)
                || !requireInteger(rebootPowerDialogWaitMsField,
                        "Power dialog wait", 250, 10000)
                || !requireInteger(rebootStepWaitMsField,
                        "Reboot step wait", 250, 10000)) {
            return false;
        }
        Config config = Config.get(this);
        boolean scheduleChanged = config.rebootAutomationEnabled()
                != rebootAutomationEnabledSwitch.isChecked()
                || config.rebootScheduleEnabled() != rebootScheduleEnabledSwitch.isChecked()
                || config.rebootScheduleHour()
                != Integer.parseInt(text(rebootScheduleHourField).trim())
                || config.rebootScheduleMinute()
                != Integer.parseInt(text(rebootScheduleMinuteField).trim());
        config.saveRebootConfig(
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
        if (scheduleChanged) {
            RebootManager.sync(this, "reboot-config-live");
        }
        refreshStatusAndLog();
        return true;
    }

    private boolean saveRebootTogglesOnly() {
        Config config = Config.get(this);
        boolean scheduleChanged = config.rebootAutomationEnabled()
                != rebootAutomationEnabledSwitch.isChecked()
                || config.rebootScheduleEnabled() != rebootScheduleEnabledSwitch.isChecked();
        config.saveRebootToggleConfig(
                rebootAutomationEnabledSwitch.isChecked(),
                rebootNotificationTriggerEnabledSwitch.isChecked(),
                rebootRemoteTriggerEnabledSwitch.isChecked(),
                rebootScheduleEnabledSwitch.isChecked(),
                rebootOnlyWhenWifiNotMatchingSwitch.isChecked());
        if (scheduleChanged) {
            RebootManager.sync(this, "reboot-toggle-live");
        }
        refreshStatusAndLog();
        return true;
    }

    private boolean saveLogConfigOnly() {
        if (!requireInteger(logMaxLinesField, "Log retention", 50, 5000)) {
            return false;
        }
        Config.get(this).saveLogConfig(
                logEnabledSwitch.isChecked(),
                text(logMaxLinesField));
        LogStore.append(this, "ui", "Log settings saved");
        refreshStatusAndLog();
        return true;
    }

    private boolean saveLogToggleOnly() {
        Config.get(this).saveLogEnabledConfig(logEnabledSwitch.isChecked());
        refreshStatusAndLog();
        return true;
    }

    private void exportSettingsXml() {
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
            seedWifiStateAsync("settings-import");
            AlarmScheduler.scheduleGpsPost(this, "settings-import");
        } else {
            AlarmScheduler.cancelGpsPost(this);
        }
        NetworkMonitorService.sync(this);
        BatteryAlertManager.sync(this, "settings-import");
        VolumeControlManager.sync(this, "settings-import");
        RebootManager.sync(this, "settings-import");
        RemoteLinkManager.restart(this, "settings-import");
        BeaconManager.refresh(this, "settings-import");
        SystemTaskService.refreshNotification();
        OpenVpnService.refreshNotification(this);
        // VPN prefs round-trip through Settings XML; profile files do not, so
        // the pill stays DISABLED until a profile is re-imported.
        OpenVpnManager.sync(this, "settings-import");
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
        notificationImageCache.evictAll();
        renderedNotificationHistoryKey = null;
        NotificationHistoryStore.clear(this);
        LogStore.append(this, "ui", "Notification history cleared");
        Toast.makeText(this, "Notification history cleared", Toast.LENGTH_SHORT).show();
        refreshNotificationHistory();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN_CONSENT) {
            // Consent returns RESULT_OK with a null data intent, so it must be
            // handled before the data-null guard below.
            boolean resume = pendingVpnConnectAfterConsent;
            pendingVpnConnectAfterConsent = false;
            if (resultCode == RESULT_OK) {
                LogStore.append(this, "vpn", "VPN consent granted");
                if (resume) {
                    OpenVpnManager.connect(this, "consent-granted");
                }
            } else {
                LogStore.append(this, "vpn", "VPN consent declined");
                Toast.makeText(this, "VPN permission was not granted", Toast.LENGTH_LONG).show();
            }
            refreshStatusAndLog();
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == REQUEST_SAVE_NOTIFICATION_IMAGE) {
                pendingImageSaveEntry = null;
            }
            if (requestCode == REQUEST_IMPORT_VPN_CERT) {
                pendingVpnCertSlot = null;
            }
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_SETTINGS) {
            handleSettingsExport(uri);
        } else if (requestCode == REQUEST_IMPORT_SETTINGS) {
            handleSettingsImport(uri);
        } else if (requestCode == REQUEST_SAVE_NOTIFICATION_IMAGE) {
            handleNotificationImageSave(uri);
        } else if (requestCode == REQUEST_IMPORT_VPN_PROFILE) {
            handleVpnProfileImport(uri);
        } else if (requestCode == REQUEST_IMPORT_VPN_CERT) {
            handleVpnCertImport(uri);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        LogStore.append(this, "ui", "Permission result request=" + requestCode);
        if (requestCode == REQUEST_BLUETOOTH_ADVERTISE && PermissionState.hasBluetoothAdvertise(this)) {
            BeaconManager.sync(this, "advertise-permission-granted");
        }
        refreshStatusAndLog();
    }

    private View.OnClickListener action(final String command) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // A quick tap after typing should act on the latest value, not
                // wait for the normal debounce window to expire.
                boolean remoteLinkRestartedBySave = "remote_link_reconnect".equals(command)
                        && remoteLinkSettingsSave != null
                        && remoteLinkSettingsSave.isPending();
                if (!flushSettingsForAction(command)) {
                    return;
                }
                if ("start".equals(command)) {
                    startTracking();
                } else if ("stop".equals(command)) {
                    stopTracking();
                } else if ("send".equals(command)) {
                    sendNow();
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
                } else if ("dnd_access".equals(command)) {
                    openDndAccessSettings();
                } else if ("accessibility_settings".equals(command)) {
                    openAccessibilitySettings();
                } else if ("test_high_priority_alert".equals(command)) {
                    testHighPriorityAlert();
                } else if ("test_battery_alert".equals(command)) {
                    testBatteryAlert();
                } else if ("add_volume_rule".equals(command)) {
                    addVolumeRule();
                } else if ("test_reboot_now".equals(command)) {
                    testRebootNow();
                } else if ("test_reboot_delayed".equals(command)) {
                    testRebootDelayed();
                } else if ("remote_link_reconnect".equals(command)) {
                    if (!remoteLinkRestartedBySave) {
                        reconnectRemoteLink();
                    }
                } else if ("remote_link_ping".equals(command)) {
                    pingRemoteLink();
                } else if ("vpn_import_profile".equals(command)) {
                    importVpnProfile();
                } else if ("vpn_edit_profile".equals(command)) {
                    editVpnProfile();
                } else if ("vpn_clear_profile".equals(command)) {
                    vpnClearProfile();
                } else if ("vpn_connect".equals(command)) {
                    vpnConnect();
                } else if ("vpn_disconnect".equals(command)) {
                    vpnDisconnect();
                } else if ("add_beacon_rule".equals(command)) {
                    addBeaconRule();
                } else if ("beacon_copy_uuid".equals(command)) {
                    copyBeaconUuid();
                } else if ("beacon_new_uuid".equals(command)) {
                    regenerateBeaconUuid();
                } else if ("bluetooth_settings".equals(command)) {
                    openBluetoothSettings();
                } else if ("bluetooth_permission".equals(command)) {
                    requestBluetoothAdvertise();
                } else if ("notifications".equals(command)) {
                    requestNotificationPermission();
                } else if ("refresh_notification_history".equals(command)) {
                    refreshNotificationHistory(true);
                } else if ("notification_history_prev".equals(command)) {
                    notificationHistoryPage--;
                    refreshNotificationHistory(true);
                    scrollNotificationHistoryToTop();
                } else if ("notification_history_next".equals(command)) {
                    notificationHistoryPage++;
                    refreshNotificationHistory(true);
                    scrollNotificationHistoryToTop();
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
        return Ui.dp(this, value);
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
