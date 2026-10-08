package com.jpitsg.sysman;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
import android.location.LocationManager;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.VpnService;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
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
import android.view.WindowInsetsController;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.jpitsg.sysman.Ui.COLOR_BAD;
import static com.jpitsg.sysman.Ui.COLOR_BORDER;
import static com.jpitsg.sysman.Ui.COLOR_DANGER;
import static com.jpitsg.sysman.Ui.COLOR_DANGER_CONTAINER;
import static com.jpitsg.sysman.Ui.COLOR_DANGER_ON_CONTAINER;
import static com.jpitsg.sysman.Ui.COLOR_FIELD_BG;
import static com.jpitsg.sysman.Ui.COLOR_FIELD_BORDER;
import static com.jpitsg.sysman.Ui.COLOR_GROUPED;
import static com.jpitsg.sysman.Ui.COLOR_LABEL;
import static com.jpitsg.sysman.Ui.COLOR_NEUTRAL_CONTAINER;
import static com.jpitsg.sysman.Ui.COLOR_NEUTRAL_ON_CONTAINER;
import static com.jpitsg.sysman.Ui.COLOR_OK;
import static com.jpitsg.sysman.Ui.COLOR_ON_ACCENT;
import static com.jpitsg.sysman.Ui.COLOR_ON_DANGER;
import static com.jpitsg.sysman.Ui.COLOR_PRIMARY;
import static com.jpitsg.sysman.Ui.COLOR_PRIMARY_CONTAINER;
import static com.jpitsg.sysman.Ui.COLOR_PRIMARY_ON_CONTAINER;
import static com.jpitsg.sysman.Ui.COLOR_RIPPLE_LIGHT;
import static com.jpitsg.sysman.Ui.COLOR_SURFACE;
import static com.jpitsg.sysman.Ui.COLOR_TEXT;
import static com.jpitsg.sysman.Ui.COLOR_TEXT_DIM;
import static com.jpitsg.sysman.Ui.COLOR_TEXT_FAINT;

public final class MainActivity extends Activity {
    private static final int REQUEST_COMMON_PERMISSIONS = 10;
    private static final int REQUEST_BACKGROUND_LOCATION = 11;
    private static final int REQUEST_NOTIFICATIONS = 12;
    private static final int REQUEST_BLUETOOTH_ADVERTISE = 13;
    private static final int REQUEST_SAVE_NOTIFICATION_IMAGE = 22;
    private static final int REQUEST_IMPORT_VPN_PROFILE = 23;
    private static final int REQUEST_IMPORT_VPN_CERT = 24;
    private static final int REQUEST_VPN_CONSENT = 25;
    private static final int REQUEST_PROJECTION_CONSENT = 26;
    private static final int REQUEST_INSTALL_UNKNOWN_APPS = 27;

    /** Set on the intent when a notification is asking for screen-capture consent. */
    static final String EXTRA_REQUEST_PROJECTION = "request_projection";

    /** Saved so a recreate (a theme change, say) lands back on the page the user was on. */
    private static final String STATE_PAGE = "page";
    private static final String STATE_HOME_SCROLL = "home_scroll";

    /**
     * How long the app may sit in the background before coming back to it starts
     * from the overview again. A shorter excursion — granting a permission in
     * Android's settings, say — leaves the user on the page they were using.
     */
    private static final long RESET_TO_OVERVIEW_AFTER_MS = 30_000L;

    /** Single spacing unit used for every margin and every padding in the UI. */
    private static final int GAP = Ui.GAP;

    private static final int CARD_CORNER = Ui.CARD_CORNER;
    private static final int SECTION_CORNER = Ui.SECTION_CORNER;
    private static final int FIELD_CORNER = Ui.FIELD_CORNER;
    private static final int BUTTON_CORNER = Ui.BUTTON_CORNER;
    private static final int GROUP_CORNER = Ui.GROUP_CORNER;
    private static final int FIELD_MIN_HEIGHT = Ui.FIELD_MIN_HEIGHT;
    private static final int BUTTON_MIN_HEIGHT = Ui.BUTTON_MIN_HEIGHT;
    private static final int TOGGLE_ROW_MIN_HEIGHT = 52;
    private static final int STATUS_ROW_MIN_HEIGHT = 40;
    private static final int PAGE_TRANSITION_MS = 280;
    private static final int STATUS_DOT_SIZE = 8;
    private static final int LOG_VISIBLE_LINES = 16;
    private static final int NOTIFICATION_HISTORY_PAGE_SIZE = 25;
    private static final long LIVE_SAVE_DELAY_MS = 600L;
    private static final long LIVE_STATUS_TICK_MS = 1000L;

    /** The log console stays dark in both themes, like a terminal. */
    private static final int COLOR_LOG_BG = 0xFF0C1412;
    private static final int COLOR_LOG_FG = 0xFFBFE0CB;

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
    private TextView notificationBackupLastSentValue;
    private TextView notificationBackupSentCountValue;
    private TextView systemBackupPill;
    private Panel upgradePanel;
    private TextView upgradePill;
    private TextView upgradeCurrentVersionValue;
    private TextView upgradeAvailableVersionValue;
    private TextView upgradeDateValue;
    private TextView upgradeSizeValue;
    private Switch upgradeAutoSwitch;
    private Button upgradeRefreshButton;
    private Button upgradeInstallButton;
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
    private volatile boolean notificationBackupRefreshInFlight;
    private volatile boolean pendingNotificationBackupRefresh;
    private LinearLayout volumeRuleList;
    private NotificationHistoryStore.Entry pendingImageSaveEntry;
    private TextView logView;
    private Button startTrackingButton;
    private Button stopTrackingButton;
    private Button backUpButton;
    private Button restoreBackupButton;
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
    private TimePickerDialog volumeRuleTimeDialog;
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
    private TextView remoteLinkUptimeValue;
    private TextView remoteLinkDowntimeValue;
    private TextView remoteLinkLatencyValue;
    private TextView remoteLinkUploadThroughputValue;
    private TextView remoteLinkDownloadThroughputValue;
    private Button remoteLinkLatencyButton;
    private Button remoteLinkThroughputButton;
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
    private boolean openVpnHasProfile;
    private boolean openVpnProfileReady;
    private static volatile String cachedVpnEngineVersion;

    private TextView vncPill;
    private TextView vncStatusText;
    private TextView vncEngineNoteText;
    private EditText vncPasswordField;
    private EditText vncPortField;
    private EditText vncAllowedClientsField;
    private EditText vncMatchingWifiSsidField;
    private EditText vncMaxFpsField;
    private EditText vncIdleTimeoutField;
    private Button vncEngineAccessibilityButton;
    private Button vncEngineProjectionButton;
    private Button vncScaleFullButton;
    private Button vncScaleThreeQuarterButton;
    private Button vncScaleHalfButton;
    private Button vncStartButton;
    private Button vncStopButton;
    private Button vncTestCaptureButton;
    private TextView vncProbeText;
    private LinearLayout vncAuthorizeRow;
    private String vncEngine = Config.VNC_ENGINE_ACCESSIBILITY;
    private int vncScalePercent = Config.VNC_SCALE_FULL;
    private BroadcastReceiver vncStateReceiver;

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
    private TextView beaconAdvertisingDurationValue;

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
    private ToggleSwitch remoteLinkEnabledSwitch;
    private Switch remoteLinkAcceptAnySslCertSwitch;
    private ToggleSwitch beaconEnabledSwitch;
    private ToggleSwitch vncEnabledSwitch;
    private Switch vncRemoteCommandEnabledSwitch;
    private Switch vncViewOnlySwitch;
    private Switch vncEnabledOnMatchingWifiSwitch;
    private Switch vncEnabledWhenVpnConnectedSwitch;
    private Switch vncEnabledOnCellularOnlySwitch;
    private Switch vncWakeOnConnectSwitch;
    private Switch logEnabledSwitch;
    private Switch clearNotificationsOnOpenSwitch;
    private Switch notificationActionButtonsEnabledSwitch;
    private Switch notificationBackupEnabledSwitch;
    private Switch notificationBackupIncludeSysmgrSwitch;
    private FrameLayout rootFrame;
    private ScrollView homeScroll;
    private FrameLayout pageLayer;
    private Dashboard dashboard;
    private View statusScrim;
    private final Map<String, Panel> pagesById = new LinkedHashMap<>();
    private final Map<TextView, Panel> pillOwners = new HashMap<>();
    private final Map<EditText, TextView> fieldErrors = new HashMap<>();
    private final java.util.Set<EditText> invalidFields = new java.util.HashSet<>();
    private Panel currentPage;
    private long stoppedAtMillis;
    private int insetLeft;
    private int insetTop;
    private int insetRight;
    private int insetBottom;
    private int insetIme;
    private Object backCallback;
    private int permissionsNeedingAttention;
    private final Handler liveSaveHandler = new Handler(Looper.getMainLooper());
    private final Handler liveStatusHandler = new Handler(Looper.getMainLooper());
    private final List<LiveSaveGroup> liveSaveGroups = new ArrayList<>();
    private LiveSaveGroup gpsSettingsSave;
    private LiveSaveGroup highPrioritySettingsSave;
    private LiveSaveGroup batteryAlertSettingsSave;
    private LiveSaveGroup rebootSettingsSave;
    private LiveSaveGroup remoteLinkSettingsSave;
    private LiveSaveGroup vpnSettingsSave;
    private LiveSaveGroup vncSettingsSave;
    private LiveSaveGroup beaconSettingsSave;
    private Toast settingsFeedbackToast;
    private BroadcastReceiver remoteLinkStateReceiver;
    private BroadcastReceiver notificationHistoryReceiver;
    private BroadcastReceiver notificationBackupReceiver;
    private BroadcastReceiver logChangedReceiver;
    private BroadcastReceiver networkStateReceiver;
    private BroadcastReceiver systemStateReceiver;
    private boolean loadingConfig;
    private volatile WifiSnapshot cachedWifi;
    private volatile boolean wifiIoInFlight;
    private volatile boolean wifiIoRequested;
    private volatile boolean logIoInFlight;
    private volatile boolean logIoRequested;
    private volatile boolean backupOperationInFlight;
    private volatile boolean backupRestoreInFlight;
    private volatile boolean upgradeDownloadInFlight;
    private File pendingUpgradeApk;
    private boolean autoUpgradeCheckPending;
    private String renderedNotificationHistoryKey;
    private final LruCache<String, Bitmap> notificationImageCache =
            new LruCache<String, Bitmap>(notificationImageCacheSizeKb()) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return Math.max(1, bitmap.getAllocationByteCount() / 1024);
                }
            };
    private final Runnable liveStatusTicker = new Runnable() {
        @Override
        public void run() {
            updateTimeBasedStatus();
            liveStatusHandler.postDelayed(this, LIVE_STATUS_TICK_MS);
        }
    };

    /** One feature page: its overview tile, pinned bar and the column its builder fills. */
    private static final class Panel {
        final String id;
        final String title;
        final int accent;
        final LinearLayout card;
        final LinearLayout bar;
        final View barLine;
        final FrameLayout host;
        final ScrollView scroll;
        final LinearLayout content;
        final StatusPill pill;
        final FeatureTile tile;

        Panel(String id, String title, int accent, LinearLayout card, LinearLayout bar, View barLine,
              FrameLayout host, ScrollView scroll, LinearLayout content, StatusPill pill,
              FeatureTile tile) {
            this.id = id;
            this.title = title;
            this.accent = accent;
            this.card = card;
            this.bar = bar;
            this.barLine = barLine;
            this.host = host;
            this.scroll = scroll;
            this.content = content;
            this.pill = pill;
            this.tile = tile;
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
        // The palette is chosen before anything is inflated: every view reads it as it is built.
        Ui.applyTheme(this);
        setTheme(Ui.dark ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
        super.onCreate(savedInstanceState);
        setTitle("System Manager");
        ServiceNotifications.deleteLegacyHiddenChannels(this);
        buildUi();
        loadConfigIntoFields();
        wireLiveSettings();
        restoreNavigation(savedInstanceState);
        LogStore.append(this, "ui", "MainActivity opened");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PAGE, currentPage == null ? "" : currentPage.id);
        outState.putInt(STATE_HOME_SCROLL, homeScroll == null ? 0 : homeScroll.getScrollY());
    }

    private void restoreNavigation(Bundle state) {
        if (state == null) {
            return;
        }
        final int scrollY = state.getInt(STATE_HOME_SCROLL, 0);
        homeScroll.post(new Runnable() {
            @Override
            public void run() {
                homeScroll.scrollTo(0, scrollY);
            }
        });
        String page = state.getString(STATE_PAGE, "");
        if (!page.isEmpty()) {
            openPage(page, false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // After a long absence the app starts from the overview, as it always has. A short
        // trip out and back (a permission screen) is not an absence, so the page stays.
        if (stoppedAtMillis != 0L
                && SystemClock.elapsedRealtime() - stoppedAtMillis > RESET_TO_OVERVIEW_AFTER_MS) {
            showHome(false);
            homeScroll.scrollTo(0, 0);
        }
        autoUpgradeCheckPending = Config.get(this).autoUpgradeEnabled();
    }

    @Override
    protected void onStop() {
        stoppedAtMillis = SystemClock.elapsedRealtime();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerRemoteLinkStateReceiver();
        registerNotificationHistoryReceiver();
        registerNotificationBackupReceiver();
        registerOpenVpnStateReceiver();
        registerBeaconStateReceiver();
        registerVncStateReceiver();
        registerLogChangedReceiver();
        registerNetworkStateReceiver();
        registerSystemStateReceiver();
        UpgradeStateStore.setRefreshing(this);
        RemoteLinkManager.probeUpgrade(this, "activity-resume");
        NotificationCleaner.clearOnAppOpen(this);
        OpenVpnManager.syncStateOnLaunch(this);
        VncManager.syncStateOnLaunch(this);
        // Accessibility, location visibility and the process hosting the VNC
        // service can all change while an Android settings screen is on top.
        // Re-evaluate on every return so an armed server does not stay blocked
        // or stopped until an unrelated setting is edited.
        VncManager.sync(this, "activity-resume");
        consumeProjectionRequest(getIntent());
        refreshStatusAndLog();
        refreshNotificationHistory();
        liveStatusHandler.removeCallbacks(liveStatusTicker);
        liveStatusHandler.post(liveStatusTicker);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The notification uses CLEAR_TOP, so a running activity is handed the
        // request here rather than through onCreate.
        setIntent(intent);
    }

    /**
     * Raises the consent dialog when the activity was opened by the "tap to
     * authorise" notification. Cleared from the intent so a later resume — a
     * rotation, or coming back from the dialog — does not ask again.
     */
    private void consumeProjectionRequest(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_REQUEST_PROJECTION, false)) {
            return;
        }
        intent.removeExtra(EXTRA_REQUEST_PROJECTION);
        setIntent(intent);
        requestProjectionConsent();
    }

    @Override
    protected void onPause() {
        liveStatusHandler.removeCallbacks(liveStatusTicker);
        flushPendingLiveSaves();
        unregisterNotificationHistoryReceiver();
        unregisterNotificationBackupReceiver();
        unregisterRemoteLinkStateReceiver();
        unregisterOpenVpnStateReceiver();
        unregisterBeaconStateReceiver();
        unregisterVncStateReceiver();
        unregisterLogChangedReceiver();
        unregisterNetworkStateReceiver();
        unregisterSystemStateReceiver();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        liveStatusHandler.removeCallbacks(liveStatusTicker);
        for (LiveSaveGroup group : liveSaveGroups) {
            group.cancel();
        }
        if (backCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        configureSystemBars();
        rootFrame.requestApplyInsets();
        rootFrame.requestLayout();
        // Rotation does not recreate the activity, so an open dialog has to be sized again.
        FittedDialog.refitOpen();
        refitTimePicker();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (currentPage != null) {
            showHome(true);
            return;
        }
        super.onBackPressed();
    }

    /** From Android 13 the back gesture reaches the app only through a registered callback. */
    private void updateBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
        if (currentPage != null && backCallback == null) {
            OnBackInvokedCallback callback;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                callback = new PageBackPreview();
            } else {
                callback = new OnBackInvokedCallback() {
                    @Override
                    public void onBackInvoked() {
                        showHome(true);
                    }
                };
            }
            backCallback = callback;
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        } else if (currentPage == null && backCallback != null) {
            dispatcher.unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
            backCallback = null;
        }
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
        rootFrame = new FrameLayout(this);
        rootFrame.setBackground(Ui.backdrop(this));

        dashboard = new Dashboard(this, new Dashboard.Listener() {
            @Override
            public void onOpenPage(String id) {
                openPage(id, true);
            }

            @Override
            public void onToggleTheme() {
                setThemeModeAndRecreate(Ui.dark ? Ui.MODE_LIGHT : Ui.MODE_DARK);
            }

            @Override
            public void onOpenUpdate() {
                openPage("upgrade", true);
            }
        });
        dashboard.setIdentity(AppVersion.name(this), Dashboard.deviceName(this));

        homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(false);
        homeScroll.setClipToPadding(false);
        homeScroll.setVerticalScrollBarEnabled(false);
        homeScroll.addView(dashboard.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL));
        rootFrame.addView(homeScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // A soft veil behind the status bar, so content scrolling under the clock stays legible.
        statusScrim = new View(this);
        statusScrim.setAlpha(0f);
        statusScrim.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.alpha(Ui.COLOR_BG, 0.96f), Ui.alpha(Ui.COLOR_BG, 0.88f), Ui.alpha(Ui.COLOR_BG, 0f)}));
        rootFrame.addView(statusScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        homeScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldX, int oldY) {
                statusScrim.setAlpha(Math.min(1f, scrollY / (float) dp(40)));
            }
        });

        pageLayer = new FrameLayout(this);
        pageLayer.setVisibility(View.GONE);
        rootFrame.addView(pageLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rootFrame.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    insetLeft = bars.left;
                    insetTop = bars.top;
                    insetRight = bars.right;
                    insetBottom = bars.bottom;
                    insetIme = insets.getInsets(WindowInsets.Type.ime()).bottom;
                } else {
                    insetLeft = insets.getSystemWindowInsetLeft();
                    insetTop = insets.getSystemWindowInsetTop();
                    insetRight = insets.getSystemWindowInsetRight();
                    // Before Android 11 the keyboard is folded into the system window inset;
                    // the stable inset is what the bars alone take.
                    insetBottom = insets.getStableInsetBottom();
                    insetIme = Math.max(0, insets.getSystemWindowInsetBottom() - insets.getStableInsetBottom());
                }
                applyInsets();
                return insets;
            }
        });

        // Order is the order of the tiles on the overview, grouped by purpose.
        buildRemoteLinkPanel();
        buildOpenVpnPanel();
        buildVncPanel();
        buildBeaconPanel();
        buildGpsLoggerPanel();
        buildNotificationHistoryPanel();
        buildNotificationBackupPanel();
        buildHighPriorityPanel();
        buildBatteryAlertPanel();
        buildVolumeControlPanel();
        buildRebootPanel();
        buildPermissionsPanel();
        buildSettingsTransferPanel();
        buildUpgradePanel();
        buildLogPanel();
        buildAppearancePanel();

        setContentView(rootFrame);
        configureSystemBars();
        BatteryAlertManager.sync(this, "activity-open");
        RemoteLinkManager.sync(this, "activity-open");
        VolumeControlManager.sync(this, "activity-open");
        OpenVpnManager.sync(this, "activity-open");
        BeaconManager.sync(this, "activity-open");
    }

    // ============================================================
    //  Pages & navigation
    // ============================================================

    /**
     * Creates a feature page and its tile on the overview. The page is built now
     * but only attached to the window while it is open; its fields are ordinary
     * views, so settings load into them whether or not it is showing.
     */
    private Panel addPage(String id, String group, String title, String description,
                          int iconRes, int accent, boolean inDial) {
        FeatureTile tile = dashboard.addTile(id, group, title, iconRes, accent, inDial);

        LinearLayout card = newColumn();
        card.setBackground(Ui.backdrop(this));
        // Keeps touches on the page from reaching the overview underneath it.
        card.setClickable(true);

        LinearLayout bar = newRow();
        card.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout back = new FrameLayout(this);
        back.setBackground(Ui.ripple(COLOR_RIPPLE_LIGHT,
                roundedFill(COLOR_SURFACE, 22, 1, COLOR_BORDER), dp(22)));
        Ui.elevate(back, 1f);
        back.setClickable(true);
        back.setFocusable(true);
        back.setContentDescription("Back");
        back.addView(Ui.icon(this, R.drawable.ic_arrow_back, 22, COLOR_TEXT),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showHome(true);
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView titleView = Ui.text(this, title, 20, COLOR_TEXT, true);
        titleView.setTypeface(Ui.FONT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(14);
        bar.addView(titleView, titleLp);

        StatusPill pill = new StatusPill(this, true);
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp.leftMargin = dp(10);
        bar.addView(pill, pillLp);

        View barLine = new View(this);
        barLine.setBackgroundColor(COLOR_BORDER);
        barLine.setAlpha(0f);
        card.addView(barLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        FrameLayout host = new FrameLayout(this);
        card.addView(host, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        host.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ContentColumn content = new ContentColumn(this);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL));

        if (description != null && !description.isEmpty()) {
            TextView intro = Ui.text(this, description, 13.5f, COLOR_TEXT_DIM, false);
            intro.setLineSpacing(0, 1.25f);
            intro.setPadding(dp(4), dp(4), dp(4), 0);
            content.addView(intro, matchWrapParams());
        }

        final View line = barLine;
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldX, int oldY) {
                line.setAlpha(Math.min(1f, scrollY / (float) dp(16)));
            }
        });

        Panel panel = new Panel(id, title, accent, card, bar, barLine, host, scroll, content, pill, tile);
        pagesById.put(id, panel);
        pillOwners.put(pill, panel);
        applyInsets(panel);
        return panel;
    }

    private void applyInsets() {
        if (dashboard != null) {
            dashboard.setInsets(insetLeft, insetTop, insetRight, insetBottom);
        }
        if (statusScrim != null) {
            statusScrim.getLayoutParams().height = insetTop + dp(20);
            statusScrim.requestLayout();
        }
        for (Panel panel : pagesById.values()) {
            applyInsets(panel);
        }
    }

    private void applyInsets(Panel panel) {
        panel.bar.setPadding(insetLeft + dp(10), insetTop + dp(10), insetRight + dp(Ui.PAGE_PADDING), dp(8));
        // With the keyboard up the scroll view itself shrinks, so Android scrolls the focused field into view.
        panel.host.setPadding(0, 0, 0, insetIme);
        panel.content.setPadding(insetLeft + dp(Ui.PAGE_PADDING), dp(6),
                insetRight + dp(Ui.PAGE_PADDING), dp(32) + (insetIme > 0 ? 0 : insetBottom));
    }

    private void openPage(String id, boolean animate) {
        final Panel panel = pagesById.get(id);
        if (panel == null || panel == currentPage) {
            return;
        }
        settleNavigation();
        hideKeyboard();
        if (currentPage != null) {
            pageLayer.removeView(currentPage.card);
        }
        currentPage = panel;
        ViewGroup parent = (ViewGroup) panel.card.getParent();
        if (parent != null) {
            parent.removeView(panel.card);
        }
        pageLayer.addView(panel.card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.scroll.scrollTo(0, 0);
        updateBackCallback();
        onPageOpened(panel);
        if (!animate) {
            settleNavigation();
            return;
        }
        pageLayer.setAlpha(0f);
        pageLayer.setTranslationX(dp(40));
        pageLayer.setVisibility(View.VISIBLE);
        pageLayer.animate().alpha(1f).translationX(0f)
                .setDuration(PAGE_TRANSITION_MS)
                .setInterpolator(new DecelerateInterpolator(2.2f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (currentPage == panel) {
                            settleNavigation();
                        }
                    }
                }).start();
        homeScroll.animate().alpha(0.3f).translationX(-dp(28))
                .setDuration(PAGE_TRANSITION_MS)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
    }

    private void showHome(boolean animate) {
        showHome(animate, false);
    }

    /**
     * Closes the open page. After a back-gesture preview the layers are already part-way,
     * so the closing animation carries on from where the finger left them.
     */
    private void showHome(boolean animate, boolean fromPreview) {
        if (currentPage == null) {
            return;
        }
        if (!fromPreview) {
            settleNavigation();
        }
        hideKeyboard();
        flushPendingLiveSaves();
        currentPage = null;
        updateBackCallback();
        homeScroll.setVisibility(View.VISIBLE);
        if (!animate) {
            settleNavigation();
            return;
        }
        if (!fromPreview) {
            homeScroll.setAlpha(0.3f);
            homeScroll.setTranslationX(-dp(28));
        }
        homeScroll.animate().alpha(1f).translationX(0f)
                .setDuration(PAGE_TRANSITION_MS)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        pageLayer.animate().alpha(0f).translationX(dp(40)).scaleX(1f).scaleY(1f)
                .setDuration(PAGE_TRANSITION_MS - 60)
                .setInterpolator(new AccelerateInterpolator(1.4f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (currentPage == null) {
                            settleNavigation();
                        }
                    }
                }).start();
    }

    /**
     * Back-gesture preview (Android 14+): the page follows the finger and shrinks slightly,
     * the overview rises into place beneath it, and releasing either completes the close or,
     * if the gesture is abandoned, puts everything back.
     */
    private final class PageBackPreview implements OnBackAnimationCallback {
        @Override
        public void onBackStarted(BackEvent event) {
            if (currentPage == null) {
                return;
            }
            settleNavigation();
            homeScroll.setVisibility(View.VISIBLE);
            homeScroll.setAlpha(0.3f);
            homeScroll.setTranslationX(-dp(28));
            pageLayer.setPivotX(pageLayer.getWidth() / 2f);
            pageLayer.setPivotY(pageLayer.getHeight() / 2f);
        }

        @Override
        public void onBackProgressed(BackEvent event) {
            if (currentPage == null) {
                return;
            }
            float progress = event.getProgress();
            float direction = event.getSwipeEdge() == BackEvent.EDGE_RIGHT ? -1f : 1f;
            pageLayer.setTranslationX(direction * dp(48) * progress);
            pageLayer.setScaleX(1f - 0.06f * progress);
            pageLayer.setScaleY(1f - 0.06f * progress);
            pageLayer.setAlpha(1f - 0.2f * progress);
            homeScroll.setAlpha(0.3f + 0.7f * progress);
            homeScroll.setTranslationX(-dp(28) * (1f - progress));
        }

        @Override
        public void onBackInvoked() {
            showHome(true, true);
        }

        @Override
        public void onBackCancelled() {
            if (currentPage == null) {
                return;
            }
            homeScroll.animate().alpha(0.3f).translationX(-dp(28)).setDuration(160).start();
            pageLayer.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f)
                    .setDuration(180)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (currentPage != null) {
                                settleNavigation();
                            }
                        }
                    }).start();
        }
    }

    /** Ends any running page transition and puts every layer in its final state. */
    private void settleNavigation() {
        homeScroll.animate().cancel();
        pageLayer.animate().cancel();
        homeScroll.setAlpha(1f);
        homeScroll.setTranslationX(0f);
        pageLayer.setAlpha(1f);
        pageLayer.setTranslationX(0f);
        pageLayer.setScaleX(1f);
        pageLayer.setScaleY(1f);
        if (currentPage != null) {
            homeScroll.setVisibility(View.INVISIBLE);
            pageLayer.setVisibility(View.VISIBLE);
        } else {
            homeScroll.setVisibility(View.VISIBLE);
            pageLayer.removeAllViews();
            pageLayer.setVisibility(View.GONE);
        }
    }

    private void onPageOpened(Panel panel) {
        if (panel == openVpnPanel) {
            resolveVpnEngineVersion();
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
        focus.clearFocus();
    }

    private void setThemeModeAndRecreate(int mode) {
        if (Ui.themeMode(this) == mode) {
            return;
        }
        flushPendingLiveSaves();
        Ui.setThemeMode(this, mode);
        recreate();
    }

    // ============================================================
    //  System bars & insets
    // ============================================================

    /** The window draws under both system bars; the content pads itself with the insets. */
    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        boolean lightBars = !Ui.dark;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBars ? mask : 0, mask);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (lightBars) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void buildGpsLoggerControls(LinearLayout panel) {
        LinearLayout controls = section(panel, "Tracking");
        LinearLayout row1 = newRow();
        controls.addView(row1, stack(controls));
        startTrackingButton = primaryButton("Start Tracking", R.drawable.ic_play, action("start"));
        stopTrackingButton = neutralButton("Stop Tracking", R.drawable.ic_stop, action("stop"));
        addRowButton(row1, startTrackingButton);
        addRowButton(row1, stopTrackingButton);

        LinearLayout row2 = newRow();
        controls.addView(row2, stack(controls));
        addRowButton(row2, tonalButton("Send Now", R.drawable.ic_send, action("send")));
    }

    // ============================================================
    //  Feature panels
    // ============================================================

    private void buildNotificationHistoryPanel() {
        Panel frame = addPage("history", "Tracking & history", "Notification History",
                "Every notification this phone has received, kept on the device with its images.",
                R.drawable.ic_inbox, Ui.ACCENT_BLUE, false);
        notificationHistoryPill = frame.pill;

        LinearLayout buttons = newRow();
        LinearLayout.LayoutParams buttonsLp = matchWrapParams();
        buttonsLp.topMargin = dp(16);
        frame.content.addView(buttons, buttonsLp);
        addRowButton(buttons, tonalButton("Refresh", R.drawable.ic_refresh, action("refresh_notification_history")));
        addRowButton(buttons, neutralButton("Clear", R.drawable.ic_trash, action("clear_notification_history")));

        notificationHistoryList = newColumn();
        notificationHistoryList.setClipChildren(false);
        LinearLayout.LayoutParams listLp = matchWrapParams();
        listLp.topMargin = dp(16);
        frame.content.addView(notificationHistoryList, listLp);

        notificationHistoryPageRow = newRow();
        LinearLayout.LayoutParams pageRowLp = matchWrapParams();
        pageRowLp.topMargin = dp(16);
        frame.content.addView(notificationHistoryPageRow, pageRowLp);
        notificationHistoryPrevButton = neutralButton("Prev", R.drawable.ic_arrow_back, action("notification_history_prev"));
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
        notificationHistoryNextButton = neutralButton("Next", R.drawable.ic_chevron_right, action("notification_history_next"));
        notificationHistoryPageRow.addView(notificationHistoryNextButton,
                inRow(notificationHistoryPageRow, 0, dp(BUTTON_MIN_HEIGHT), 1f));

        LinearLayout options = section(frame.content, "Options");
        LinearLayout optionsGroup = addToggleGroup(options);
        clearNotificationsOnOpenSwitch = addGroupedToggle(optionsGroup, "Clear Android notifications on app open");
        notificationActionButtonsEnabledSwitch = addGroupedToggle(
                optionsGroup, "Show Delete and Clear buttons on notifications");
    }

    private void buildNotificationBackupPanel() {
        Panel frame = addPage("notification_backup", "Tracking & history", "Notification Backup",
                "Mirror every notification to your server so none are lost.",
                R.drawable.ic_upload_cloud, Ui.ACCENT_CYAN, true);
        notificationBackupPill = frame.pill;

        notificationBackupEnabledSwitch = addMasterToggle(frame, R.drawable.ic_upload_cloud,
                "Send to server", "Queued on the phone and delivered over the Remote Link");

        LinearLayout statusCard = newRow();
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setBackground(roundedFill(COLOR_SURFACE, SECTION_CORNER, 1, COLOR_BORDER));
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusCard.setMinimumHeight(dp(STATUS_ROW_MIN_HEIGHT));
        statusCard.setVisibility(View.GONE);
        notificationBackupStatusCard = statusCard;

        notificationBackupDot = new View(this);
        setDotColor(notificationBackupDot, COLOR_TEXT_FAINT);
        statusCard.addView(notificationBackupDot, inRow(statusCard, dp(STATUS_DOT_SIZE), dp(STATUS_DOT_SIZE), 0f));

        notificationBackupStatus = new TextView(this);
        notificationBackupStatus.setTextSize(13.5f);
        notificationBackupStatus.setTextColor(COLOR_TEXT);
        notificationBackupStatus.setIncludeFontPadding(false);
        notificationBackupStatus.setLineSpacing(0, 1.15f);
        notificationBackupStatus.setPadding(dp(GAP), 0, dp(GAP), 0);
        statusCard.addView(notificationBackupStatus, inRow(statusCard, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        notificationBackupCountPill = new StatusPill(this, false);
        notificationBackupCountPill.setVisibility(View.GONE);
        statusCard.addView(notificationBackupCountPill, inRow(statusCard,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        LinearLayout.LayoutParams statusLp = matchWrapParams();
        statusLp.topMargin = dp(14);
        frame.content.addView(statusCard, statusLp);

        LinearLayout options = section(frame.content, "Options");
        LinearLayout backupGroup = addToggleGroup(options);
        notificationBackupIncludeSysmgrSwitch = addGroupedToggle(backupGroup, "Include own notifications",
                "Also back up System Manager's own notifications");

        LinearLayout statsCard = section(frame.content, "Statistics");
        notificationBackupLastSentValue = addInfoRow(
                statsCard, "Last notification sent", "Never", COLOR_TEXT_DIM);
        notificationBackupSentCountValue = addInfoRow(
                statsCard, "Notifications sent", "0", COLOR_TEXT_DIM);
    }

    private void buildGpsLoggerPanel() {
        Panel panel = addPage("gps", "Tracking & history", "GPS Logger",
                "Reports this phone's location to your server on a schedule.",
                R.drawable.ic_gps, Ui.ACCENT_EMERALD, true);
        statusPill = panel.pill;
        panel.tile.enableToggle(new FeatureTile.ToggleListener() {
            @Override
            public void onToggled(boolean on) {
                toggleTracking(on);
            }
        });
        buildGpsLoggerControls(panel.content);
        buildEndpointSubsection(panel.content);
        buildWifiSubsection(panel.content);
        buildLocationSubsection(panel.content);
        buildSchedulingSubsection(panel.content);
    }

    private void buildEndpointSubsection(LinearLayout panel) {
        LinearLayout section = section(panel, "Server");
        serverBaseUrlField = addField(section, "Server base URL", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        trackPathField = addField(section, "Track path", InputType.TYPE_CLASS_TEXT);
        httpTimeoutField = addField(section, "HTTP timeout (seconds)", InputType.TYPE_CLASS_NUMBER);
        LinearLayout group = addToggleGroup(section);
        gpsUseRemoteLinkSwitch = addGroupedToggle(group, "Send GPS over Remote Link");
        includeExtendedFieldsSwitch = addGroupedToggle(group, "Include extended fields");
    }

    private void buildWifiSubsection(LinearLayout panel) {
        LinearLayout section = section(panel, "Wi-Fi matching");
        ssidPatternField = addField(section, "SSID pattern", InputType.TYPE_CLASS_TEXT);
        LinearLayout group = addToggleGroup(section);
        caseSensitiveSsidSwitch = addGroupedToggle(group, "Case-sensitive SSID match");
        requestGpsOnSsidMismatchSwitch = addGroupedToggle(group, "Request GPS when SSID differs");
        useFallbackOnSsidMatchSwitch = addGroupedToggle(group, "Use fallback when SSID matches");
    }

    private void buildLocationSubsection(LinearLayout panel) {
        LinearLayout section = section(panel, "Location");
        highBatteryIntervalField = addField(section, "Interval when battery ≥ threshold (minutes)", InputType.TYPE_CLASS_NUMBER);
        lowBatteryIntervalField = addField(section, "Interval when battery < threshold (minutes)", InputType.TYPE_CLASS_NUMBER);
        batteryThresholdField = addField(section, "Battery threshold (%)", InputType.TYPE_CLASS_NUMBER);
        locationTimeoutField = addField(section, "Location timeout (seconds)", InputType.TYPE_CLASS_NUMBER);
        desiredAccuracyField = addField(section, "Desired accuracy (meters)", InputType.TYPE_CLASS_NUMBER);
        maxCachedLocationField = addField(section, "Max cached location (minutes)", InputType.TYPE_CLASS_NUMBER);
        fallbackLatitudeField = addField(section, "Fallback latitude", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        fallbackLongitudeField = addField(section, "Fallback longitude", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        LinearLayout group = addToggleGroup(section);
        useGpsProviderSwitch = addGroupedToggle(group, "Use GPS provider");
        useNetworkProviderSwitch = addGroupedToggle(group, "Use network provider");
        useCachedBeforeFreshSwitch = addGroupedToggle(group, "Prefer accurate cached location");
    }

    private void buildSchedulingSubsection(LinearLayout panel) {
        LinearLayout section = section(panel, "Schedule");
        LinearLayout group = addToggleGroup(section);
        useExactAlarmsSwitch = addGroupedToggle(group, "Use exact GPS, volume, and reboot alarms");
        allowIdleAlarmsSwitch = addGroupedToggle(group, "Allow GPS, volume, and reboot alarms while idle");
        postOnStartupSwitch = addGroupedToggle(group, "Send once after boot");
        postOnWifiChangeSwitch = addGroupedToggle(group, "Send when Wi-Fi changes");
        // The Wi-Fi monitor's own notification toggle lives under Permissions.
    }

    private void buildHighPriorityPanel() {
        Panel frame = addPage("alerts", "Alerts & automation", "High Priority Alerts",
                "Sound a loud alarm for notifications that match your rules, even in silent mode.",
                R.drawable.ic_bell_ring, Ui.ACCENT_ROSE, true);
        highPriorityPill = frame.pill;

        LinearLayout app = section(frame.content, "Package based alerts");
        LinearLayout packageGroup = addToggleGroup(app);
        highPriorityEnabledSwitch = addGroupedToggle(packageGroup, "Enable package-based alerts");
        highPriorityPackageField = addField(app, "Notification app", InputType.TYPE_CLASS_TEXT);
        configureAppPickerField(highPriorityPackageField);
        highPriorityTitleFilterField = addField(app, "Title contains", InputType.TYPE_CLASS_TEXT);
        highPriorityTitleExcludeField = addField(app, "Title does not contain", InputType.TYPE_CLASS_TEXT);
        highPriorityTextFilterField = addField(app, "Message contains", InputType.TYPE_CLASS_TEXT);
        highPriorityTextExcludeField = addField(app, "Message does not contain", InputType.TYPE_CLASS_TEXT);
        highPriorityDedupeSecondsField = addField(app, "Duplicate window (seconds)", InputType.TYPE_CLASS_NUMBER);

        LinearLayout remote = section(frame.content, "Remote Link alerts");
        LinearLayout socketGroup = addToggleGroup(remote);
        highPriorityRemoteEnabledSwitch = addGroupedToggle(socketGroup, "Enable Remote Link alerts");
        highPriorityRemoteTitleFilterField = addField(remote, "Title contains", InputType.TYPE_CLASS_TEXT);
        highPriorityRemoteTitleExcludeField = addField(remote, "Title does not contain", InputType.TYPE_CLASS_TEXT);
        highPriorityRemoteTextFilterField = addField(remote, "Message contains", InputType.TYPE_CLASS_TEXT);
        highPriorityRemoteTextExcludeField = addField(remote, "Message does not contain", InputType.TYPE_CLASS_TEXT);
        highPriorityRemoteDedupeSecondsField = addField(remote, "Duplicate window (seconds)", InputType.TYPE_CLASS_NUMBER);

        LinearLayout tone = section(frame.content, "Tone & volume");
        highPriorityToneTitleField = addField(tone, "Alarm tone", InputType.TYPE_CLASS_TEXT);
        configureTonePickerField(highPriorityToneTitleField);
        highPriorityPlaySecondsField = addField(tone, "Play duration (seconds)", InputType.TYPE_CLASS_NUMBER);
        highPriorityAlarmVolumePercentField = addField(tone, "Alarm volume (%)", InputType.TYPE_CLASS_NUMBER);
        LinearLayout volumeGroup = addToggleGroup(tone);
        highPriorityRaiseAlarmVolumeSwitch = addGroupedToggle(volumeGroup, "Temporarily raise alarm volume");

        addStackedButton(tone, tonalButton("Test Alert", R.drawable.ic_bell_ring, action("test_high_priority_alert")));
        addStackedButton(tone, neutralButton("Notification Access", R.drawable.ic_key, action("notification_access")));
    }

    private void buildBatteryAlertPanel() {
        Panel frame = addPage("battery", "Alerts & automation", "Battery Alerts",
                "Get warned when the battery runs low.",
                R.drawable.ic_battery, Ui.ACCENT_AMBER, true);
        batteryAlertPill = frame.pill;

        batteryAlertEnabledSwitch = addMasterToggle(frame, R.drawable.ic_battery,
                "Enable battery alerts", "Notify when the charge drops below your threshold");

        LinearLayout alert = section(frame.content, "Alert settings");
        batteryAlertThresholdField = addField(alert, "Alert threshold (%)", InputType.TYPE_CLASS_NUMBER);
        batteryAlertCheckIntervalField = addField(alert, "Check interval (minutes)", InputType.TYPE_CLASS_NUMBER);
        batteryAlertVibrateSecondsField = addField(alert, "Vibration duration (seconds)", InputType.TYPE_CLASS_NUMBER);

        LinearLayout schedule = section(frame.content, "Schedule");
        LinearLayout scheduleGroup = addToggleGroup(schedule);
        batteryAlertUseExactAlarmsSwitch = addGroupedToggle(scheduleGroup, "Use exact battery alert alarms");
        batteryAlertAllowIdleAlarmsSwitch = addGroupedToggle(scheduleGroup, "Allow battery alert alarms while idle");

        addStackedButton(schedule, tonalButton("Test Battery Alert", R.drawable.ic_battery, action("test_battery_alert")));
        addStackedButton(schedule, neutralButton("Notification Permission", R.drawable.ic_bell, action("notifications")));
    }

    private void buildVolumeControlPanel() {
        Panel frame = addPage("volume", "Alerts & automation", "Volume Control",
                "Change volumes and Do Not Disturb at chosen times of day.",
                R.drawable.ic_volume, Ui.ACCENT_VIOLET, true);
        volumeControlPill = frame.pill;

        LinearLayout add = section(frame.content, "Add rule");
        volumeRuleTimeButton = addPickerButton(add, "Time", "Select Time", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showVolumeRuleTimePicker();
            }
        });
        volumeRuleMediaInput = addVolumeInput(add, "Media");
        volumeRuleRingInput = addVolumeInput(add, "Ring");
        volumeRuleNotificationInput = addVolumeInput(add, "Notification");
        volumeRuleAlarmInput = addVolumeInput(add, "Alarm");
        addDndModeInput(add);

        LinearLayout addRow = newRow();
        add.addView(addRow, stack(add));
        addRowButton(addRow, primaryButton("Add Rule", R.drawable.ic_plus, action("add_volume_rule")));

        volumeRuleList = listCard(frame.content, "Rules");
    }

    private void buildOpenVpnPanel() {
        Panel frame = addPage("vpn", "Connectivity", "OpenVPN",
                "A built-in VPN client that works without root.",
                R.drawable.ic_shield_lock, Ui.ACCENT_BLUE, true);
        openVpnPanel = frame;
        openVpnPill = frame.pill;
        frame.tile.enableToggle(new FeatureTile.ToggleListener() {
            @Override
            public void onToggled(boolean on) {
                toggleVpn(on);
            }
        });

        LinearLayout profile = section(frame.content, "Profile");
        openVpnProfileSummary = newColumn();
        profile.addView(openVpnProfileSummary, stack(profile));

        LinearLayout importRow = newRow();
        profile.addView(importRow, stack(profile));
        addRowButton(importRow, tonalButton("Import Profile", R.drawable.ic_download, action("vpn_import_profile")));
        addRowButton(importRow, neutralButton("Clear Profile", R.drawable.ic_trash, action("vpn_clear_profile")));

        openVpnEditRow = newRow();
        profile.addView(openVpnEditRow, stack(profile));
        addRowButton(openVpnEditRow, tonalButton("Edit Profile", R.drawable.ic_edit, action("vpn_edit_profile")));

        openVpnSlotList = listCard(frame.content, "Files");

        vpnAuthSection = newColumn();
        frame.content.addView(vpnAuthSection, matchWrapParams());
        LinearLayout auth = section(vpnAuthSection, "Authentication");
        vpnUserPassBlock = newColumn();
        auth.addView(vpnUserPassBlock, stack(auth));
        vpnUsernameField = addField(vpnUserPassBlock, "Username", InputType.TYPE_CLASS_TEXT);
        vpnPasswordField = addField(vpnUserPassBlock, "Password",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        vpnPassphraseBlock = newColumn();
        auth.addView(vpnPassphraseBlock, stack(auth));
        vpnKeyPassphraseField = addField(vpnPassphraseBlock, "Private key passphrase",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        vpnTapSection = newColumn();
        frame.content.addView(vpnTapSection, matchWrapParams());
        LinearLayout tap = section(vpnTapSection, "TAP addressing");
        vpnTapStaticIpField = addField(tap, "Static IP (blank = server-assigned)", InputType.TYPE_CLASS_TEXT);
        vpnTapNetmaskField = addField(tap, "Netmask", InputType.TYPE_CLASS_TEXT);
        vpnTapGatewayField = addField(tap, "Gateway (optional)", InputType.TYPE_CLASS_TEXT);

        LinearLayout remote = section(frame.content, "Remote control");
        LinearLayout remoteGroup = addToggleGroup(remote);
        vpnRemoteCommandEnabledSwitch = addGroupedToggle(remoteGroup, "Allow VPN control from Remote Link");

        LinearLayout status = section(frame.content, "Status");
        openVpnStatusText = historyText("Off", 14, COLOR_TEXT, false);
        openVpnStatusText.setLineSpacing(0, 1.25f);
        status.addView(openVpnStatusText, stack(status));

        openVpnEngineVersionText = addNote(status, "OpenVPN engine: resolving…");

        LinearLayout controlRow = newRow();
        status.addView(controlRow, stack(status));
        vpnConnectButton = primaryButton("Connect", R.drawable.ic_play, action("vpn_connect"));
        vpnDisconnectButton = neutralButton("Disconnect", R.drawable.ic_stop, action("vpn_disconnect"));
        addRowButton(controlRow, vpnConnectButton);
        addRowButton(controlRow, vpnDisconnectButton);
    }

    private void buildVncPanel() {
        Panel frame = addPage("vnc", "Connectivity", "VNC Server",
                "View and control this screen from any VNC client.",
                R.drawable.ic_cast, Ui.ACCENT_VIOLET, true);
        vncPill = frame.pill;

        vncEnabledSwitch = addMasterToggle(frame, R.drawable.ic_cast,
                "Enable VNC server", "Let a VNC client see and drive this screen");
        mirrorToTile(frame, vncEnabledSwitch);

        LinearLayout engine = section(frame.content, "Engine");
        addVncEngineInput(engine);
        vncEngineNoteText = addNote(engine, "");

        LinearLayout access = section(frame.content, "Access");
        vncPasswordField = addField(access, "Password (VNC uses the first 8 characters)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        vncPortField = addField(access, "Port", InputType.TYPE_CLASS_NUMBER);
        vncAllowedClientsField = addField(access,
                "Allowed clients (blank = any; IP or CIDR, comma separated)", InputType.TYPE_CLASS_TEXT);
        LinearLayout accessGroup = addToggleGroup(access);
        vncViewOnlySwitch = addGroupedToggle(accessGroup, "View only (no input)");
        // The key mapping is not discoverable, and the typing limit is a real
        // constraint rather than a bug: there is no key injection without a
        // platform signature.
        addNote(access,
                "Touch, drag, scroll and right-click (long press) work anywhere. Typing only "
                        + "reaches a focused text field. On the device PIN screen, digits type "
                        + "the PIN, Backspace/Delete removes a digit, and Enter submits it.\n"
                        + "Esc Back · Home Home · End Recents · "
                        + "arrows D-pad · F1 Notifications · F2 Quick Settings · F3 Menu · "
                        + "F4 Power · F5 Lock · F6 Screenshot");

        LinearLayout availability = section(frame.content, "Availability");
        LinearLayout availabilityGroup = addToggleGroup(availability);
        vncEnabledOnMatchingWifiSwitch = addGroupedToggle(
                availabilityGroup, "Enable on matching Wi-Fi SSID");
        vncEnabledWhenVpnConnectedSwitch = addGroupedToggle(
                availabilityGroup, "Enable when VPN is connected");
        vncEnabledOnCellularOnlySwitch = addGroupedToggle(
                availabilityGroup, "Enable on cellular only");
        vncMatchingWifiSsidField = addField(
                availability, "Wi-Fi SSID pattern (* wildcard)", InputType.TYPE_CLASS_TEXT);
        addNote(availability,
                "When one or more conditions are selected, the enabled server runs while any "
                        + "selected condition matches. With none selected, it runs on any connection.");

        LinearLayout capture = section(frame.content, "Capture");
        addVncScaleInput(capture);
        vncMaxFpsField = addField(capture, "Max frames per second", InputType.TYPE_CLASS_NUMBER);
        vncIdleTimeoutField = addField(capture, "Idle timeout (minutes, 0 = never)",
                InputType.TYPE_CLASS_NUMBER);
        LinearLayout captureGroup = addToggleGroup(capture);
        vncWakeOnConnectSwitch = addGroupedToggle(captureGroup,
                "Wake a sleeping screen when a client connects");
        addNote(capture, "The display stays awake while a VNC client is connected.");

        LinearLayout probeRow = newRow();
        capture.addView(probeRow, stack(capture));
        vncTestCaptureButton = tonalButton("Test Capture", R.drawable.ic_activity, action("vnc_test_capture"));
        addRowButton(probeRow, vncTestCaptureButton);
        vncProbeText = addNote(capture, "");

        LinearLayout remote = section(frame.content, "Remote control");
        LinearLayout remoteGroup = addToggleGroup(remote);
        vncRemoteCommandEnabledSwitch = addGroupedToggle(
                remoteGroup, "Allow VNC control from Remote Link");

        LinearLayout status = section(frame.content, "Status");
        vncStatusText = historyText("Off", 14, COLOR_TEXT, false);
        vncStatusText.setLineSpacing(0, 1.25f);
        status.addView(vncStatusText, stack(status));

        LinearLayout controlRow = newRow();
        status.addView(controlRow, stack(status));
        vncStartButton = primaryButton("Start", R.drawable.ic_play, action("vnc_start"));
        vncStopButton = neutralButton("Stop", R.drawable.ic_stop, action("vnc_stop"));
        addRowButton(controlRow, vncStartButton);
        addRowButton(controlRow, vncStopButton);

        vncAuthorizeRow = newRow();
        status.addView(vncAuthorizeRow, stack(status));
        addRowButton(vncAuthorizeRow, tonalButton("Authorise Screen Capture", R.drawable.ic_cast, action("vnc_authorize")));
    }

    private void addVncEngineInput(LinearLayout root) {
        LinearLayout row = newRow();
        root.addView(row, stack(root));
        vncEngineAccessibilityButton = tonalButton("Accessibility", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setVncEngine(Config.VNC_ENGINE_ACCESSIBILITY);
            }
        });
        vncEngineProjectionButton = tonalButton("Screen Capture", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setVncEngine(Config.VNC_ENGINE_PROJECTION);
            }
        });
        addRowButton(row, vncEngineAccessibilityButton);
        addRowButton(row, vncEngineProjectionButton);
        updateVncEngineButtons();
    }

    private void setVncEngine(String engine) {
        String previous = vncEngine;
        vncEngine = engine;
        updateVncEngineButtons();
        if (!loadingConfig && vncSettingsSave != null) {
            if (!vncSettingsSave.saveNow()) {
                vncEngine = previous;
                updateVncEngineButtons();
            }
        }
    }

    private void updateVncEngineButtons() {
        styleChoiceButton(vncEngineAccessibilityButton,
                Config.VNC_ENGINE_ACCESSIBILITY.equals(vncEngine));
        styleChoiceButton(vncEngineProjectionButton,
                Config.VNC_ENGINE_PROJECTION.equals(vncEngine));
        updateVncEngineNote();
    }

    /**
     * Screen Capture cannot start unattended — from Android 14 the consent
     * token is single-use — so pairing it with an availability condition is
     * worth calling out where the choice is made rather than leaving it to be
     * discovered.
     */
    private void updateVncEngineNote() {
        if (vncEngineNoteText == null) {
            return;
        }
        boolean projection = Config.VNC_ENGINE_PROJECTION.equals(vncEngine);
        boolean conditionSelected = (vncEnabledOnMatchingWifiSwitch != null
                && vncEnabledOnMatchingWifiSwitch.isChecked())
                || (vncEnabledWhenVpnConnectedSwitch != null
                && vncEnabledWhenVpnConnectedSwitch.isChecked())
                || (vncEnabledOnCellularOnlySwitch != null
                && vncEnabledOnCellularOnlySwitch.isChecked());
        String note;
        if (projection && conditionSelected) {
            note = "Screen Capture needs a tap to authorise each time it starts, so an availability "
                    + "condition will notify you instead of starting silently. Use Accessibility for "
                    + "unattended start.";
        } else if (projection) {
            note = "Screen Capture: full frame rate, but needs a tap to authorise each time it "
                    + "starts. Input still needs the Accessibility service; raise the frame rate "
                    + "below to make use of it.";
        } else {
            note = "Accessibility: about 3 frames per second, but starts unattended. "
                    + "Needs the Accessibility service enabled.";
        }
        vncEngineNoteText.setText(note);
    }

    private void addVncScaleInput(LinearLayout root) {
        addFieldLabel(root, "Scale");
        LinearLayout row = newRow();
        root.addView(row, belowLabel(root));
        vncScaleFullButton = tonalButton("100%", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setVncScale(Config.VNC_SCALE_FULL);
            }
        });
        vncScaleThreeQuarterButton = tonalButton("75%", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setVncScale(Config.VNC_SCALE_THREE_QUARTER);
            }
        });
        vncScaleHalfButton = tonalButton("50%", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setVncScale(Config.VNC_SCALE_HALF);
            }
        });
        addRowButton(row, vncScaleFullButton);
        addRowButton(row, vncScaleThreeQuarterButton);
        addRowButton(row, vncScaleHalfButton);
        updateVncScaleButtons();
    }

    private void setVncScale(int percent) {
        int previous = vncScalePercent;
        vncScalePercent = percent;
        updateVncScaleButtons();
        if (!loadingConfig && vncSettingsSave != null) {
            if (!vncSettingsSave.saveNow()) {
                vncScalePercent = previous;
                updateVncScaleButtons();
            }
        }
    }

    private void updateVncScaleButtons() {
        styleChoiceButton(vncScaleFullButton, vncScalePercent == Config.VNC_SCALE_FULL);
        styleChoiceButton(vncScaleThreeQuarterButton, vncScalePercent == Config.VNC_SCALE_THREE_QUARTER);
        styleChoiceButton(vncScaleHalfButton, vncScalePercent == Config.VNC_SCALE_HALF);
    }

    private void buildBeaconPanel() {
        Panel frame = addPage("beacon", "Connectivity", "Beacon",
                "Broadcast this phone as a Bluetooth iBeacon that your own receivers can find.",
                R.drawable.ic_radio, Ui.ACCENT_TEAL, true);
        beaconPill = frame.pill;

        beaconEnabledSwitch = addMasterToggle(frame, R.drawable.ic_radio,
                "Enable beacon", "Advertise over Bluetooth at the rate your battery rules allow");
        mirrorToTile(frame, beaconEnabledSwitch);

        LinearLayout identity = section(frame.content, "Identity");
        addFieldLabel(identity, "Proximity UUID — match receivers on this");
        beaconUuidValue = historyText("", 13, COLOR_TEXT, false);
        beaconUuidValue.setTypeface(Typeface.MONOSPACE);
        beaconUuidValue.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        beaconUuidValue.setPadding(dp(14), dp(GAP), dp(14), dp(GAP));
        beaconUuidValue.setMinHeight(dp(FIELD_MIN_HEIGHT));
        beaconUuidValue.setGravity(Gravity.CENTER_VERTICAL);
        beaconUuidValue.setTextIsSelectable(true);
        identity.addView(beaconUuidValue, belowLabel(identity));

        LinearLayout uuidRow = newRow();
        identity.addView(uuidRow, stack(identity));
        addRowButton(uuidRow, tonalButton("Copy UUID", R.drawable.ic_copy, action("beacon_copy_uuid")));
        addRowButton(uuidRow, neutralButton("New UUID", R.drawable.ic_refresh, action("beacon_new_uuid")));

        beaconMajorField = addField(identity, "Major (0-65535)", InputType.TYPE_CLASS_NUMBER);
        beaconMinorField = addField(identity, "Minor (0-65535)", InputType.TYPE_CLASS_NUMBER);

        LinearLayout signal = section(frame.content, "Signal");
        addBeaconTxPowerInput(signal);
        beaconMeasuredPowerField = addField(signal, "Measured power at 1 m (dBm)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

        LinearLayout addRule = section(frame.content, "Add battery rule");
        beaconRuleBatteryField = addField(addRule, "Battery at or above (%)", InputType.TYPE_CLASS_NUMBER);
        beaconRuleIntervalField = addField(addRule, "Broadcast every (seconds, 0 = don't broadcast)",
                InputType.TYPE_CLASS_NUMBER);
        LinearLayout addRuleRow = newRow();
        addRule.addView(addRuleRow, stack(addRule));
        addRowButton(addRuleRow, primaryButton("Add Rule", R.drawable.ic_plus, action("add_beacon_rule")));

        beaconRuleList = listCard(frame.content, "Battery rules");

        LinearLayout status = section(frame.content, "Status");
        beaconStatusList = newColumn();
        status.addView(beaconStatusList, stack(status));

        LinearLayout buttonRow = newRow();
        status.addView(buttonRow, stack(status));
        addRowButton(buttonRow, tonalButton("Bluetooth Settings", R.drawable.ic_bluetooth, action("bluetooth_settings")));
    }

    private void addBeaconTxPowerInput(LinearLayout root) {
        addFieldLabel(root, "Transmit power");
        LinearLayout row = newRow();
        root.addView(row, belowLabel(root));
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

    private void buildRebootPanel() {
        Panel frame = addPage("reboot", "Alerts & automation", "Reboot Automation",
                "Restart the phone on a schedule, from a matching notification, or on command.",
                R.drawable.ic_power, Ui.ACCENT_ORANGE, true);
        rebootPill = frame.pill;

        rebootAutomationEnabledSwitch = addMasterToggle(frame, R.drawable.ic_power,
                "Enable reboot automation", "Uses the Accessibility service to open the power menu");

        LinearLayout notification = section(frame.content, "Notification trigger");
        LinearLayout notifGroup = addToggleGroup(notification);
        rebootNotificationTriggerEnabledSwitch = addGroupedToggle(notifGroup, "Reboot from matching notification");
        rebootTriggerPackageField = addField(notification, "Notification app", InputType.TYPE_CLASS_TEXT);
        configureAppPickerField(rebootTriggerPackageField);
        rebootTriggerTitleField = addField(notification, "Title contains", InputType.TYPE_CLASS_TEXT);
        rebootTriggerTextField = addField(notification, "Text contains", InputType.TYPE_CLASS_TEXT);

        LinearLayout remote = section(frame.content, "Remote Link trigger");
        LinearLayout remoteGroup = addToggleGroup(remote);
        rebootRemoteTriggerEnabledSwitch = addGroupedToggle(remoteGroup, "Reboot from Remote Link");

        LinearLayout schedule = section(frame.content, "Daily schedule");
        LinearLayout scheduleGroup = addToggleGroup(schedule);
        rebootScheduleEnabledSwitch = addGroupedToggle(scheduleGroup, "Enable scheduled reboot");
        rebootScheduleHourField = addField(schedule, "Hour (0-23)", InputType.TYPE_CLASS_NUMBER);
        rebootScheduleMinuteField = addField(schedule, "Minute (0-59)", InputType.TYPE_CLASS_NUMBER);
        rebootWifiPatternField = addField(schedule, "SSID pattern", InputType.TYPE_CLASS_TEXT);
        LinearLayout wifiMatchGroup = addToggleGroup(schedule);
        rebootOnlyWhenWifiNotMatchingSwitch = addGroupedToggle(wifiMatchGroup, "Only reboot when SSID doesn't match");

        LinearLayout automation = section(frame.content, "Automation");
        rebootPinSequenceField = addField(automation, "PIN sequence (optional)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        rebootDelayedTestSecondsField = addField(automation, "Delayed lock test delay (seconds)", InputType.TYPE_CLASS_NUMBER);
        rebootPowerDialogWaitMsField = addField(automation, "Power dialog wait (milliseconds)", InputType.TYPE_CLASS_NUMBER);
        rebootStepWaitMsField = addField(automation, "Step wait (milliseconds)", InputType.TYPE_CLASS_NUMBER);

        addStackedButton(automation, tonalButton("Test Now", R.drawable.ic_power, action("test_reboot_now")));
        addStackedButton(automation, tonalButton("Delayed Lock Test", R.drawable.ic_clock, action("test_reboot_delayed")));
        addStackedButton(automation, neutralButton("Accessibility", R.drawable.ic_user, action("accessibility_settings")));
    }

    private void buildRemoteLinkPanel() {
        Panel frame = addPage("remote_link", "Connectivity", "Remote Link",
                "A secure connection to your server for commands, alerts and backups.",
                R.drawable.ic_link, Ui.ACCENT_CYAN, true);
        remoteLinkPill = frame.pill;

        remoteLinkEnabledSwitch = addMasterToggle(frame, R.drawable.ic_link,
                "Enable Remote Link", "Keep a connection open to your server");
        mirrorToTile(frame, remoteLinkEnabledSwitch);
        // The Remote Link notification is shown or hidden per channel in
        // Android's notification settings.

        LinearLayout connection = section(frame.content, "Connection");
        remoteLinkEndpointField = addField(connection, "Remote Link endpoint", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        remoteLinkUsernameField = addField(connection, "Username", InputType.TYPE_CLASS_TEXT);
        remoteLinkPasswordField = addField(connection, "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        remoteLinkHeartbeatSecondsField = addField(connection, "Heartbeat (seconds)", InputType.TYPE_CLASS_NUMBER);
        LinearLayout sslGroup = addToggleGroup(connection);
        remoteLinkAcceptAnySslCertSwitch = addGroupedToggle(sslGroup, "Accept any SSL cert",
                "Skips certificate checks, as a self-signed server needs");

        LinearLayout row = newRow();
        connection.addView(row, stack(connection));
        addRowButton(row, tonalButton("Reconnect", R.drawable.ic_refresh, action("remote_link_reconnect")));
        addRowButton(row, tonalButton("Ping", R.drawable.ic_activity, action("remote_link_ping")));

        LinearLayout statistics = section(frame.content, "Link statistics");
        remoteLinkUptimeValue = addInfoRow(
                statistics, "Up — last 24 hours", "100.000%", COLOR_TEXT);
        remoteLinkDowntimeValue = addInfoRow(
                statistics, "Down — last 24 hours", "00:00:00", COLOR_TEXT);

        LinearLayout testStatus = section(frame.content, "Link test");
        remoteLinkLatencyValue = addInfoRow(testStatus, "Latency", "Unknown", COLOR_TEXT_DIM);
        remoteLinkUploadThroughputValue = addInfoRow(
                testStatus, "Send throughput", "Unknown", COLOR_TEXT_DIM);
        remoteLinkDownloadThroughputValue = addInfoRow(
                testStatus, "Receive throughput", "Unknown", COLOR_TEXT_DIM);

        LinearLayout testRow = newRow();
        testStatus.addView(testRow, stack(testStatus));
        remoteLinkLatencyButton = tonalButton("Test Latency", action("remote_link_latency"));
        remoteLinkThroughputButton = tonalButton("Test Throughput", action("remote_link_throughput"));
        addRowButton(testRow, remoteLinkLatencyButton);
        addRowButton(testRow, remoteLinkThroughputButton);
    }

    private void buildSettingsTransferPanel() {
        Panel frame = addPage("backup", "System", "Backup",
                "Save every setting, credential, history item and VPN file to your server, and restore it later.",
                R.drawable.ic_archive, Ui.ACCENT_TEAL, false);
        systemBackupPill = frame.pill;

        LinearLayout actions = section(frame.content, "Backup & restore");
        LinearLayout row = newRow();
        actions.addView(row, stack(actions));
        backUpButton = primaryButton("Back Up Now", R.drawable.ic_upload_cloud, action("backup_now"));
        restoreBackupButton = tonalButton("Restore", R.drawable.ic_download, action("restore_backup"));
        addRowButton(row, backUpButton);
        addRowButton(row, restoreBackupButton);
        addNote(actions, "Needs a connected Remote Link and a server with backups enabled. "
                + "Restoring replaces everything on this phone with the saved copy.");
    }

    private void buildPermissionsPanel() {
        Panel frame = addPage("permissions", "System", "Permissions",
                "What System Manager needs in order to do its job. Tap anything marked Needed to fix it.",
                R.drawable.ic_shield_check, Ui.ACCENT_EMERALD, true);
        permissionsPill = frame.pill;

        LinearLayout readiness = section(frame.content, "Readiness");
        statusContainer = newColumn();
        readiness.addView(statusContainer, stack(readiness));

        wifiMonitorWarning = historyText("", 12.5f, COLOR_BAD, false);
        wifiMonitorWarning.setVisibility(View.GONE);
        readiness.addView(wifiMonitorWarning, stack(readiness));

        LinearLayout grant = listCard(frame.content, "Grant access");
        addActionRow(grant, R.drawable.ic_pin, "Location & Wi-Fi", "Fine location and nearby Wi-Fi devices", "common_permissions");
        addActionRow(grant, R.drawable.ic_gps, "Background location", "Allow all the time", "background_location");
        addActionRow(grant, R.drawable.ic_clock, "Exact alarms", "Run schedules on time", "exact_alarm");
        addActionRow(grant, R.drawable.ic_battery, "Battery settings", "Allow unrestricted background use", "battery");
        addActionRow(grant, R.drawable.ic_sliders, "App settings", "Open Android's page for System Manager", "app_settings");
        addActionRow(grant, R.drawable.ic_pin, "Location settings", "Turn device location on", "location_settings");
        addActionRow(grant, R.drawable.ic_bell, "Notification access", "Read notifications for alerts and backup", "notification_access");
        addActionRow(grant, R.drawable.ic_user, "Accessibility", "Reboot automation and the Wi-Fi monitor", "accessibility_settings");
        addActionRow(grant, R.drawable.ic_volume, "Do Not Disturb access", "Needed by volume rules that change DND", "dnd_access");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addActionRow(grant, R.drawable.ic_bell_ring, "Notification permission", "Post notifications", "notifications");
        }
        addActionRow(grant, R.drawable.ic_bluetooth, "Bluetooth advertise", "Needed by the beacon", "bluetooth_permission");
        addActionRow(grant, R.drawable.ic_bluetooth, "Bluetooth settings", "Turn Bluetooth on", "bluetooth_settings");

        LinearLayout service = section(frame.content, "Service notifications");
        LinearLayout serviceNotifications = addToggleGroup(service);
        showWifiMonitorNotificationSwitch = addGroupedToggle(serviceNotifications, "Wi-Fi monitor");
        addNote(service, "Other service notifications are shown or hidden per channel in "
                + "Android's notification settings.");
        LinearLayout r7 = newRow();
        service.addView(r7, stack(service));
        addRowButton(r7, tonalButton("Notification Settings", R.drawable.ic_bell, action("notification_settings")));
    }

    private void buildUpgradePanel() {
        Panel frame = addPage("upgrade", "System", "Upgrade",
                "Install new versions of System Manager from your server.",
                R.drawable.ic_download, Ui.ACCENT_BLUE, false);
        upgradePanel = frame;
        upgradePill = frame.pill;
        dashboard.setTileVisible("upgrade", false);

        LinearLayout metadata = section(frame.content, "Version");
        upgradeCurrentVersionValue = addInfoRow(
                metadata, "Current version", AppVersion.name(this), COLOR_TEXT);
        upgradeAvailableVersionValue = addInfoRow(
                metadata, "Available version", "Press Refresh", COLOR_TEXT_DIM);
        upgradeDateValue = addInfoRow(metadata, "File date", "Unknown", COLOR_TEXT_DIM);
        upgradeSizeValue = addInfoRow(metadata, "File size", "Unknown", COLOR_TEXT_DIM);

        LinearLayout automatic = section(frame.content, "Automatic upgrades");
        LinearLayout automaticGroup = addToggleGroup(automatic);
        upgradeAutoSwitch = addGroupedToggle(automaticGroup, "Enable automatic upgrades",
                "Offer a new version when you open the app");

        LinearLayout buttons = newRow();
        automatic.addView(buttons, stack(automatic));
        upgradeRefreshButton = tonalButton("Refresh", R.drawable.ic_refresh, action("refresh_upgrade"));
        upgradeInstallButton = primaryButton("Upgrade", R.drawable.ic_download, action("install_upgrade"));
        addRowButton(buttons, upgradeRefreshButton);
        addRowButton(buttons, upgradeInstallButton);
    }

    private void buildLogPanel() {
        Panel frame = addPage("log", "System", "Log",
                "A record of what System Manager has been doing.",
                R.drawable.ic_terminal, Ui.ACCENT_SLATE, true);
        logPill = frame.pill;

        logEnabledSwitch = addMasterToggle(frame, R.drawable.ic_terminal,
                "Enable logging", "Keep a rolling log on this phone");

        LinearLayout retention = section(frame.content, "Retention");
        logMaxLinesField = addField(retention, "Retention (lines)", InputType.TYPE_CLASS_NUMBER);

        LinearLayout buttons = newRow();
        retention.addView(buttons, stack(retention));
        addRowButton(buttons, tonalButton("Refresh", R.drawable.ic_refresh, action("refresh_log")));
        addRowButton(buttons, neutralButton("Clear", R.drawable.ic_trash, action("clear_log")));

        TextView consoleHeader = Ui.caps(this, "Console");
        consoleHeader.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams headerLp = matchWrapParams();
        headerLp.topMargin = dp(24);
        headerLp.bottomMargin = dp(10);
        frame.content.addView(consoleHeader, headerLp);

        logView = new TextView(this);
        logView.setText("");
        logView.setTextColor(COLOR_LOG_FG);
        logView.setTextSize(11);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setBackground(roundedFill(COLOR_LOG_BG, SECTION_CORNER, 1, Ui.alpha(COLOR_LOG_FG, 0.12f)));
        logView.setPadding(dp(14), dp(14), dp(14), dp(14));
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
        frame.content.addView(logView, matchWrapParams());
    }

    private void buildAppearancePanel() {
        Panel frame = addPage("appearance", "System", "Appearance",
                "Choose how System Manager looks. It can follow your phone's setting or stay light or dark.",
                R.drawable.ic_contrast, Ui.ACCENT_VIOLET, false);

        LinearLayout theme = section(frame.content, "Theme");
        LinearLayout choices = newRow();
        theme.addView(choices, stack(theme));
        int mode = Ui.themeMode(this);
        addThemeChoice(choices, "System", R.drawable.ic_contrast, Ui.MODE_SYSTEM, mode);
        addThemeChoice(choices, "Light", R.drawable.ic_sun, Ui.MODE_LIGHT, mode);
        addThemeChoice(choices, "Dark", R.drawable.ic_moon, Ui.MODE_DARK, mode);
        addNote(theme, "System follows your phone's dark mode setting, including any schedule it runs.");

        String modeLabel = mode == Ui.MODE_LIGHT ? "LIGHT" : mode == Ui.MODE_DARK ? "DARK" : "AUTOMATIC";
        setPillState(frame.pill, modeLabel, COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
        frame.tile.setDetail(mode == Ui.MODE_SYSTEM ? (Ui.dark ? "Following the phone: dark" : "Following the phone: light")
                : "Always " + (mode == Ui.MODE_DARK ? "dark" : "light"));

        LinearLayout about = section(frame.content, "About");
        addInfoRow(about, "Version", AppVersion.name(this) + " (build " + AppVersion.code(this) + ")", COLOR_TEXT);
        addInfoRow(about, "Package", getPackageName(), COLOR_TEXT);
        addInfoRow(about, "Device", Dashboard.deviceName(this), COLOR_TEXT);
        addInfoRow(about, "Android", Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT, COLOR_TEXT);
    }

    private void addThemeChoice(LinearLayout row, String label, int iconRes, final int mode, int current) {
        boolean selected = mode == current;
        LinearLayout cell = newColumn();
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(8), dp(16), dp(8), dp(14));
        int fg = selected ? COLOR_PRIMARY_ON_CONTAINER : COLOR_TEXT_DIM;
        cell.setBackground(Ui.ripple(COLOR_RIPPLE_LIGHT,
                roundedFill(selected ? COLOR_PRIMARY_CONTAINER : COLOR_FIELD_BG, FIELD_CORNER + 2,
                        selected ? 2 : 1, selected ? COLOR_PRIMARY : COLOR_FIELD_BORDER), dp(FIELD_CORNER + 2)));
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setContentDescription(label + " theme" + (selected ? ", selected" : ""));
        cell.addView(Ui.icon(this, iconRes, 26, selected ? COLOR_PRIMARY : COLOR_TEXT_DIM),
                new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView text = historyText(label, 13.5f, fg, true);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.topMargin = dp(8);
        cell.addView(text, textLp);
        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setThemeModeAndRecreate(mode);
            }
        });
        row.addView(cell, inRow(row, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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
        Panel historyPage = pagesById.get("history");
        if (historyPage != null) {
            historyPage.tile.setStatus(count == 0 ? "Empty" : (count == 1 ? "1 saved" : count + " saved"),
                    count > 0 ? Ui.TONE_OK : Ui.TONE_NEUTRAL);
            historyPage.tile.setDetail("Kept on this phone");
        }
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
        Panel history = pagesById.get("history");
        final ScrollView sv = history == null ? null : history.scroll;
        if (sv == null) {
            return;
        }
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
        setTileDetail("volume", rules.isEmpty() ? "No schedule" : "Next at " + nextVolumeRuleTime(rules));
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
                hairline.setBackgroundColor(COLOR_BORDER);
                volumeRuleList.addView(hairline, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
            addVolumeRuleRow(rules.get(i));
        }
    }

    /** The next rule time of day to come, wrapping to tomorrow's first rule after the last one. */
    private String nextVolumeRuleTime(List<Config.VolumeRule> rules) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int nowMinute = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        Config.VolumeRule next = null;
        Config.VolumeRule earliest = null;
        for (Config.VolumeRule rule : rules) {
            if (earliest == null || rule.minuteOfDay() < earliest.minuteOfDay()) {
                earliest = rule;
            }
            if (rule.minuteOfDay() > nowMinute && (next == null || rule.minuteOfDay() < next.minuteOfDay())) {
                next = rule;
            }
        }
        return (next != null ? next : earliest).displayTime();
    }

    /** Shrinks a button to sit beside text in a list row. */
    private Button compact(Button button) {
        button.setMinHeight(dp(40));
        button.setMinimumHeight(dp(40));
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        return button;
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

        Button delete = compact(neutralButton("Delete", R.drawable.ic_trash, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteVolumeRule(rule);
            }
        }));
        top.addView(delete, inRow(top, dp(112), dp(40), 0f));

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
        openVpnHasProfile = hasProfile;
        openVpnProfileReady = hasProfile && meta != null && meta.allSlotsSatisfied();

        renderOpenVpnSummary(meta);
        renderOpenVpnSlots(meta);
        openVpnEditRow.setVisibility(hasProfile ? View.VISIBLE : View.GONE);

        setTileDetail("vpn", meta != null && meta.hasProfile()
                ? meta.remoteHost + ":" + meta.remotePort : "Import one to connect");

        boolean showAuth = meta != null && (meta.authUserPass || meta.keyEncrypted);
        vpnAuthSection.setVisibility(showAuth ? View.VISIBLE : View.GONE);
        if (showAuth) {
            vpnUserPassBlock.setVisibility(meta.authUserPass ? View.VISIBLE : View.GONE);
            vpnPassphraseBlock.setVisibility(meta.keyEncrypted ? View.VISIBLE : View.GONE);
            // The gap above the passphrase only belongs there when the username block is showing.
            ((LinearLayout.LayoutParams) vpnPassphraseBlock.getLayoutParams()).topMargin =
                    meta.authUserPass ? dp(GAP) : 0;
        }
        vpnTapSection.setVisibility(meta != null && meta.isTap() ? View.VISIBLE : View.GONE);

        refreshOpenVpnLiveState();

        resolveVpnEngineVersion();
    }

    private void refreshOpenVpnLiveState() {
        if (openVpnPill == null || openVpnStatusText == null) {
            return;
        }
        String simpleState = OpenVpnStateStore.simpleState(this);
        boolean connectedOrConnecting = OpenVpnStateStore.SIMPLE_CONNECTED.equals(simpleState)
                || OpenVpnStateStore.SIMPLE_CONNECTING.equals(simpleState);
        setOpenVpnPill(openVpnHasProfile, simpleState);
        dashboardTileToggle("vpn", openVpnHasProfile && connectedOrConnecting);
        openVpnStatusText.setText(openVpnStatusLine(simpleState));
        applyButtonState(vpnConnectButton, openVpnProfileReady && !connectedOrConnecting,
                COLOR_PRIMARY, COLOR_ON_ACCENT);
        applyButtonState(vpnDisconnectButton, connectedOrConnecting, COLOR_DANGER, COLOR_ON_DANGER);
    }

    private void setOpenVpnPill(boolean hasProfile, String simpleState) {
        if (hasProfile && OpenVpnStateStore.SIMPLE_CONNECTED.equals(simpleState)) {
            setPillState(openVpnPill, "CONNECTED", COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
        } else if (hasProfile && OpenVpnStateStore.SIMPLE_CONNECTING.equals(simpleState)) {
            setPillState(openVpnPill, "CONNECTING", COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
        } else if (hasProfile && OpenVpnStateStore.SIMPLE_ERROR.equals(simpleState)) {
            setPillState(openVpnPill, "ERROR", COLOR_DANGER_CONTAINER, COLOR_DANGER_ON_CONTAINER);
        } else if (hasProfile) {
            setPillState(openVpnPill, "OFF", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
        } else {
            setPillState(openVpnPill, "NO PROFILE", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
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
        openVpnProfileSummary.setBackground(roundedFill(COLOR_GROUPED, GROUP_CORNER, 1, COLOR_BORDER));
        openVpnProfileSummary.setPadding(dp(14), dp(14), dp(14), dp(14));
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
        if (meta == null || meta.requiredSlots.isEmpty()) {
            TextView empty = historyText(meta == null ? "Import a profile to see its files"
                    : "Profile references no external files", 13, COLOR_TEXT_DIM, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(20), dp(16), dp(20));
            openVpnSlotList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        boolean first = true;
        for (String slot : meta.requiredSlots) {
            if (!first) {
                View hairline = new View(this);
                hairline.setBackgroundColor(COLOR_BORDER);
                openVpnSlotList.addView(hairline, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
            first = false;
            addVpnSlotRow(slot, meta.satisfiedSlots.get(slot));
        }
    }

    private void addVpnSlotRow(final String slotId, String satisfiedBy) {
        LinearLayout row = newRow();
        row.setMinimumHeight(dp(STATUS_ROW_MIN_HEIGHT + 6));
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        row.setClickable(true);
        row.setBackground(Ui.ripple(COLOR_RIPPLE_LIGHT, roundedFill(0, 0, 0, 0), 0));

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
        boolean showing = openVpnPanel != null && currentPage == openVpnPanel;
        if (!showing && !OpenVpnProfileStore.hasProfile(this)) {
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

    /** Rounded fill with the corner and stroke given in dp. */
    private GradientDrawable roundedFill(int color, int cornerDp, int strokeDp, int strokeColor) {
        return Ui.roundedFill(color, dp(cornerDp), strokeDp > 0 ? dp(strokeDp) : 0, strokeColor);
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
                refreshOpenVpnLiveState();
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
                refreshBeaconPanel();
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

    private void registerVncStateReceiver() {
        if (vncStateReceiver != null) {
            return;
        }
        vncStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                syncVncMasterSwitchFromConfig();
                refreshVncPanel();
            }
        };
        IntentFilter filter = new IntentFilter(VncStateStore.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vncStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(vncStateReceiver, filter);
        }
    }

    private void unregisterVncStateReceiver() {
        if (vncStateReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(vncStateReceiver);
        } catch (RuntimeException ignored) {
        }
        vncStateReceiver = null;
    }

    /** Keeps an open settings panel truthful when Remote Link changes VNC. */
    private void syncVncMasterSwitchFromConfig() {
        if (vncEnabledSwitch == null) {
            return;
        }
        boolean enabled = Config.get(this).vncEnabled();
        if (vncEnabledSwitch.isChecked() == enabled) {
            return;
        }
        boolean wasLoading = loadingConfig;
        loadingConfig = true;
        try {
            vncEnabledSwitch.setChecked(enabled);
        } finally {
            loadingConfig = wasLoading;
        }
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

    /** Starts connecting; false when something is missing and nothing was started. */
    private boolean vpnConnect() {
        if (!OpenVpnProfileStore.hasProfile(this)) {
            Toast.makeText(this, "Import a profile first", Toast.LENGTH_LONG).show();
            return false;
        }
        OpenVpnProfileStore.Meta meta = OpenVpnProfileStore.readMeta(this);
        if (!meta.allSlotsSatisfied()) {
            Toast.makeText(this, "Import the missing certificate files first", Toast.LENGTH_LONG).show();
            return false;
        }
        if (meta.keyEncrypted && Config.get(this).vpnKeyPassphrase().isEmpty()) {
            Toast.makeText(this, "Enter the private key passphrase", Toast.LENGTH_LONG).show();
            return false;
        }
        if (meta.authUserPass && Config.get(this).vpnUsername().isEmpty()) {
            Toast.makeText(this, "Enter the VPN username", Toast.LENGTH_LONG).show();
            return false;
        }
        Intent consent = OpenVpnManager.consentIntentOrNull(this);
        if (consent == null) {
            OpenVpnManager.connect(this, "ui-connect");
            Toast.makeText(this, "VPN connect requested", Toast.LENGTH_SHORT).show();
            refreshStatusAndLog();
            return true;
        }
        pendingVpnConnectAfterConsent = true;
        try {
            startActivityForResult(consent, REQUEST_VPN_CONSENT);
        } catch (RuntimeException e) {
            pendingVpnConnectAfterConsent = false;
            LogStore.append(this, "vpn", "VPN consent launch failed: " + e.getMessage());
            Toast.makeText(this, "Could not request VPN permission", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
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

    private void addHistoryEntry(final NotificationHistoryStore.Entry entry) {
        boolean urgent = entry.highPriority;
        LinearLayout item = newColumn();
        item.setPadding(dp(15), dp(13), dp(15), dp(14));
        item.setBackground(Ui.roundedFill(urgent ? COLOR_DANGER_CONTAINER : COLOR_SURFACE, dp(18), dp(1),
                urgent ? Ui.alpha(COLOR_DANGER, 0.4f) : COLOR_BORDER));
        Ui.elevate(item, urgent ? 0f : 1f);

        LinearLayout meta = newRow();
        item.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (urgent) {
            meta.addView(Ui.icon(this, R.drawable.ic_bell_ring, 14, COLOR_DANGER_ON_CONTAINER),
                    inRow(meta, dp(14), dp(14), 0f));
        }
        TextView source = historyText(entry.source.toUpperCase(Locale.US), 10.5f,
                urgent ? COLOR_DANGER_ON_CONTAINER : COLOR_PRIMARY, true);
        source.setLetterSpacing(0.1f);
        source.setSingleLine(true);
        source.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sourceLp = inRow(meta, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (urgent) {
            sourceLp.leftMargin = dp(6);
        }
        meta.addView(source, sourceLp);

        TextView time = historyText(formatHistoryTime(entry.timestampMillis), 11.5f,
                urgent ? COLOR_DANGER_ON_CONTAINER : COLOR_TEXT_FAINT, false);
        time.setSingleLine(true);
        meta.addView(time, inRow(meta, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        TextView title = historyText(historyDisplayTitle(entry), 15,
                urgent ? COLOR_DANGER_ON_CONTAINER : COLOR_TEXT, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(title, topMarginParams(8));

        String detail = historyDetail(entry);
        if (hasText(detail)) {
            TextView body = historyText(detail, 13, urgent ? COLOR_DANGER_ON_CONTAINER : COLOR_TEXT_DIM, false);
            body.setLineSpacing(0, 1.2f);
            body.setMaxLines(3);
            body.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(body, topMarginParams(5));
        }

        Bitmap image = decodeHistoryImage(entry);
        if (image != null) {
            AspectImageView imageView = new AspectImageView(this);
            imageView.setImageBitmap(image);
            imageView.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_BORDER));
            item.addView(imageView, topMarginParams(10));

            LinearLayout imageActions = newRow();
            item.addView(imageActions, topMarginParams(10));
            addRowButton(imageActions, tonalButton("Save", R.drawable.ic_download, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    saveNotificationImage(entry);
                }
            }));
            addRowButton(imageActions, tonalButton("Share", R.drawable.ic_send, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    shareNotificationImage(entry);
                }
            }));
        }

        setHistorySwipeToClear(item, entry);
        LinearLayout.LayoutParams itemLp = matchWrapParams();
        if (notificationHistoryList.getChildCount() > 0) {
            itemLp.topMargin = dp(10);
        }
        notificationHistoryList.addView(item, itemLp);
    }

    private void addHistoryEmptyRow(String text) {
        LinearLayout box = newColumn();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(16), dp(34), dp(16), dp(34));
        box.setBackground(Ui.roundedFill(COLOR_SURFACE, dp(18), dp(1), COLOR_BORDER));
        box.addView(Ui.icon(this, R.drawable.ic_inbox, 34, COLOR_TEXT_FAINT),
                new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView empty = historyText(text, 14, COLOR_TEXT_DIM, false);
        empty.setGravity(Gravity.CENTER);
        box.addView(empty, topMarginParams(12));
        notificationHistoryList.addView(box, matchWrapParams());
    }

    private TextView historyText(String text, float sp, int color, boolean medium) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(medium ? Ui.FONT_BOLD : Typeface.DEFAULT);
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

        // Sized for the widest the image can be shown, in either orientation: the decode is cached
        // and would otherwise stay soft after the screen turns.
        android.util.DisplayMetrics screen = getResources().getDisplayMetrics();
        int widest = Math.min(Math.max(screen.widthPixels, screen.heightPixels), dp(Ui.CONTENT_MAX_WIDTH));
        int targetWidth = Math.max(dp(160), widest - dp(GAP * 6));
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
    //  VNC panel
    // ============================================================

    private void refreshVncPanel() {
        if (vncPill == null || vncStatusText == null) {
            return;
        }
        String state = VncStateStore.state(this);
        boolean enabled = switchValue(vncEnabledSwitch, Config.get(this).vncEnabled());
        setVncPill(enabled, state);
        vncStatusText.setText(vncStatusLine(enabled, state));
        String client = VncStateStore.clientAddress(this);
        setTileDetail("vnc", !enabled ? "Remote screen"
                : !client.isEmpty() ? "Client " + client
                : "Port " + Config.get(this).vncPort() + " · " + Config.vncEngineLabel(vncEngine));

        // Stop is available whenever the service is up at all, waiting
        // included, so the user can hold it before walking into range.
        boolean running = enabled && !VncStateStore.STATE_OFF.equals(state);
        boolean retryable = VncStateStore.STATE_OFF.equals(state)
                || VncStateStore.STATE_BLOCKED.equals(state)
                || VncStateStore.STATE_ERROR.equals(state);
        applyButtonState(vncStartButton, enabled && retryable, COLOR_PRIMARY, COLOR_ON_ACCENT);
        applyButtonState(vncStopButton, running, COLOR_DANGER, COLOR_ON_DANGER);
        if (vncAuthorizeRow != null) {
            vncAuthorizeRow.setVisibility(
                    Config.VNC_ENGINE_PROJECTION.equals(vncEngine) ? View.VISIBLE : View.GONE);
        }
        updateVncEngineNote();
        refreshVncProbeText();
    }

    /**
     * The consent dialog can only be raised from an activity, and the token it
     * returns is single-use, so this is the one unavoidable manual step of the
     * Screen Capture engine.
     */
    private void requestProjectionConsent() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            Toast.makeText(this, "Screen capture is unavailable on this device", Toast.LENGTH_LONG).show();
            return;
        }
        if (!Config.get(this).vncEnabled()) {
            Toast.makeText(this, "Enable the VNC server first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent request = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    ? manager.createScreenCaptureIntent(
                            MediaProjectionConfig.createConfigForDefaultDisplay())
                    : manager.createScreenCaptureIntent();
            startActivityForResult(request, REQUEST_PROJECTION_CONSENT);
        } catch (RuntimeException e) {
            LogStore.append(this, "vnc", "Could not ask for screen capture consent: " + e.getMessage());
            Toast.makeText(this, "Could not open the screen capture prompt", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshVncProbeText() {
        if (vncProbeText == null) {
            return;
        }
        boolean probing = VncCaptureProbe.isRunning();
        applyButtonState(vncTestCaptureButton, !probing, COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
        if (probing) {
            vncProbeText.setText("Capturing for " + VncCaptureProbe.DURATION_SECONDS + "s…");
            return;
        }
        String result = VncStateStore.probeResult(this);
        vncProbeText.setText(result.isEmpty()
                ? "Measures the Accessibility engine's frame rate and how much of the screen "
                        + "changes between frames. Never uses Screen Capture, whose consent is "
                        + "single-use and worth saving for a real session."
                : result);
    }

    /**
     * Bounded on purpose: capture costs battery and there is no client to serve
     * yet, so it runs on demand rather than continuously.
     */
    private void testVncCapture() {
        String blocking = SystemManagerAccessibilityService.screenshotBlockedReason(this);
        if (blocking != null) {
            Toast.makeText(this, blocking, Toast.LENGTH_LONG).show();
            VncStateStore.setProbeResult(this, "Cannot capture: " + blocking);
            refreshVncProbeText();
            return;
        }
        if (!VncCaptureProbe.start(this, new VncCaptureProbe.Callback() {
            @Override
            public void onFinished(String summary) {
                refreshVncProbeText();
            }
        })) {
            return;
        }
        refreshVncProbeText();
    }

    private void setVncPill(boolean enabled, String state) {
        if (!enabled) {
            setPillState(vncPill, "DISABLED", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
            return;
        }
        if (VncStateStore.STATE_LISTENING.equals(state) || VncStateStore.STATE_CONNECTED.equals(state)) {
            setPillState(vncPill, VncStateStore.pillLabel(state),
                    COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
        } else if (VncStateStore.STATE_BLOCKED.equals(state) || VncStateStore.STATE_ERROR.equals(state)) {
            setPillState(vncPill, VncStateStore.pillLabel(state),
                    COLOR_DANGER_CONTAINER, COLOR_DANGER_ON_CONTAINER);
        } else if (VncStateStore.STATE_OFF.equals(state)) {
            // Armed but not running: stopped by hand, not disabled.
            setPillState(vncPill, "STOPPED", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
        } else {
            setPillState(vncPill, VncStateStore.pillLabel(state),
                    COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
        }
    }

    private String vncStatusLine(boolean enabled, String state) {
        StringBuilder text = new StringBuilder();
        if (!enabled) {
            text.append("Off");
        } else if (VncStateStore.STATE_OFF.equals(state)) {
            text.append("Stopped");
        } else {
            text.append(VncStateStore.label(state));
            String detail = VncStateStore.detail(this);
            if (!detail.isEmpty()) {
                text.append(" — ").append(detail);
            }
        }

        String listen = VncStateStore.listenAddress(this);
        if (!listen.isEmpty()) {
            text.append("\n").append(listen);
        }
        String client = VncStateStore.clientAddress(this);
        text.append("\n").append(client.isEmpty() ? "No client connected" : "Client " + client);
        text.append("\nEngine: ").append(Config.vncEngineLabel(vncEngine))
                .append(" · ").append(vncScalePercent).append("%");

        if (VncSecretStore.isTruncated(this)) {
            text.append("\nPassword is longer than 8 characters; VNC will use the first 8.");
        }
        return text.toString();
    }

    private boolean saveVncConfigOnly() {
        if (!requireInteger(vncPortField, "VNC port", 1024, 65535)
                || !requireInteger(vncMaxFpsField, "VNC max frames per second", 1, 60)
                || !requireInteger(vncIdleTimeoutField, "VNC idle timeout", 0, 1440)) {
            return false;
        }
        Config.get(this).saveVncConfig(
                vncEnabledSwitch.isChecked(),
                vncRemoteCommandEnabledSwitch.isChecked(),
                vncEngine,
                text(vncPortField),
                vncViewOnlySwitch.isChecked(),
                text(vncAllowedClientsField),
                vncEnabledOnMatchingWifiSwitch.isChecked(),
                text(vncMatchingWifiSsidField),
                vncEnabledWhenVpnConnectedSwitch.isChecked(),
                vncEnabledOnCellularOnlySwitch.isChecked(),
                vncScalePercent,
                text(vncMaxFpsField),
                vncWakeOnConnectSwitch.isChecked(),
                text(vncIdleTimeoutField));
        VncSecretStore.setPassword(this, text(vncPasswordField));
        VncManager.sync(this, "settings-live");
        refreshStatusAndLog();
        return true;
    }

    private boolean saveVncRemoteCommandToggleOnly() {
        Config.get(this).saveVncRemoteCommandConfig(vncRemoteCommandEnabledSwitch.isChecked());
        refreshStatusAndLog();
        return true;
    }

    /** Disabling must persist even when another field on the panel is invalid. */
    private boolean saveVncDisabledOnly() {
        Config.get(this).setVncEnabled(false);
        VncManager.sync(this, "vnc-disabled-live");
        refreshStatusAndLog();
        return true;
    }

    private void startVncServer() {
        if (!Config.get(this).vncEnabled()) {
            Toast.makeText(this, "Enable the VNC server first", Toast.LENGTH_SHORT).show();
            return;
        }
        String blocking = VncManager.blockingReason(this);
        if (blocking != null) {
            Toast.makeText(this, blocking, Toast.LENGTH_LONG).show();
        }
        VncManager.start(this, "ui-start");
        refreshStatusAndLog();
    }

    /**
     * Holds rather than stops: when availability conditions are selected, the
     * service keeps the connection watcher so a later change can re-arm it. A
     * settings edit will not undo the hold; with no conditions, Start will.
     * Turning the master switch off is what actually takes the service down.
     */
    private void stopVncServer() {
        VncManager.hold(this, "ui-stop");
        refreshStatusAndLog();
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
        setTileDetail("beacon", !enabled ? "Bluetooth iBeacon"
                : BeaconStateStore.STATE_ADVERTISING.equals(state)
                ? Config.beaconIntervalDisplay(BeaconStateStore.intervalSeconds(this))
                : BeaconStateStore.label(state));
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
        if (BeaconStateStore.STATE_STARTING.equals(state) || BeaconStateStore.STATE_RETRYING.equals(state)) {
            setPillState(beaconPill, BeaconStateStore.label(state).toUpperCase(java.util.Locale.US),
                    COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
            return;
        }
        setPillState(beaconPill, "ATTENTION", COLOR_DANGER_CONTAINER, COLOR_DANGER_ON_CONTAINER);
    }

    private void renderBeaconStatus(Config config, boolean enabled, String state) {
        if (beaconStatusList == null) {
            return;
        }
        beaconAdvertisingDurationValue = null;
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

        int battery = BatteryReader.batteryPercent(this);
        if (battery < 0) {
            battery = BeaconStateStore.batteryPercent(this);
        }
        addInfoRow(beaconStatusList, "Battery", battery < 0 ? "Unknown" : battery + "%", COLOR_TEXT);

        addInfoRow(beaconStatusList, "Transmit power",
                Config.beaconTxPowerDisplay(beaconTxPowerDbm), COLOR_TEXT);
        addInfoRow(beaconStatusList, "Major / minor",
                config.beaconMajor() + " / " + config.beaconMinor(), COLOR_TEXT);

        if (advertising) {
            long since = BeaconStateStore.advertisingSinceMillis(this);
            if (since > 0L) {
                beaconAdvertisingDurationValue = addInfoRow(beaconStatusList, "Broadcasting for",
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
                hairline.setBackgroundColor(COLOR_BORDER);
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
            StatusPill activePill = new StatusPill(this, true);
            activePill.set("ACTIVE", Ui.TONE_OK);
            top.addView(activePill, inRow(top,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0f));
        }

        Button delete = compact(neutralButton("Delete", R.drawable.ic_trash, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteBeaconRule(rule);
            }
        }));
        top.addView(delete, inRow(top, dp(112), dp(40), 0f));

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

    private void updateTimeBasedStatus() {
        TextView duration = beaconAdvertisingDurationValue;
        if (duration != null && BeaconStateStore.isAdvertising(this)) {
            long since = BeaconStateStore.advertisingSinceMillis(this);
            if (since > 0L) {
                duration.setText(formatDuration(System.currentTimeMillis() - since));
            }
        }
        refreshRemoteLinkStatistics();
    }

    // ============================================================
    //  Cards, sections & pills
    // ============================================================

    /**
     * A titled card on a page. The heading sits above the card, the way Android's own
     * settings group things; a null title gives an untitled card.
     */
    private LinearLayout section(LinearLayout container, String title) {
        boolean titled = title != null && !title.isEmpty();
        if (titled) {
            TextView header = Ui.caps(this, title);
            header.setPadding(dp(4), 0, dp(4), 0);
            LinearLayout.LayoutParams headerLp = matchWrapParams();
            headerLp.topMargin = dp(22);
            headerLp.bottomMargin = dp(10);
            container.addView(header, headerLp);
        }
        LinearLayout card = newColumn();
        card.setBackground(roundedFill(COLOR_SURFACE, SECTION_CORNER, 1, COLOR_BORDER));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        Ui.elevate(card, 1f);
        LinearLayout.LayoutParams cardLp = matchWrapParams();
        if (!titled) {
            cardLp.topMargin = dp(14);
        }
        container.addView(card, cardLp);
        return card;
    }

    /** A section card without padding, for lists whose rows bring their own. */
    private LinearLayout listCard(LinearLayout container, String title) {
        LinearLayout card = section(container, title);
        card.setPadding(0, 0, 0, 0);
        card.setClipToOutline(true);
        return card;
    }

    /**
     * The headline switch of a page. It lights up in the feature's colour while on, so a page
     * says at a glance whether the feature is running. The returned switch is an ordinary one:
     * the caller binds persistence to it like any other.
     */
    private ToggleSwitch addMasterToggle(Panel page, int iconRes, String label, String subtitle) {
        final int accent = page.accent;
        final LinearLayout card = newRow();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        final LinearLayout chip = Ui.iconChip(this, iconRes, 46, 24, COLOR_NEUTRAL_CONTAINER, COLOR_TEXT_FAINT);
        card.addView(chip);

        LinearLayout texts = newColumn();
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.leftMargin = dp(14);
        card.addView(texts, textsLp);
        texts.addView(historyText(label, 16, COLOR_TEXT, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = historyText(subtitle, 12.5f, COLOR_TEXT_DIM, false);
            sub.setLineSpacing(0, 1.2f);
            texts.addView(sub, topMarginParams(3));
        }

        final ToggleSwitch sw = makeSwitch();
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.leftMargin = dp(12);
        card.addView(sw, swLp);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sw.toggle();
            }
        });
        ToggleSwitch.Hook hook = new ToggleSwitch.Hook() {
            @Override
            public void onChecked(boolean on) {
                int surface = COLOR_SURFACE;
                int fg = Ui.accentFg(accent);
                int bg = Ui.accentContainer(accent);
                GradientDrawable shape;
                if (on) {
                    shape = Ui.gradient(GradientDrawable.Orientation.TL_BR,
                            Ui.blend(bg, surface, Ui.dark ? 0.55f : 0.65f), surface, dp(SECTION_CORNER));
                    shape.setStroke(dp(1), Ui.blend(fg, surface, Ui.dark ? 0.35f : 0.3f));
                    chip.setBackground(Ui.roundedFill(bg, dp(16), 0, 0));
                    ((ImageView) chip.getChildAt(0)).setColorFilter(fg);
                } else {
                    shape = Ui.gradient(GradientDrawable.Orientation.TL_BR, surface, surface, dp(SECTION_CORNER));
                    shape.setStroke(dp(1), COLOR_BORDER);
                    chip.setBackground(Ui.roundedFill(COLOR_NEUTRAL_CONTAINER, dp(16), 0, 0));
                    ((ImageView) chip.getChildAt(0)).setColorFilter(COLOR_TEXT_FAINT);
                }
                card.setBackground(Ui.ripple(Ui.alpha(fg, 0.12f), shape, dp(SECTION_CORNER)));
            }
        };
        sw.addHook(hook);
        hook.onChecked(false);
        Ui.elevate(card, 1f);
        LinearLayout.LayoutParams lp = matchWrapParams();
        lp.topMargin = dp(16);
        page.content.addView(card, lp);
        return sw;
    }

    /**
     * Shows a page's master switch on its overview tile. The page's switch stays the single
     * source of truth: a tap on the tile's switch just flips that one, so saving, validation,
     * permission prompts and revert-on-failure behave exactly as they do on the page, and the
     * tile follows however the page's switch changes (loading, a remote command, a failed save).
     */
    private void mirrorToTile(final Panel page, final ToggleSwitch pageSwitch) {
        page.tile.enableToggle(new FeatureTile.ToggleListener() {
            @Override
            public void onToggled(boolean on) {
                pageSwitch.setChecked(on);
            }
        });
        pageSwitch.addHook(new ToggleSwitch.Hook() {
            @Override
            public void onChecked(boolean checked) {
                page.tile.setToggleChecked(checked);
            }
        });
        page.tile.setToggleChecked(pageSwitch.isChecked());
    }

    /** A line of small explanatory text under a control. */
    private TextView addNote(LinearLayout parent, String text) {
        TextView note = historyText(text, 12, COLOR_TEXT_FAINT, false);
        note.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams lp = matchWrapParams();
        if (parent.getChildCount() > 0) {
            lp.topMargin = dp(12);
        }
        parent.addView(note, lp);
        return note;
    }

    private void addDivider(LinearLayout parent, int insetStartDp) {
        View line = new View(this);
        line.setBackgroundColor(COLOR_BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.leftMargin = dp(insetStartDp);
        parent.addView(line, lp);
    }

    /** A tappable row of an icon, a title and a hint, which runs {@link #action}. */
    private void addActionRow(LinearLayout card, int iconRes, String title, String hint, String command) {
        if (card.getChildCount() > 0) {
            addDivider(card, 64);
        }
        LinearLayout row = newRow();
        row.setPadding(dp(14), dp(12), dp(10), dp(12));
        row.setMinimumHeight(dp(56));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(Ui.ripple(COLOR_RIPPLE_LIGHT, roundedFill(0, 0, 0, 0), 0));
        row.addView(Ui.iconChip(this, iconRes, 38, 20, COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER));
        LinearLayout texts = newColumn();
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.leftMargin = dp(14);
        row.addView(texts, textsLp);
        texts.addView(historyText(title, 14.5f, COLOR_TEXT, true));
        if (hint != null && !hint.isEmpty()) {
            texts.addView(historyText(hint, 12, COLOR_TEXT_DIM, false), topMarginParams(2));
        }
        row.addView(Ui.icon(this, R.drawable.ic_chevron_right, 20, COLOR_TEXT_FAINT),
                new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.setOnClickListener(action(command));
        card.addView(row, matchWrapParams());
    }

    // ============================================================
    //  Buttons
    // ============================================================

    /** A button on a row of its own, for labels too long to share a line. */
    private void addStackedButton(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(BUTTON_MIN_HEIGHT));
        lp.topMargin = dp(GAP);
        parent.addView(button, lp);
    }

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
        int ripple = enabled ? Ui.alpha(enabledFg, 0.2f) : 0x00000000;
        button.setBackground(Ui.ripple(ripple,
                Ui.roundedFill(bg, dp(BUTTON_CORNER), enabled ? 0 : dp(1), COLOR_FIELD_BORDER), dp(BUTTON_CORNER)));
    }

    private Button primaryButton(String text, View.OnClickListener listener) {
        return primaryButton(text, 0, listener);
    }

    private Button primaryButton(String text, int iconRes, View.OnClickListener listener) {
        return Ui.iconButton(this, text, iconRes, COLOR_PRIMARY, COLOR_ON_ACCENT,
                Ui.alpha(COLOR_ON_ACCENT, 0.2f), listener);
    }

    private Button tonalButton(String text, View.OnClickListener listener) {
        return tonalButton(text, 0, listener);
    }

    private Button tonalButton(String text, int iconRes, View.OnClickListener listener) {
        return Ui.iconButton(this, text, iconRes, COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER,
                COLOR_RIPPLE_LIGHT, listener);
    }

    private Button neutralButton(String text, View.OnClickListener listener) {
        return neutralButton(text, 0, listener);
    }

    private Button neutralButton(String text, int iconRes, View.OnClickListener listener) {
        return Ui.iconButton(this, text, iconRes, COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER,
                COLOR_RIPPLE_LIGHT, listener);
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
        field.setBackground(Ui.fieldBackground(this, false));
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setTextSize(15);
        field.setTextColor(COLOR_TEXT);
        field.setHintTextColor(COLOR_TEXT_FAINT);
        field.setHighlightColor(Ui.alpha(COLOR_PRIMARY, 0.3f));
        field.setMinHeight(dp(FIELD_MIN_HEIGHT));
        field.setMinimumHeight(dp(FIELD_MIN_HEIGHT));
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(field, belowLabel(root));
        return field;
    }

    private void addFieldLabel(LinearLayout root, String label) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12.5f);
        labelView.setTextColor(COLOR_LABEL);
        labelView.setTypeface(Ui.FONT_MEDIUM);
        labelView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp = matchWrapParams();
        if (root.getChildCount() > 0) {
            lp.topMargin = dp(16);
        }
        root.addView(labelView, lp);
    }

    /** Layout for the control that sits directly under its {@link #addFieldLabel}. */
    private LinearLayout.LayoutParams belowLabel(LinearLayout root) {
        LinearLayout.LayoutParams lp = matchWrapParams();
        lp.topMargin = dp(7);
        return lp;
    }

    private Button addPickerButton(LinearLayout root, String label, String text, View.OnClickListener listener) {
        addFieldLabel(root, label);
        Button button = tonalButton(text, listener);
        root.addView(button, belowLabel(root));
        return button;
    }

    private VolumeInput addVolumeInput(LinearLayout root, String label) {
        LinearLayout box = newColumn();
        box.setBackground(roundedFill(COLOR_FIELD_BG, FIELD_CORNER, 1, COLOR_FIELD_BORDER));
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams boxLp = stack(root);
        if (root.getChildCount() > 0) {
            boxLp.topMargin = dp(16);
        }
        root.addView(box, boxLp);

        LinearLayout top = newRow();
        box.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(14.5f);
        title.setTypeface(Ui.FONT_MEDIUM);
        title.setIncludeFontPadding(false);
        top.addView(title, inRow(top, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout unchanged = newRow();
        unchanged.setClickable(true);
        unchanged.setBackground(Ui.ripple(COLOR_RIPPLE_LIGHT, Ui.roundedFill(0, dp(10), 0, 0), dp(10)));
        unchanged.setPadding(dp(8), dp(4), 0, dp(4));
        TextView unchangedLabel = new TextView(this);
        unchangedLabel.setText("Unchanged");
        unchangedLabel.setTextColor(COLOR_TEXT_DIM);
        unchangedLabel.setTextSize(12.5f);
        unchangedLabel.setSingleLine(true);
        unchangedLabel.setIncludeFontPadding(false);
        unchanged.addView(unchangedLabel, inRow(unchanged, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));
        final Switch unchangedSwitch = makeSwitch();
        unchangedSwitch.setChecked(true);
        unchanged.addView(unchangedSwitch, inRow(unchanged, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));
        top.addView(unchanged, inRow(top, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

        LinearLayout control = newRow();
        box.addView(control, topMarginParams(6));

        final SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(50);
        Ui.styleSeekBar(slider);
        control.addView(slider, inRow(control, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView valueView = new TextView(this);
        valueView.setTextColor(COLOR_TEXT_DIM);
        valueView.setTextSize(13);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setGravity(Gravity.END);
        valueView.setIncludeFontPadding(false);
        valueView.setMinWidth(dp(48));
        control.addView(valueView, inRow(control, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f));

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
        root.addView(row, belowLabel(root));
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
        showVolumeRuleTimePicker(volumeRuleHour >= 0 ? volumeRuleHour : 17,
                volumeRuleMinute >= 0 ? volumeRuleMinute : 0);
    }

    private void showVolumeRuleTimePicker(int initialHour, int initialMinute) {
        final TimePickerDialog dialog = new TimePickerDialog(
                this,
                Ui.dark ? R.style.SysmgrDialog_Dark : R.style.SysmgrDialog_Light,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(android.widget.TimePicker view, int hourOfDay, int minute) {
                        setVolumeRuleTime(hourOfDay, minute);
                    }
                },
                initialHour,
                initialMinute,
                true);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface ignored) {
                if (volumeRuleTimeDialog == dialog) {
                    volumeRuleTimeDialog = null;
                }
            }
        });
        volumeRuleTimeDialog = dialog;
        dialog.show();
    }

    /**
     * The platform time picker lays itself out for the screen it was created on, and rotation does
     * not recreate the activity, so a picker left open across a turn keeps the old shape and runs
     * off the new screen. Open a fresh one in its place, on the time that was showing.
     */
    private void refitTimePicker() {
        TimePickerDialog open = volumeRuleTimeDialog;
        if (open == null || !open.isShowing()) {
            return;
        }
        Bundle state = open.onSaveInstanceState();
        int hour = state.getInt("hour", volumeRuleHour >= 0 ? volumeRuleHour : 17);
        int minute = state.getInt("minute", volumeRuleMinute >= 0 ? volumeRuleMinute : 0);
        open.dismiss();
        showVolumeRuleTimePicker(hour, minute);
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
        input.valueView.setText(unchanged ? "—" : input.slider.getProgress() + "%");
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
        int bg = selected ? COLOR_PRIMARY : COLOR_FIELD_BG;
        int fg = selected ? COLOR_ON_ACCENT : COLOR_TEXT_DIM;
        button.setTextColor(fg);
        button.setBackground(Ui.ripple(Ui.alpha(fg, 0.2f),
                Ui.roundedFill(bg, dp(BUTTON_CORNER), selected ? 0 : dp(1), COLOR_FIELD_BORDER), dp(BUTTON_CORNER)));
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
        parent.addView(group, stack(parent));
        return group;
    }

    private Switch addGroupedToggle(LinearLayout group, String text) {
        return addGroupedToggle(group, text, null);
    }

    /** A labelled switch row; rows in one group are separated by hairlines. */
    private Switch addGroupedToggle(LinearLayout group, String text, String description) {
        if (group.getChildCount() > 0) {
            addDivider(group, 0);
        }

        LinearLayout row = newRow();
        row.setMinimumHeight(dp(TOGGLE_ROW_MIN_HEIGHT));
        row.setPadding(0, dp(8), 0, dp(8));

        LinearLayout texts = newColumn();
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(14.5f);
        label.setIncludeFontPadding(false);
        label.setLineSpacing(0, 1.1f);
        texts.addView(label);
        if (description != null && !description.isEmpty()) {
            TextView hint = historyText(description, 12, COLOR_TEXT_DIM, false);
            hint.setLineSpacing(0, 1.2f);
            texts.addView(hint, topMarginParams(3));
        }

        final Switch sw = makeSwitch();
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.setMargins(dp(14), 0, 0, 0);
        row.addView(sw, swLp);

        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(Ui.ripple(COLOR_RIPPLE_LIGHT, Ui.roundedFill(0, dp(12), 0, 0), dp(12)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sw.toggle();
            }
        });

        group.addView(row, matchWrapParams());
        return sw;
    }

    private ToggleSwitch makeSwitch() {
        return new ToggleSwitch(this);
    }

    /** A readiness row: an icon, the requirement and its state; a missing one can be tapped to fix. */
    private void addStatusRow(LinearLayout root, String label, boolean ok) {
        addStatusRow(root, label, ok, null);
    }

    private void addStatusRow(LinearLayout root, String label, boolean ok, String fixCommand) {
        if (root.getChildCount() > 0) {
            addDivider(root, 34);
        }
        LinearLayout row = newRow();
        row.setMinimumHeight(dp(STATUS_ROW_MIN_HEIGHT + 6));

        row.addView(Ui.icon(this, ok ? R.drawable.ic_check_circle : R.drawable.ic_warning, 20,
                ok ? COLOR_OK : COLOR_BAD), new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(COLOR_TEXT);
        labelView.setTextSize(14);
        labelView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.leftMargin = dp(14);
        row.addView(labelView, labelLp);

        TextView valueView = new TextView(this);
        valueView.setText(ok ? "READY" : "NEEDED");
        valueView.setTextColor(ok ? COLOR_OK : COLOR_BAD);
        valueView.setTextSize(10.5f);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setLetterSpacing(0.1f);
        valueView.setIncludeFontPadding(false);
        row.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!ok && fixCommand != null) {
            row.addView(Ui.icon(this, R.drawable.ic_chevron_right, 18, COLOR_BAD),
                    new LinearLayout.LayoutParams(dp(18), dp(18)));
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(Ui.ripple(Ui.alpha(COLOR_BAD, 0.12f), Ui.roundedFill(0, dp(10), 0, 0), dp(10)));
            row.setOnClickListener(action(fixCommand));
        }
        root.addView(row, matchWrapParams());
    }

    /** Label/value row for read-only status cards. */
    private TextView addInfoRow(LinearLayout root, String label, String value, int valueColor) {
        if (root.getChildCount() > 0) {
            addDivider(root, 0);
        }
        LinearLayout row = newRow();
        row.setMinimumHeight(dp(STATUS_ROW_MIN_HEIGHT + 4));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(COLOR_LABEL);
        labelView.setTextSize(13.5f);
        labelView.setIncludeFontPadding(false);
        row.addView(labelView, inRow(row, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(valueColor);
        valueView.setTextSize(13.5f);
        valueView.setTypeface(Ui.FONT_MEDIUM);
        valueView.setGravity(Gravity.END);
        valueView.setIncludeFontPadding(false);
        valueView.setLineSpacing(0, 1.15f);
        row.addView(valueView, inRow(row, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));

        root.addView(row, matchWrapParams());
        return valueView;
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
        refreshVncPanel();
        refreshBeaconPanel();
        setEnabledPill(rebootPill, switchValue(rebootAutomationEnabledSwitch, config.rebootAutomationEnabled()));
        setRemoteLinkPill(RemoteLinkStateStore.isConnected(this));
        refreshRemoteLinkStatistics();
        refreshRemoteLinkTestStatus();
        refreshNotificationBackupStatus();
        setEnabledPill(logPill, switchValue(logEnabledSwitch, config.logEnabled()));
        refreshSystemBackupStatus();
        refreshUpgradeStatus();
        dashboardTileToggle("gps", tracking);
        applyButtonState(startTrackingButton, !tracking, COLOR_PRIMARY, COLOR_ON_ACCENT);
        applyButtonState(stopTrackingButton, tracking, COLOR_DANGER, COLOR_ON_DANGER);

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
        permissionsNeedingAttention = 0;
        statusRow("Fine location", fineLocation, "common_permissions");
        statusRow("Background location", backgroundLocation, "background_location");
        statusRow("Device location services", locationServices, "location_settings");
        statusRow("Nearby Wi-Fi", nearbyWifi, "common_permissions");
        statusRow(exactAlarmRequested ? "Exact alarms" : "Exact alarms not required", exactAlarms, "exact_alarm");
        statusRow("Battery unrestricted", batteryUnrestricted, "battery");
        statusRow("Notifications", notifications, "notification_settings");
        statusRow("Notification access", notificationAccess, "notification_access");
        statusRow("Accessibility monitor", accessibility, "accessibility_settings");
        if (dndRuleConfigured) {
            statusRow("DND access", dndAccess, "dnd_access");
        }
        if (vpnProfileConfigured) {
            statusRow("VPN consent", vpnConsent, null);
        }
        if (beaconEnabled) {
            statusRow("Bluetooth advertise", bluetoothAdvertise, "bluetooth_permission");
            statusRow("Bluetooth on", bluetoothOn, "bluetooth_settings");
        }
        if (hiddenWifiMonitorNeedsAccessibility) {
            statusRow("Hidden Wi-Fi monitor", false, "accessibility_settings");
        }
        setTileDetail("permissions", permissionsNeedingAttention == 0
                ? "Everything granted" : permissionsNeedingAttention + " to fix");
        refreshDashboard();

        // Wi-Fi identity and the log file are read independently off the main
        // thread and applied when ready. The Wi-Fi lookup can block ~1.2s while
        // identity is resolving (e.g. right after a network change).
        scheduleWifiRefresh();
        scheduleLogRefresh();
    }

    private void statusRow(String label, boolean ok, String fixCommand) {
        if (!ok) {
            permissionsNeedingAttention++;
        }
        addStatusRow(statusContainer, label, ok, fixCommand);
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

    /** Reads Wi-Fi off the main thread and coalesces bursts of network events. */
    private void scheduleWifiRefresh() {
        if (wifiIoInFlight) {
            wifiIoRequested = true;
            return;
        }
        wifiIoInFlight = true;
        wifiIoRequested = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final WifiSnapshot wifi = WifiInfoReader.read(MainActivity.this);
                cachedWifi = wifi;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wifiIoInFlight = false;
                        if (wifiSummary != null) {
                            renderWifi(Config.get(MainActivity.this), wifi);
                        }
                        refreshDashboard();
                        if (wifiIoRequested) {
                            wifiIoRequested = false;
                            scheduleWifiRefresh();
                        }
                    }
                });
            }
        }, "SystemManagerWifiStatus").start();
    }

    /** Reads the newest log lines off the main thread and coalesces write bursts. */
    private void scheduleLogRefresh() {
        if (logIoInFlight) {
            logIoRequested = true;
            return;
        }
        logIoInFlight = true;
        logIoRequested = false;
        final int maxLines = Math.min(Config.get(this).logMaxLines(), 300);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final CharSequence log = colorizeLog(LogStore.readTail(MainActivity.this, maxLines));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        logIoInFlight = false;
                        if (logView != null) {
                            logView.setText(log);
                        }
                        if (logIoRequested) {
                            logIoRequested = false;
                            scheduleLogRefresh();
                        }
                    }
                });
            }
        }, "SystemManagerLogStatus").start();
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
        if (!Config.get(this).remoteLinkEnabled()) {
            setPillState(remoteLinkPill, "DISABLED", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
            return;
        }
        setPillState(
                remoteLinkPill,
                connected ? "CONNECTED" : "DISCONNECTED",
                connected ? COLOR_PRIMARY_CONTAINER : COLOR_DANGER_CONTAINER,
                connected ? COLOR_PRIMARY_ON_CONTAINER : COLOR_DANGER_ON_CONTAINER);
    }

    private void refreshRemoteLinkStatistics() {
        if (remoteLinkUptimeValue == null || remoteLinkDowntimeValue == null) {
            return;
        }
        RemoteLinkAvailabilityStore.Snapshot statistics =
                RemoteLinkAvailabilityStore.snapshot(this);
        remoteLinkUptimeValue.setText(String.format(
                Locale.US, "%.3f%%", statistics.upPercent));
        remoteLinkDowntimeValue.setText(formatHms(statistics.downMillis));
        remoteLinkUptimeValue.setTextColor(COLOR_TEXT);
        remoteLinkDowntimeValue.setTextColor(COLOR_TEXT);
    }

    private static String formatHms(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void refreshRemoteLinkTestStatus() {
        if (remoteLinkLatencyValue == null
                || remoteLinkUploadThroughputValue == null
                || remoteLinkDownloadThroughputValue == null) {
            return;
        }
        boolean latencyTesting = RemoteLinkTestStateStore.isLatencyTesting(this);
        boolean throughputTesting = RemoteLinkTestStateStore.isThroughputTesting(this);
        long latencyMicros = RemoteLinkTestStateStore.latencyMicros(this);
        String throughputPhase = RemoteLinkTestStateStore.throughputPhase(this);
        long uploadBps = RemoteLinkTestStateStore.uploadBitsPerSecond(this);
        long downloadBps = RemoteLinkTestStateStore.downloadBitsPerSecond(this);
        long uploadBytes = RemoteLinkTestStateStore.uploadBytes(this);
        long downloadBytes = RemoteLinkTestStateStore.downloadBytes(this);

        if (latencyTesting) {
            remoteLinkLatencyValue.setText(latencyMicros >= 0L
                    ? String.format(Locale.US, "%.1f ms (testing…)", latencyMicros / 1000.0)
                    : "Waiting for response…");
            remoteLinkLatencyValue.setTextColor(COLOR_PRIMARY_ON_CONTAINER);
        } else if (latencyMicros >= 0L) {
            remoteLinkLatencyValue.setText(String.format(
                    Locale.US, "%.1f ms", latencyMicros / 1000.0));
            remoteLinkLatencyValue.setTextColor(COLOR_TEXT);
        } else {
            remoteLinkLatencyValue.setText("Unknown");
            remoteLinkLatencyValue.setTextColor(COLOR_TEXT_DIM);
        }

        if (throughputTesting
                && RemoteLinkTestStateStore.THROUGHPUT_PHASE_UPLOAD.equals(throughputPhase)) {
            remoteLinkUploadThroughputValue.setText(String.format(
                    Locale.US, "Sent %.1f MB…", uploadBytes / (1024.0 * 1024.0)));
            remoteLinkUploadThroughputValue.setTextColor(COLOR_PRIMARY_ON_CONTAINER);
        } else {
            setThroughputValue(remoteLinkUploadThroughputValue, uploadBps);
        }

        if (throughputTesting
                && RemoteLinkTestStateStore.THROUGHPUT_PHASE_DOWNLOAD.equals(throughputPhase)) {
            remoteLinkDownloadThroughputValue.setText(String.format(
                    Locale.US, "Received %.1f MB…", downloadBytes / (1024.0 * 1024.0)));
            remoteLinkDownloadThroughputValue.setTextColor(COLOR_PRIMARY_ON_CONTAINER);
        } else if (throughputTesting) {
            remoteLinkDownloadThroughputValue.setText("Waiting…");
            remoteLinkDownloadThroughputValue.setTextColor(COLOR_TEXT_DIM);
        } else {
            setThroughputValue(remoteLinkDownloadThroughputValue, downloadBps);
        }

        boolean connected = Config.get(this).remoteLinkEnabled()
                && RemoteLinkStateStore.isConnected(this);
        remoteLinkLatencyButton.setText(latencyTesting ? "Stop" : "Test Latency");
        remoteLinkThroughputButton.setText(throughputTesting ? "Stop" : "Test Throughput");
        if (latencyTesting) {
            applyButtonState(remoteLinkLatencyButton, true, COLOR_DANGER, COLOR_ON_DANGER);
            applyButtonState(remoteLinkThroughputButton, false,
                    COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
        } else if (throughputTesting) {
            applyButtonState(remoteLinkLatencyButton, false,
                    COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
            applyButtonState(remoteLinkThroughputButton, true, COLOR_DANGER, COLOR_ON_DANGER);
        } else {
            applyButtonState(remoteLinkLatencyButton, connected,
                    COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
            applyButtonState(remoteLinkThroughputButton, connected,
                    COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
        }
    }

    private void setThroughputValue(TextView target, long bitsPerSecond) {
        if (bitsPerSecond >= 1_000_000L) {
            target.setText(String.format(
                    Locale.US, "%.1f Mbps", bitsPerSecond / 1_000_000.0));
            target.setTextColor(COLOR_TEXT);
        } else if (bitsPerSecond >= 1000L) {
            target.setText(String.format(
                    Locale.US, "%.1f Kbps", bitsPerSecond / 1000.0));
            target.setTextColor(COLOR_TEXT);
        } else if (bitsPerSecond >= 0L) {
            target.setText(bitsPerSecond + " bps");
            target.setTextColor(COLOR_TEXT);
        } else {
            target.setText("Unknown");
            target.setTextColor(COLOR_TEXT_DIM);
        }
    }

    private void registerRemoteLinkStateReceiver() {
        if (remoteLinkStateReceiver != null) {
            return;
        }
        remoteLinkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setRemoteLinkPill(RemoteLinkStateStore.isConnected(MainActivity.this));
                refreshRemoteLinkStatistics();
                refreshRemoteLinkTestStatus();
                refreshNotificationBackupStatus();
                refreshSystemBackupStatus();
                refreshUpgradeStatus();
                maybePromptAutoUpgrade();
            }
        };
        IntentFilter filter = new IntentFilter(RemoteLinkStateStore.ACTION_STATE_CHANGED);
        filter.addAction(RemoteLinkTestStateStore.ACTION_STATE_CHANGED);
        filter.addAction(SystemBackupStateStore.ACTION_STATE_CHANGED);
        filter.addAction(UpgradeStateStore.ACTION_STATE_CHANGED);
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

    private void registerLogChangedReceiver() {
        if (logChangedReceiver != null) {
            return;
        }
        logChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                scheduleLogRefresh();
            }
        };
        registerInternalReceiver(logChangedReceiver, new IntentFilter(LogStore.ACTION_CHANGED));
    }

    private void unregisterLogChangedReceiver() {
        if (logChangedReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(logChangedReceiver);
        } catch (RuntimeException ignored) {
        }
        logChangedReceiver = null;
    }

    private void registerNetworkStateReceiver() {
        if (networkStateReceiver != null) {
            return;
        }
        networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                scheduleWifiRefresh();
            }
        };
        registerInternalReceiver(networkStateReceiver,
                new IntentFilter(NetworkStateStore.ACTION_STATE_CHANGED));
    }

    private void unregisterNetworkStateReceiver() {
        if (networkStateReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(networkStateReceiver);
        } catch (RuntimeException ignored) {
        }
        networkStateReceiver = null;
    }

    private void registerSystemStateReceiver() {
        if (systemStateReceiver != null) {
            return;
        }
        systemStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? "" : intent.getAction();
                if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)
                        || WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)
                        || WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                    scheduleWifiRefresh();
                    return;
                }
                if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                    refreshBeaconPanel();
                    refreshDashboard();
                    return;
                }
                // Permission and radio changes are uncommon, so a complete
                // synchronous indicator refresh is clearer and still cheap.
                refreshStatusAndLog();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        filter.addAction(LocationManager.MODE_CHANGED_ACTION);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            filter.addAction(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);
        }
        registerFrameworkReceiver(systemStateReceiver, filter);
    }

    private void unregisterSystemStateReceiver() {
        if (systemStateReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(systemStateReceiver);
        } catch (RuntimeException ignored) {
        }
        systemStateReceiver = null;
    }

    private void registerInternalReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void registerFrameworkReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Some framework broadcasts (Bluetooth in particular) originate
            // from privileged apps rather than the system UID and require an
            // exported dynamic receiver on Android 13+.
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    private void setEnabledPill(TextView target, boolean enabled) {
        setPillState(
                target,
                enabled ? "ENABLED" : "DISABLED",
                enabled ? COLOR_PRIMARY_CONTAINER : COLOR_NEUTRAL_CONTAINER,
                enabled ? COLOR_PRIMARY_ON_CONTAINER : COLOR_NEUTRAL_ON_CONTAINER);
    }

    private void refreshSystemBackupStatus() {
        Config config = Config.get(this);
        boolean connected = config.remoteLinkEnabled() && RemoteLinkStateStore.isConnected(this);
        boolean checked = SystemBackupStateStore.isChecked(this);
        boolean available = SystemBackupStateStore.isServerAvailable(this);
        boolean exists = SystemBackupStateStore.backupExists(this);
        String label;
        int background;
        int foreground;
        if (backupOperationInFlight) {
            label = backupRestoreInFlight ? "RESTORING" : "BACKING UP";
            background = COLOR_PRIMARY_CONTAINER;
            foreground = COLOR_PRIMARY_ON_CONTAINER;
        } else if (!config.remoteLinkEnabled()) {
            label = "LINK OFF";
            background = COLOR_NEUTRAL_CONTAINER;
            foreground = COLOR_NEUTRAL_ON_CONTAINER;
        } else if (!connected) {
            label = "LINK OFFLINE";
            background = COLOR_DANGER_CONTAINER;
            foreground = COLOR_DANGER_ON_CONTAINER;
        } else if (!checked) {
            label = "CHECKING";
            background = COLOR_NEUTRAL_CONTAINER;
            foreground = COLOR_NEUTRAL_ON_CONTAINER;
        } else if (!available) {
            label = "UNAVAILABLE";
            background = COLOR_DANGER_CONTAINER;
            foreground = COLOR_DANGER_ON_CONTAINER;
        } else if (!exists) {
            label = "NEVER";
            background = COLOR_NEUTRAL_CONTAINER;
            foreground = COLOR_NEUTRAL_ON_CONTAINER;
        } else {
            long timestamp = SystemBackupStateStore.backupModifiedAtMillis(this);
            if (timestamp <= 0L) {
                timestamp = config.lastBackupMillis();
            }
            label = timestamp > 0L ? formatBackupDate(timestamp) : "AVAILABLE";
            background = COLOR_PRIMARY_CONTAINER;
            foreground = COLOR_PRIMARY_ON_CONTAINER;
        }
        setPillState(systemBackupPill, label, background, foreground);
        setTileDetail("backup", "Settings, history, VPN");
        boolean canBackUp = connected && checked && available && !backupOperationInFlight;
        applyButtonState(backUpButton, canBackUp, COLOR_PRIMARY, COLOR_ON_ACCENT);
        applyButtonState(restoreBackupButton, canBackUp && exists,
                COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
    }

    private void refreshUpgradeStatus() {
        if (upgradePanel == null) {
            return;
        }
        boolean configured = UpgradeStateStore.isConfigured(this);
        if (!configured) {
            dashboard.setTileVisible("upgrade", false);
            dashboard.setUpdate(null);
            // The page can still be on screen (or restored); say so rather than show stale state.
            setPillState(upgradePill, "UNAVAILABLE", COLOR_NEUTRAL_CONTAINER, COLOR_NEUTRAL_ON_CONTAINER);
            applyButtonState(upgradeRefreshButton, false, COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
            applyButtonState(upgradeInstallButton, false, COLOR_PRIMARY, COLOR_ON_ACCENT);
            return;
        }
        dashboard.setTileVisible("upgrade", true);

        boolean connected = Config.get(this).remoteLinkEnabled()
                && RemoteLinkStateStore.isConnected(this);
        boolean checked = UpgradeStateStore.isChecked(this);
        boolean exists = UpgradeStateStore.apkExists(this);
        String label;
        int background;
        int foreground;
        if (upgradeDownloadInFlight) {
            label = "DOWNLOADING";
            background = COLOR_PRIMARY_CONTAINER;
            foreground = COLOR_PRIMARY_ON_CONTAINER;
        } else if (!Config.get(this).remoteLinkEnabled()) {
            label = "LINK OFF";
            background = COLOR_NEUTRAL_CONTAINER;
            foreground = COLOR_NEUTRAL_ON_CONTAINER;
        } else if (!connected) {
            label = "LINK OFFLINE";
            background = COLOR_DANGER_CONTAINER;
            foreground = COLOR_DANGER_ON_CONTAINER;
        } else if (!checked) {
            label = "CHECKING";
            background = COLOR_NEUTRAL_CONTAINER;
            foreground = COLOR_NEUTRAL_ON_CONTAINER;
        } else if (!exists) {
            label = "MISSING";
            background = COLOR_DANGER_CONTAINER;
            foreground = COLOR_DANGER_ON_CONTAINER;
        } else {
            label = "AVAILABLE";
            background = COLOR_PRIMARY_CONTAINER;
            foreground = COLOR_PRIMARY_ON_CONTAINER;
        }
        setPillState(upgradePill, label, background, foreground);
        String offered = UpgradeStateStore.apkVersionName(this);
        boolean newer = exists && UpgradeStateStore.isApkVersionReady(this) && !offered.isEmpty()
                && UpgradeStateStore.apkVersionCode(this) > AppVersion.code(this);
        dashboard.setUpdate(newer ? offered : null);
        setTileDetail("upgrade", newer ? "Version " + offered : "Version " + AppVersion.name(this));

        upgradeCurrentVersionValue.setText(AppVersion.name(this));
        upgradeCurrentVersionValue.setTextColor(COLOR_TEXT);
        String availableVersion = UpgradeStateStore.apkVersionName(this);
        boolean cachedVersion = exists
                && UpgradeStateStore.isApkVersionReady(this)
                && !availableVersion.isEmpty();
        if (cachedVersion) {
            upgradeAvailableVersionValue.setText(availableVersion);
            upgradeAvailableVersionValue.setTextColor(COLOR_TEXT);
        } else if (upgradeDownloadInFlight || !checked) {
            upgradeAvailableVersionValue.setText("Checking…");
            upgradeAvailableVersionValue.setTextColor(COLOR_TEXT_DIM);
        } else if (exists) {
            upgradeAvailableVersionValue.setText("Press Refresh");
            upgradeAvailableVersionValue.setTextColor(COLOR_TEXT_DIM);
        } else {
            upgradeAvailableVersionValue.setText("Unknown");
            upgradeAvailableVersionValue.setTextColor(COLOR_TEXT_DIM);
        }

        if (exists) {
            long modifiedAt = UpgradeStateStore.apkModifiedAtMillis(this);
            long sizeBytes = UpgradeStateStore.apkSizeBytes(this);
            upgradeDateValue.setText(modifiedAt > 0L
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(modifiedAt))
                    : "Unknown");
            upgradeSizeValue.setText(OpenVpnService.formatBytes(sizeBytes));
            upgradeDateValue.setTextColor(modifiedAt > 0L ? COLOR_TEXT : COLOR_TEXT_DIM);
            upgradeSizeValue.setTextColor(COLOR_TEXT);
        } else if (!checked) {
            upgradeDateValue.setText("Checking…");
            upgradeSizeValue.setText("Checking…");
            upgradeDateValue.setTextColor(COLOR_TEXT_DIM);
            upgradeSizeValue.setTextColor(COLOR_TEXT_DIM);
        } else {
            upgradeDateValue.setText("Unknown");
            upgradeSizeValue.setText("Unknown");
            upgradeDateValue.setTextColor(COLOR_TEXT_DIM);
            upgradeSizeValue.setTextColor(COLOR_TEXT_DIM);
        }

        boolean idleAndConnected = connected && !upgradeDownloadInFlight;
        applyButtonState(upgradeRefreshButton, idleAndConnected,
                COLOR_PRIMARY_CONTAINER, COLOR_PRIMARY_ON_CONTAINER);
        applyButtonState(upgradeInstallButton, idleAndConnected && checked && exists,
                COLOR_PRIMARY, COLOR_ON_ACCENT);
    }

    private void maybePromptAutoUpgrade() {
        if (!autoUpgradeCheckPending || !Config.get(this).autoUpgradeEnabled()) {
            return;
        }
        boolean connected = Config.get(this).remoteLinkEnabled()
                && RemoteLinkStateStore.isConnected(this);
        if (!connected || !UpgradeStateStore.isChecked(this)) {
            return;
        }
        if (!UpgradeStateStore.isConfigured(this)) {
            autoUpgradeCheckPending = false;
            return;
        }
        if (!UpgradeStateStore.apkExists(this)) {
            return;
        }
        if (!UpgradeStateStore.isApkVersionReady(this)) {
            // An inotify event invalidates the daemon's cached version before
            // it reparses the replacement. Wait for the follow-up status.
            return;
        }
        final long availableCode = UpgradeStateStore.apkVersionCode(this);
        long currentCode = AppVersion.code(this);
        if (availableCode <= 0L || currentCode <= 0L) {
            return;
        }
        if (availableCode <= currentCode) {
            autoUpgradeCheckPending = false;
            LogStore.append(this, "upgrade", "Automatic upgrade check is current"
                    + " installed_code=" + currentCode
                    + " available_code=" + availableCode);
            return;
        }

        final String availableVersion = UpgradeStateStore.apkVersionName(this);
        autoUpgradeCheckPending = false;
        LogStore.append(this, "upgrade", "Automatic upgrade available version="
                + availableVersion + " version_code=" + availableCode);
        OpenVpnConfirmDialog.show(this,
                "Upgrade to version " + availableVersion + "?",
                "Version " + availableVersion + " is available. The installed version is "
                        + AppVersion.name(this) + ". Would you like to upgrade now?",
                "Upgrade", false, new OpenVpnConfirmDialog.Listener() {
                    @Override
                    public void onConfirm() {
                        if (!UpgradeStateStore.isApkVersionReady(MainActivity.this)
                                || UpgradeStateStore.apkVersionCode(MainActivity.this)
                                != availableCode
                                || !availableVersion.equals(
                                        UpgradeStateStore.apkVersionName(
                                                MainActivity.this))) {
                            autoUpgradeCheckPending = Config.get(MainActivity.this)
                                    .autoUpgradeEnabled();
                            RemoteLinkManager.probeUpgrade(MainActivity.this,
                                    "auto-upgrade-version-changed");
                            Toast.makeText(MainActivity.this,
                                    "The available APK changed; checking again",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        startUpgradeDownload(availableVersion, availableCode);
                    }
                });
    }

    private void setPillState(TextView target, String text, int bg, int fg) {
        if (target == null) {
            return;
        }
        int tone = Ui.toneOfContainer(bg);
        if (target instanceof StatusPill) {
            ((StatusPill) target).set(text, tone);
        } else {
            target.setText(text);
            target.setTextColor(fg);
            target.setBackground(Ui.roundedFill(bg, dp(Ui.PILL_CORNER), 0, 0));
        }
        Panel owner = pillOwners.get(target);
        if (owner != null) {
            owner.tile.setStatus(tileStatus(text), tone);
            dashboard.scheduleHeroRefresh();
        }
    }

    /** Pills shout in capitals; a tile speaks in sentences. */
    private static String tileStatus(String pillText) {
        return Ui.sentence(pillText).replace("All ok", "All OK");
    }

    private void setTileDetail(String id, String detail) {
        FeatureTile tile = dashboard == null ? null : dashboard.tile(id);
        if (tile != null) {
            tile.setDetail(detail);
        }
    }

    private boolean switchValue(Switch sw, boolean fallback) {
        return sw == null ? fallback : sw.isChecked();
    }

    // ============================================================
    //  Overview
    // ============================================================

    /**
     * Pushes the vital signs and the one-line summaries into the overview. The state words on
     * the tiles arrive separately, from whichever refresh sets each page's pill.
     */
    private void refreshDashboard() {
        if (dashboard == null) {
            return;
        }
        Config config = Config.get(this);

        dashboard.setBattery(BatteryReader.batteryPercent(this), BatteryReader.isPluggedIn(this));
        refreshNetworkVital();
        boolean linkEnabled = config.remoteLinkEnabled();
        dashboard.setLink(linkEnabled, linkEnabled && RemoteLinkStateStore.isConnected(this),
                RemoteLinkAvailabilityStore.snapshot(this).upPercent);

        setTileDetail("remote_link", linkEnabled ? endpointHost(config.remoteLinkEndpoint()) : "Not enabled");

        int battery = BatteryReader.batteryPercent(this);
        setTileDetail("gps", config.isTrackingEnabled()
                ? "Every " + config.intervalMinutesForBattery(battery < 0 ? 100 : battery) + " min"
                : "Not tracking");

        List<String> alertSources = new ArrayList<>();
        if (config.highPriorityEnabled()) {
            String app = appLabel(config.highPriorityPackage());
            alertSources.add(app.isEmpty() ? "App alerts" : app);
        }
        if (config.highPriorityRemoteEnabled()) {
            alertSources.add("Remote Link");
        }
        setTileDetail("alerts", alertSources.isEmpty() ? "No sources on" : TextUtils.join(" + ", alertSources));

        setTileDetail("battery", "Alert below " + config.batteryAlertThresholdPercent() + "%");

        List<String> triggers = new ArrayList<>();
        if (config.rebootScheduleEnabled()) {
            triggers.add("Daily " + formatRuleTime(config.rebootScheduleHour(), config.rebootScheduleMinute()));
        }
        if (config.rebootNotificationTriggerEnabled()) {
            triggers.add("Notification");
        }
        if (config.rebootRemoteTriggerEnabled()) {
            triggers.add("Remote");
        }
        setTileDetail("reboot", triggers.isEmpty() ? "No triggers set" : TextUtils.join(" + ", triggers));

        setTileDetail("log", "Keeps " + config.logMaxLines() + " lines");
    }

    /**
     * Describes the network the phone is really on. The Wi-Fi snapshot only counts as
     * connected when the SSID can be read, which needs the location permission, and a VPN
     * hides the transport underneath it, so this looks at every network that has internet
     * and only borrows the snapshot for the name.
     */
    @SuppressWarnings("deprecation")
    private void refreshNetworkVital() {
        boolean known = false;
        boolean wifiUp = false;
        boolean cellularUp = false;
        boolean otherUp = false;
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager != null) {
                known = true;
                for (android.net.Network network : manager.getAllNetworks()) {
                    android.net.NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                    if (caps == null || !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        continue;
                    }
                    if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                        wifiUp = true;
                    } else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        cellularUp = true;
                    } else if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                        otherUp = true;
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        WifiSnapshot wifi = cachedWifi;
        if (!known) {
            wifiUp = wifi != null && wifi.connected;
        }
        if (wifiUp) {
            String ssid = wifi == null || wifi.ssidRedacted ? "" : wifi.ssid;
            dashboard.setNetwork(R.drawable.ic_wifi, ssid.isEmpty() ? "Connected" : ssid, "Wi-Fi");
        } else if (cellularUp) {
            dashboard.setNetwork(R.drawable.ic_radio, "Cellular", "Mobile data");
        } else if (otherUp) {
            dashboard.setNetwork(R.drawable.ic_globe, "Online", "Network");
        } else {
            dashboard.setNetwork(R.drawable.ic_wifi_off, "Offline", "No network");
        }
    }

    private static String endpointHost(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (value.isEmpty()) {
            return "No endpoint set";
        }
        try {
            String host = URI.create(value).getHost();
            if (host != null && !host.isEmpty()) {
                return host;
            }
        } catch (RuntimeException ignored) {
        }
        return value;
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
            notificationActionButtonsEnabledSwitch.setChecked(
                    config.notificationActionButtonsEnabled());
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
            upgradeAutoSwitch.setChecked(config.autoUpgradeEnabled());
            vpnUsernameField.setText(config.vpnUsername());
            vpnPasswordField.setText(config.vpnPassword());
            vpnKeyPassphraseField.setText(config.vpnKeyPassphrase());
            vpnTapStaticIpField.setText(config.vpnTapStaticIp());
            vpnTapNetmaskField.setText(config.vpnTapNetmask());
            vpnTapGatewayField.setText(config.vpnTapGateway());
            vpnRemoteCommandEnabledSwitch.setChecked(config.vpnRemoteCommandEnabled());
            vncEnabledSwitch.setChecked(config.vncEnabled());
            vncRemoteCommandEnabledSwitch.setChecked(config.vncRemoteCommandEnabled());
            vncEngine = config.vncEngine();
            updateVncEngineButtons();
            vncPasswordField.setText(VncSecretStore.password(this));
            vncPortField.setText(Integer.toString(config.vncPort()));
            vncAllowedClientsField.setText(config.vncAllowedClients());
            vncViewOnlySwitch.setChecked(config.vncViewOnly());
            vncEnabledOnMatchingWifiSwitch.setChecked(config.vncEnabledOnMatchingWifi());
            vncMatchingWifiSsidField.setText(config.vncMatchingWifiSsid());
            vncEnabledWhenVpnConnectedSwitch.setChecked(config.vncEnabledWhenVpnConnected());
            vncEnabledOnCellularOnlySwitch.setChecked(config.vncEnabledOnCellularOnly());
            vncScalePercent = config.vncScalePercent();
            updateVncScaleButtons();
            vncMaxFpsField.setText(Integer.toString(config.vncMaxFps()));
            vncIdleTimeoutField.setText(Integer.toString(config.vncIdleTimeoutMinutes()));
            vncWakeOnConnectSwitch.setChecked(config.vncWakeOnConnect());
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

        final LiveSaveGroup upgradeSettings = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                boolean enabled = upgradeAutoSwitch.isChecked();
                Config.get(MainActivity.this).setAutoUpgradeEnabled(enabled);
                autoUpgradeCheckPending = enabled;
                if (enabled) {
                    UpgradeStateStore.setRefreshing(MainActivity.this);
                    RemoteLinkManager.probeUpgrade(MainActivity.this,
                            "auto-upgrade-enabled");
                }
                refreshUpgradeStatus();
                return true;
            }
        });
        bindLiveToggles(upgradeSettings, upgradeAutoSwitch);

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

        vncSettingsSave = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveVncConfigOnly();
            }
        });
        bindLiveEdits(vncSettingsSave,
                vncPasswordField, vncPortField, vncAllowedClientsField,
                vncMatchingWifiSsidField, vncMaxFpsField, vncIdleTimeoutField);
        // Only the master switch gets the disable fallback: the others must just
        // revert when a sibling field is invalid, not take the server down.
        bindLiveToggles(vncSettingsSave, new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveVncDisabledOnly();
            }
        }, vncEnabledSwitch);
        bindLiveToggles(vncSettingsSave, vncViewOnlySwitch, vncEnabledOnMatchingWifiSwitch,
                vncEnabledWhenVpnConnectedSwitch, vncEnabledOnCellularOnlySwitch,
                vncWakeOnConnectSwitch);

        final LiveSaveGroup vncRemoteControl = liveSaveGroup(new LiveSaveAction() {
            @Override
            public boolean save() {
                return saveVncRemoteCommandToggleOnly();
            }
        });
        bindLiveToggles(vncRemoteControl, vncRemoteCommandEnabledSwitch);

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
                        clearNotificationsOnOpenSwitch.isChecked(),
                        notificationActionButtonsEnabledSwitch.isChecked());
                refreshStatusAndLog();
                return true;
            }
        });
        bindLiveToggles(notificationHistorySettings,
                clearNotificationsOnOpenSwitch, notificationActionButtonsEnabledSwitch);

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
        if ("backup_now".equals(command)) {
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
        if ("vnc_start".equals(command) || "vnc_stop".equals(command)
                || "vnc_test_capture".equals(command)) {
            return flushLiveSaveGroup(vncSettingsSave);
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

    /**
     * Marks a field invalid with a red ring and a line of explanation beneath it, or clears it
     * again. The line is part of the layout rather than the platform's error popup, which is
     * a white box whatever the theme.
     */
    private void setFieldError(EditText field, String message) {
        TextView label = fieldErrors.get(field);
        boolean invalid = invalidFields.contains(field);
        if (message == null) {
            if (invalid) {
                invalidFields.remove(field);
                if (label != null) {
                    label.setVisibility(View.GONE);
                }
                field.setBackground(Ui.fieldBackground(this, false));
            }
            return;
        }
        if (label == null && field.getParent() instanceof LinearLayout) {
            LinearLayout parent = (LinearLayout) field.getParent();
            label = historyText("", 12.5f, COLOR_BAD, false);
            LinearLayout.LayoutParams lp = matchWrapParams();
            lp.topMargin = dp(6);
            parent.addView(label, parent.indexOfChild(field) + 1, lp);
            fieldErrors.put(field, label);
        }
        if (label != null) {
            label.setText(message);
            label.setVisibility(View.VISIBLE);
        }
        if (!invalid) {
            invalidFields.add(field);
            field.setBackground(Ui.fieldBackground(this, true));
        }
    }

    private boolean requireInteger(EditText field, String label, int min, int max) {
        try {
            int value = Integer.parseInt(text(field).trim());
            if (value < min || value > max) {
                return invalidSetting(field, label, "Use a value from " + min + " to " + max);
            }
            setFieldError(field, null);
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
            setFieldError(field, null);
            return true;
        } catch (RuntimeException e) {
            return invalidSetting(field, label, "Use a value from " + min + " to " + max);
        }
    }

    private boolean requireText(EditText field, String label) {
        if (text(field).trim().isEmpty()) {
            return invalidSetting(field, label, "This value is required");
        }
        setFieldError(field, null);
        return true;
    }

    private boolean requireAppSelection(EditText field, String label) {
        if (appPackageFromField(field).trim().isEmpty()) {
            return invalidSetting(field, label, "Choose an app");
        }
        setFieldError(field, null);
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
            setFieldError(field, null);
            return true;
        } catch (RuntimeException e) {
            return invalidSetting(field, label, "Use a complete server URL");
        }
    }

    private boolean requireIpv4(EditText field, String label, boolean optional) {
        String value = text(field).trim();
        if (optional && value.isEmpty()) {
            setFieldError(field, null);
            return true;
        }
        if (strictIpv4Value(value) < 0L) {
            return invalidSetting(field, label, "Use a dotted IPv4 address");
        }
        setFieldError(field, null);
        return true;
    }

    private boolean requireSubnetMask(EditText field, String label) {
        long mask = strictIpv4Value(text(field).trim());
        long inverted = (~mask) & 0xffffffffL;
        if (mask <= 0L || (inverted & (inverted + 1L)) != 0L) {
            return invalidSetting(field, label, "Use a contiguous subnet mask");
        }
        setFieldError(field, null);
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
        setFieldError(field, message);
        if (settingsFeedbackToast != null) {
            settingsFeedbackToast.cancel();
        }
        settingsFeedbackToast = Toast.makeText(
                getApplicationContext(), "Check " + label, Toast.LENGTH_SHORT);
        settingsFeedbackToast.show();
        return false;
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
        if (notificationBackupRefreshInFlight) {
            pendingNotificationBackupRefresh = true;
            return;
        }
        notificationBackupRefreshInFlight = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int queued = NotificationBackupStore.count(MainActivity.this);
                final long sentCount =
                        NotificationBackupStateStore.sentCount(MainActivity.this);
                final long lastSentAtMillis =
                        NotificationBackupStateStore.lastSentAtMillis(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        notificationBackupRefreshInFlight = false;
                        Config config = Config.get(MainActivity.this);
                        applyNotificationBackupStatus(
                                switchValue(notificationBackupEnabledSwitch,
                                        config.notificationBackupEnabled()),
                                config.remoteLinkEnabled(),
                                RemoteLinkStateStore.isConnected(MainActivity.this),
                                NotificationBackupStateStore.isChecked(MainActivity.this),
                                NotificationBackupStateStore.isServerAvailable(MainActivity.this),
                                queued,
                                sentCount,
                                lastSentAtMillis);
                        if (pendingNotificationBackupRefresh) {
                            pendingNotificationBackupRefresh = false;
                            refreshNotificationBackupStatus();
                        }
                    }
                });
            }
        }, "SystemManagerBackupStatus").start();
    }

    private void applyNotificationBackupStatus(boolean enabled, boolean linkEnabled, boolean linkConnected,
                                               boolean serverChecked, boolean serverAvailable, int queued,
                                               long sentCount, long lastSentAtMillis) {
        if (notificationBackupPill == null) {
            return;
        }
        if (notificationBackupLastSentValue != null) {
            notificationBackupLastSentValue.setText(lastSentAtMillis > 0L
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(lastSentAtMillis))
                    : "Never");
            notificationBackupLastSentValue.setTextColor(
                    lastSentAtMillis > 0L ? COLOR_TEXT : COLOR_TEXT_DIM);
        }
        if (notificationBackupSentCountValue != null) {
            notificationBackupSentCountValue.setText(
                    String.format(Locale.US, "%,d", Math.max(0L, sentCount)));
            notificationBackupSentCountValue.setTextColor(
                    sentCount > 0L ? COLOR_TEXT : COLOR_TEXT_DIM);
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
        } else if (!linkConnected) {
            pillText = "OFFLINE";
            pillBg = COLOR_DANGER_CONTAINER;
            pillFg = COLOR_DANGER_ON_CONTAINER;
            message = "Waiting for the Remote Link to reconnect. Backups are held on this device.";
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
        setTileDetail("notification_backup", !enabled ? "Mirror to your server"
                : queued > 0 ? queued + " waiting to send"
                : sentCount > 0 ? String.format(Locale.US, "%,d sent", sentCount) : "Nothing sent yet");

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
                        queued == 1 ? "1 QUEUED" : (queued + " QUEUED"),
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
            setFieldError(highPriorityPackageField, null);
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

    /**
     * The overview switch for tracking. It does what the Start and Stop buttons do, including
     * saving edits first, then shows whether tracking really is on: it stays off when the
     * settings are invalid or location access is missing.
     */
    private void toggleTracking(boolean on) {
        if (flushSettingsForAction(on ? "start" : "stop")) {
            if (on) {
                startTracking();
            } else {
                stopTracking();
            }
        }
        dashboardTileToggle("gps", Config.get(this).isTrackingEnabled());
    }

    /**
     * The overview switch for the VPN. On means connected or connecting; it runs the same
     * checks as the Connect button and stays off if the profile is not ready.
     */
    private void toggleVpn(boolean on) {
        boolean active = false;
        if (on) {
            active = flushSettingsForAction("vpn_connect") && vpnConnect();
        } else {
            vpnDisconnect();
        }
        dashboardTileToggle("vpn", active);
    }

    private void dashboardTileToggle(String id, boolean on) {
        FeatureTile tile = dashboard == null ? null : dashboard.tile(id);
        if (tile != null) {
            tile.setToggleChecked(on);
        }
    }

    private void startTracking() {
        // Starting the location service without any location access would crash it, and
        // switching tracking on is now a single tap on the overview.
        if (!PermissionState.hasFineLocation(this) && !PermissionState.hasCoarseLocation(this)) {
            Toast.makeText(this, "Allow location access to start tracking", Toast.LENGTH_LONG).show();
            requestCommonPermissions();
            return;
        }
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

    private void openAppNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
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
                remoteLinkAcceptAnySslCertSwitch.isChecked());
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

    private void testRemoteLinkLatency() {
        if (RemoteLinkTestStateStore.isLatencyTesting(this)) {
            LogStore.append(this, "ui", "Remote Link latency test stop requested");
            boolean stopped = RemoteLinkManager.stopLatencyTest(this);
            Toast.makeText(this,
                    stopped ? "Remote Link latency test stopped"
                            : "No Remote Link latency test is running",
                    Toast.LENGTH_SHORT).show();
            refreshRemoteLinkTestStatus();
            return;
        }
        LogStore.append(this, "ui", "Remote Link latency test requested");
        boolean started = RemoteLinkManager.testLatency(this);
        Toast.makeText(this,
                started ? "Measuring Remote Link latency for 10 seconds"
                        : "Remote Link is unavailable or another test is running",
                Toast.LENGTH_SHORT).show();
        refreshRemoteLinkTestStatus();
    }

    private void testRemoteLinkThroughput() {
        if (RemoteLinkTestStateStore.isThroughputTesting(this)) {
            LogStore.append(this, "ui", "Remote Link throughput test stop requested");
            boolean stopped = RemoteLinkManager.stopThroughputTest(this);
            Toast.makeText(this,
                    stopped ? "Remote Link throughput test stopped"
                            : "No Remote Link throughput test is running",
                    Toast.LENGTH_SHORT).show();
            refreshRemoteLinkTestStatus();
            return;
        }
        LogStore.append(this, "ui", "Remote Link throughput test requested");
        boolean started = RemoteLinkManager.testThroughput(this);
        Toast.makeText(this,
                started ? "Testing a 1 MB send, then a 1 MB receive"
                        : "Remote Link is unavailable or another test is running",
                Toast.LENGTH_SHORT).show();
        refreshRemoteLinkTestStatus();
    }

    private boolean saveRebootConfigOnly() {
        if (!rebootNotificationTriggerEnabledSwitch.isChecked()) {
            setFieldError(rebootTriggerPackageField, null);
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

    private void startSystemBackup() {
        if (!canUseRemoteBackup(false)) {
            return;
        }
        final Context app = getApplicationContext();
        final Config config = Config.get(app);
        final long previousBackupMillis = config.lastBackupMillis();
        final long backupMillis = System.currentTimeMillis();
        config.setLastBackupMillis(backupMillis);
        backupOperationInFlight = true;
        backupRestoreInFlight = false;
        LogStore.append(app, "backup", "Backup started");
        refreshSystemBackupStatus();
        Toast.makeText(this, "Backing up to Remote Link", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                File archive = null;
                Exception failure = null;
                try {
                    archive = SystemBackupArchive.create(app);
                    RemoteBackupClient.upload(app, archive);
                } catch (Exception e) {
                    failure = e;
                    config.setLastBackupMillis(previousBackupMillis);
                } finally {
                    if (archive != null) {
                        //noinspection ResultOfMethodCallIgnored
                        archive.delete();
                    }
                }
                final Exception resultFailure = failure;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        backupOperationInFlight = false;
                        backupRestoreInFlight = false;
                        if (resultFailure == null) {
                            LogStore.append(MainActivity.this, "backup", "Backup completed");
                            Toast.makeText(MainActivity.this, "Backup completed",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            String detail = operationFailureMessage(resultFailure, "Backup failed");
                            LogStore.append(MainActivity.this, "backup", "Backup failed: " + detail);
                            Toast.makeText(MainActivity.this, detail, Toast.LENGTH_LONG).show();
                        }
                        refreshStatusAndLog();
                    }
                });
            }
        }, "SystemManagerBackup").start();
    }

    private void confirmSystemRestore() {
        if (!canUseRemoteBackup(true)) {
            return;
        }
        OpenVpnConfirmDialog.show(this, "Restore backup?",
                "This replaces all System Manager settings, credentials, history, logs, and VPN files with the Remote Link backup.",
                "Restore", true, new OpenVpnConfirmDialog.Listener() {
                    @Override
                    public void onConfirm() {
                        startSystemRestore();
                    }
                });
    }

    private void startSystemRestore() {
        if (!canUseRemoteBackup(true)) {
            return;
        }
        final Context app = getApplicationContext();
        backupOperationInFlight = true;
        backupRestoreInFlight = true;
        LogStore.append(app, "backup", "Restore started");
        refreshSystemBackupStatus();
        Toast.makeText(this, "Restoring from Remote Link", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                File archive = null;
                Exception failure = null;
                SystemBackupArchive.RestoreResult result = null;
                final boolean[] servicesStopped = {false};
                try {
                    archive = RemoteBackupClient.download(app);
                    result = SystemBackupArchive.restore(app, archive, new Runnable() {
                        @Override
                        public void run() {
                            servicesStopped[0] = true;
                            prepareForSystemRestore(app);
                        }
                    });
                } catch (Exception e) {
                    failure = e;
                } finally {
                    if (archive != null) {
                        //noinspection ResultOfMethodCallIgnored
                        archive.delete();
                    }
                }
                final Exception resultFailure = failure;
                final SystemBackupArchive.RestoreResult restoreResult = result;
                final boolean resumeServices = servicesStopped[0];
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        backupOperationInFlight = false;
                        backupRestoreInFlight = false;
                        if (resumeServices) {
                            loadConfigIntoFields();
                            syncAfterBackupRestore();
                        }
                        if (resultFailure == null && restoreResult != null) {
                            renderedNotificationHistoryKey = null;
                            notificationImageCache.evictAll();
                            LogStore.append(MainActivity.this, "backup",
                                    "Restore completed files=" + restoreResult.fileCount
                                            + " preference_stores="
                                            + restoreResult.preferenceStoreCount);
                            Toast.makeText(MainActivity.this, "Backup restored",
                                    Toast.LENGTH_SHORT).show();
                            refreshNotificationHistory();
                        } else {
                            String detail = operationFailureMessage(resultFailure, "Restore failed");
                            LogStore.append(MainActivity.this, "backup", "Restore failed: " + detail);
                            Toast.makeText(MainActivity.this, detail, Toast.LENGTH_LONG).show();
                        }
                        refreshStatusAndLog();
                    }
                });
            }
        }, "SystemManagerRestore").start();
    }

    private boolean canUseRemoteBackup(boolean requireExistingBackup) {
        if (backupOperationInFlight) {
            Toast.makeText(this, "A backup operation is already running", Toast.LENGTH_SHORT).show();
            return false;
        }
        Config config = Config.get(this);
        if (!config.remoteLinkEnabled()) {
            Toast.makeText(this, "Enable Remote Link first", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!RemoteLinkStateStore.isConnected(this)) {
            Toast.makeText(this, "Remote Link is not connected", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!SystemBackupStateStore.isChecked(this)) {
            Toast.makeText(this, "Waiting for backup status from Remote Link",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (!SystemBackupStateStore.isServerAvailable(this)) {
            Toast.makeText(this, "Backups are not configured on Remote Link",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        if (requireExistingBackup && !SystemBackupStateStore.backupExists(this)) {
            Toast.makeText(this, "No backup is available", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void refreshUpgradeApk() {
        startUpgradeDownload(false, "", 0L);
    }

    private void startUpgradeDownload() {
        startUpgradeDownload(true, "", 0L);
    }

    private void startUpgradeDownload(String expectedVersionName, long expectedVersionCode) {
        startUpgradeDownload(true, expectedVersionName, expectedVersionCode);
    }

    private void startUpgradeDownload(final boolean installAfterDownload,
                                      final String expectedVersionName,
                                      final long expectedVersionCode) {
        if (upgradeDownloadInFlight) {
            Toast.makeText(this, "An upgrade download is already running",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Config.get(this).remoteLinkEnabled()
                || !RemoteLinkStateStore.isConnected(this)) {
            Toast.makeText(this, "Remote Link is not connected", Toast.LENGTH_LONG).show();
            return;
        }
        if (installAfterDownload && !UpgradeStateStore.isChecked(this)) {
            Toast.makeText(this, "Waiting for upgrade status from Remote Link",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!UpgradeStateStore.isConfigured(this)
                || (installAfterDownload && !UpgradeStateStore.apkExists(this))) {
            Toast.makeText(this, "Upgrade APK is not available", Toast.LENGTH_LONG).show();
            return;
        }

        final Context app = getApplicationContext();
        pendingUpgradeApk = null;
        upgradeDownloadInFlight = true;
        refreshUpgradeStatus();
        LogStore.append(app, "upgrade", installAfterDownload
                ? "Upgrade APK download started"
                : "Upgrade APK version refresh started");
        Toast.makeText(this, installAfterDownload
                ? "Downloading upgrade APK"
                : "Downloading APK to check its version", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                File apk = null;
                String versionName = "";
                long versionCode = 0L;
                Exception failure = null;
                boolean servedVersionChanged = false;
                try {
                    apk = RemoteUpgradeClient.download(app);
                    PackageInfo archive = validateUpgradeApk(apk);
                    versionName = archive.versionName.trim();
                    versionCode = AppVersion.code(archive);
                    if (expectedVersionCode > 0L
                            && (versionCode != expectedVersionCode
                                || !versionName.equals(expectedVersionName))) {
                        servedVersionChanged = true;
                        throw new IOException("The served APK changed after confirmation");
                    }
                    UpgradeStateStore.setApkVersion(app, versionName, versionCode);
                } catch (Exception e) {
                    failure = e;
                    if (apk != null) {
                        //noinspection ResultOfMethodCallIgnored
                        apk.delete();
                    }
                }
                final File downloadedApk = apk;
                final String downloadedVersionName = versionName;
                final long downloadedVersionCode = versionCode;
                final Exception resultFailure = failure;
                final boolean resultServedVersionChanged = servedVersionChanged;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        upgradeDownloadInFlight = false;
                        refreshUpgradeStatus();
                        if (resultFailure != null) {
                            String detail = operationFailureMessage(
                                    resultFailure, "Upgrade download failed");
                            LogStore.append(MainActivity.this, "upgrade",
                                    "Upgrade download failed: " + detail);
                            Toast.makeText(MainActivity.this, detail,
                                    Toast.LENGTH_LONG).show();
                            if (resultServedVersionChanged) {
                                autoUpgradeCheckPending = Config.get(MainActivity.this)
                                        .autoUpgradeEnabled();
                                RemoteLinkManager.probeUpgrade(MainActivity.this,
                                        "auto-upgrade-download-version-changed");
                            }
                            return;
                        }
                        LogStore.append(MainActivity.this, "upgrade",
                                "Upgrade APK downloaded version=" + downloadedVersionName
                                        + " version_code=" + downloadedVersionCode
                                        + " bytes=" + downloadedApk.length());
                        if (installAfterDownload) {
                            prepareUpgradeInstall(downloadedApk);
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "Available version: " + downloadedVersionName,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }, installAfterDownload ? "SystemManagerUpgrade" : "SystemManagerUpgradeRefresh").start();
    }

    private PackageInfo validateUpgradeApk(File apk) throws IOException {
        if (apk == null || !apk.isFile() || apk.length() < 1L) {
            throw new IOException("Downloaded upgrade APK is empty");
        }
        PackageInfo archive = getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(), 0);
        if (archive == null || archive.packageName == null) {
            throw new IOException("Downloaded file is not a valid Android APK");
        }
        if (!getPackageName().equals(archive.packageName)) {
            throw new IOException("Downloaded APK is for a different application");
        }
        String versionName = archive.versionName == null ? "" : archive.versionName.trim();
        if (versionName.isEmpty()) {
            throw new IOException("Downloaded APK does not declare a version name");
        }
        if (AppVersion.code(archive) < 1L) {
            throw new IOException("Downloaded APK does not declare a valid version code");
        }
        return archive;
    }

    private void prepareUpgradeInstall(File apk) {
        pendingUpgradeApk = apk;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivityForResult(permission, REQUEST_INSTALL_UNKNOWN_APPS);
                Toast.makeText(this, "Allow installs from System Manager, then return",
                        Toast.LENGTH_LONG).show();
            } catch (RuntimeException e) {
                pendingUpgradeApk = null;
                LogStore.append(this, "upgrade", "Could not open install permission: "
                        + e.getMessage());
                Toast.makeText(this, "Could not open install permission settings",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        launchUpgradeInstaller();
    }

    private void launchUpgradeInstaller() {
        File apk = pendingUpgradeApk;
        if (apk == null || !apk.isFile()) {
            pendingUpgradeApk = null;
            Toast.makeText(this, "Downloaded upgrade APK is no longer available",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Uri uri = UpgradeApkProvider.apkUri();
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, UpgradeApkProvider.MIME_TYPE);
        install.setClipData(ClipData.newRawUri("System Manager upgrade", uri));
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(install);
            pendingUpgradeApk = null;
            LogStore.append(this, "upgrade", "Android package installer opened");
        } catch (RuntimeException e) {
            pendingUpgradeApk = null;
            LogStore.append(this, "upgrade", "Could not open package installer: "
                    + e.getMessage());
            Toast.makeText(this, "Could not open Android's package installer",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void prepareForSystemRestore(Context context) {
        HighPriorityAlertPlayer.stop(context, "backup-restore");
        OpenVpnManager.disconnect(context, "backup-restore");
        VncManager.stop(context, "backup-restore");
        BeaconManager.stop(context);
        NetworkMonitorService.stop(context);
        RemoteLinkManager.stop(context);
        long deadline = System.currentTimeMillis() + 3_000L;
        while (System.currentTimeMillis() < deadline
                && (RemoteLinkService.isRunning() || OpenVpnService.isActive()
                || VncService.isActive() || BeaconService.isActive())) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private String operationFailureMessage(Exception error, String fallback) {
        if (error == null) {
            return fallback;
        }
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return fallback;
        }
        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        return message.length() > 180 ? message.substring(0, 177) + "..." : message;
    }

    private void syncAfterBackupRestore() {
        Config config = Config.get(this);
        if (config.isTrackingEnabled()) {
            seedWifiStateAsync("backup-restore");
            AlarmScheduler.scheduleGpsPost(this, "backup-restore");
        } else {
            AlarmScheduler.cancelGpsPost(this);
        }
        NetworkMonitorService.sync(this);
        BatteryAlertManager.sync(this, "backup-restore");
        VolumeControlManager.sync(this, "backup-restore");
        RebootManager.sync(this, "backup-restore");
        RemoteLinkManager.restart(this, "backup-restore");
        BeaconManager.refresh(this, "backup-restore");
        OpenVpnManager.sync(this, "backup-restore");
        VncManager.sync(this, "backup-restore");
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
        if (requestCode == REQUEST_INSTALL_UNKNOWN_APPS) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    || getPackageManager().canRequestPackageInstalls()) {
                launchUpgradeInstaller();
            } else {
                pendingUpgradeApk = null;
                LogStore.append(this, "upgrade", "Install permission was not granted");
                Toast.makeText(this, "Install permission was not granted",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_PROJECTION_CONSENT) {
            if (resultCode == RESULT_OK && data != null) {
                // Handed straight to the service: it has to be foreground with
                // the mediaProjection type before it may consume the token.
                Intent authorize = new Intent(this, VncService.class)
                        .setAction(VncService.ACTION_AUTHORIZE)
                        .putExtra(VncService.EXTRA_REASON, "ui-consent")
                        .putExtra(VncService.EXTRA_RESULT_CODE, resultCode)
                        .putExtra(VncService.EXTRA_RESULT_DATA, data);
                startForegroundService(authorize);
                LogStore.append(this, "vnc", "Screen capture consent granted");
            } else {
                LogStore.append(this, "vnc", "Screen capture consent declined");
                Toast.makeText(this, "Screen capture was not authorised", Toast.LENGTH_LONG).show();
            }
            refreshStatusAndLog();
            return;
        }
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
        if (requestCode == REQUEST_SAVE_NOTIFICATION_IMAGE) {
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
                } else if ("backup_now".equals(command)) {
                    startSystemBackup();
                } else if ("restore_backup".equals(command)) {
                    confirmSystemRestore();
                } else if ("refresh_upgrade".equals(command)) {
                    refreshUpgradeApk();
                } else if ("install_upgrade".equals(command)) {
                    startUpgradeDownload();
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
                } else if ("notification_settings".equals(command)) {
                    openAppNotificationSettings();
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
                } else if ("remote_link_latency".equals(command)) {
                    testRemoteLinkLatency();
                } else if ("remote_link_throughput".equals(command)) {
                    testRemoteLinkThroughput();
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
                } else if ("vnc_start".equals(command)) {
                    startVncServer();
                } else if ("vnc_stop".equals(command)) {
                    stopVncServer();
                } else if ("vnc_test_capture".equals(command)) {
                    testVncCapture();
                } else if ("vnc_authorize".equals(command)) {
                    requestProjectionConsent();
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
