package com.jpitsg.sysman;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

final class HighPriorityAlertPlayer {
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MODE_LOOPING = 1;
    private static final int MODE_SINGLE_CYCLE = 2;
    private static final int ALERT_KIND_NONE = 0;
    private static final int ALERT_KIND_HIGH_PRIORITY = 1;
    private static final int ALERT_KIND_REMOTE_ALARM = 2;
    private static final long LOCKED_PLAY_DELAY_MILLIS = 1_000L;
    private static final long POWER_STATE_POLL_MILLIS = 500L;
    private static final int DISMISS_VOLUME_MAX = 100;
    private static final int DISMISS_VOLUME_CURRENT = 50;

    private static Ringtone currentRingtone;
    private static int currentAlertKind = ALERT_KIND_NONE;
    private static long nextPlaybackGeneration;
    private static long currentPlaybackGeneration;
    private static Runnable stopRunnable;
    private static Runnable unlockPollRunnable;
    private static boolean shouldRestoreAlarmVolume;
    private static int originalAlarmVolume = -1;
    private static BroadcastReceiver unlockReceiver;
    private static boolean unlockReceiverRegistered;
    private static BroadcastReceiver screenOnReceiver;
    private static boolean screenOnReceiverRegistered;
    private static Runnable screenOnPollRunnable;
    private static boolean screenOnPollSawNonInteractive;
    private static MediaSession hardwareDismissSession;
    private static BroadcastReceiver powerStateReceiver;
    private static boolean powerStateReceiverRegistered;
    private static Runnable powerStatePollRunnable;
    private static boolean powerStateAtPlaybackStart;

    interface StartCallback {
        void onResult(boolean ok, String reason);
    }

    private HighPriorityAlertPlayer() {
    }

    static void play(final Context context, final String reason) {
        handleNotification(context, reason);
    }

    static void handleNotification(final Context context, final String reason) {
        final Context app = context.getApplicationContext();
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                DeviceState state = DeviceState.read(app);
                LogStore.append(app, "alert", "High-priority device state interactive="
                        + state.interactive + " locked=" + state.locked + " reason=" + reason);
                if (state.locked) {
                    playAfterLockedDelay(app, reason + ":locked");
                } else {
                    playOnMain(app, reason + ":unlocked", MODE_SINGLE_CYCLE, false, true);
                }
            }
        });
    }

    static void stop(final Context context, final String reason) {
        final Context app = context.getApplicationContext();
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    stopLocked(app, reason);
                }
            }
        });
    }

    static void stopHighPriorityForHardwareButton(final Context context, final String reason) {
        final Context app = context.getApplicationContext();
        final long generation;
        synchronized (LOCK) {
            if (currentAlertKind != ALERT_KIND_HIGH_PRIORITY || currentRingtone == null) {
                return;
            }
            generation = currentPlaybackGeneration;
        }
        requestPlaybackStop(app, ALERT_KIND_HIGH_PRIORITY, generation, reason);
    }

    static void playRemoteAlarm(final Context context, final String toneTitle, final int seconds,
                                final boolean vibrateWithTone, final String reason,
                                final StartCallback callback) {
        final Context app = context.getApplicationContext();
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                StartResult result;
                synchronized (LOCK) {
                    result = playRemoteAlarmOnMain(app, toneTitle, seconds, vibrateWithTone, reason);
                }
                if (callback != null) {
                    callback.onResult(result.ok, result.reason);
                }
            }
        });
    }

    private static void playAfterLockedDelay(final Context app, final String reason) {
        synchronized (LOCK) {
            stopLocked(app, "locked-alert-delay");
            stopRunnable = new Runnable() {
                @Override
                public void run() {
                    synchronized (LOCK) {
                        if (stopRunnable != this) {
                            return;
                        }
                        stopRunnable = null;
                        DeviceState state = DeviceState.read(app);
                        LogStore.append(app, "alert", "Delayed high-priority device state interactive="
                                + state.interactive + " locked=" + state.locked + " reason=" + reason);
                        if (!state.locked) {
                            LogStore.append(app, "alert", "High-priority alert skipped; device unlocked during delay reason=" + reason);
                        } else {
                            playOnMain(app, reason + ":delayed", MODE_LOOPING, true, true);
                        }
                    }
                }
            };
            MAIN.postDelayed(stopRunnable, LOCKED_PLAY_DELAY_MILLIS);
            LogStore.append(app, "alert", "Delaying locked high-priority tone by "
                    + LOCKED_PLAY_DELAY_MILLIS + "ms reason=" + reason);
        }
    }

    private static void playOnMain(Context app, String reason, int mode, boolean stopOnUnlock, boolean vibrateWithTone) {
        synchronized (LOCK) {
            stopLocked(app, "restart");

            Config config = Config.get(app);
            AudioManager audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            prepareAlarmVolume(app, audio, config);

            Uri toneUri = findToneUri(app, config.highPriorityToneTitle());
            if (toneUri == null) {
                LogStore.append(app, "alert", "No alarm tone URI available");
                restoreAlarmVolume(app, audio);
                return;
            }

            Ringtone ringtone = RingtoneManager.getRingtone(app, toneUri);
            if (ringtone == null) {
                LogStore.append(app, "alert", "Could not load alarm tone uri=" + toneUri);
                restoreAlarmVolume(app, audio);
                return;
            }

            boolean interactiveAtPlaybackStart = DeviceState.read(app).interactive;
            try {
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setLooping(mode == MODE_LOOPING);
                    ringtone.setVolume(1.0f);
                }
                ringtone.play();
            } catch (RuntimeException e) {
                LogStore.append(app, "alert", "Could not play alert tone: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                restoreAlarmVolume(app, audio);
                return;
            }

            currentRingtone = ringtone;
            currentAlertKind = ALERT_KIND_HIGH_PRIORITY;
            final long generation = beginPlaybackGeneration();
            long maxPlayMillis = Math.max(1000L, config.highPriorityPlaySeconds() * 1000L);
            if (vibrateWithTone) {
                vibrate(app, reason + ":tone", maxPlayMillis);
            }
            if (stopOnUnlock) {
                registerUnlockStop(app, generation);
            }
            if (mode == MODE_SINGLE_CYCLE) {
                scheduleSingleCycleStop(app, ringtone, generation, maxPlayMillis);
            } else {
                scheduleTimeoutStop(app, ALERT_KIND_HIGH_PRIORITY, generation, maxPlayMillis);
            }
            registerHighPriorityHardwareDismiss(app, generation, interactiveAtPlaybackStart);
            LogStore.append(app, "alert", "Playing high-priority alert tone=" + config.highPriorityToneTitle()
                    + " reason=" + reason
                    + " mode=" + (mode == MODE_SINGLE_CYCLE ? "single-cycle" : "looping")
                    + " stopOnUnlock=" + stopOnUnlock
                    + " vibrateWithTone=" + vibrateWithTone
                    + " maxDuration=" + config.highPriorityPlaySeconds() + "s");
        }
    }

    private static StartResult playRemoteAlarmOnMain(Context app, String toneTitle, int seconds,
                                                     boolean vibrateWithTone, String reason) {
        stopLocked(app, "remote-alarm-restart");

        int safeSeconds = Math.max(1, Math.min(300, seconds));
        Config config = Config.get(app);
        AudioManager audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        prepareAlarmVolume(app, audio, config);

        Uri toneUri = findRemoteAlarmToneUri(app, toneTitle);
        if (toneUri == null) {
            LogStore.append(app, "alert", "Remote alarm failed; no tone URI available tone=" + safeText(toneTitle));
            restoreAlarmVolume(app, audio);
            return new StartResult(false, "tone unavailable");
        }

        Ringtone ringtone = RingtoneManager.getRingtone(app, toneUri);
        if (ringtone == null) {
            LogStore.append(app, "alert", "Remote alarm failed; could not load tone uri=" + toneUri);
            restoreAlarmVolume(app, audio);
            return new StartResult(false, "tone load failed");
        }

        try {
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.setLooping(true);
                ringtone.setVolume(1.0f);
            }
            ringtone.play();
        } catch (RuntimeException e) {
            LogStore.append(app, "alert", "Remote alarm failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            restoreAlarmVolume(app, audio);
            return new StartResult(false, "playback failed");
        }

        currentRingtone = ringtone;
        currentAlertKind = ALERT_KIND_REMOTE_ALARM;
        final long generation = beginPlaybackGeneration();
        long maxPlayMillis = safeSeconds * 1000L;
        if (vibrateWithTone) {
            vibrate(app, reason + ":remote-alarm", maxPlayMillis);
        }
        scheduleTimeoutStop(app, ALERT_KIND_REMOTE_ALARM, generation, maxPlayMillis);
        registerScreenOnStop(app, generation);
        LogStore.append(app, "alert", "Playing remote alarm tone=" + safeText(toneTitle)
                + " reason=" + reason
                + " vibrateWithTone=" + vibrateWithTone
                + " stopOnScreenOn=true"
                + " duration=" + safeSeconds + "s");
        return new StartResult(true, "started");
    }

    private static long beginPlaybackGeneration() {
        nextPlaybackGeneration++;
        if (nextPlaybackGeneration == 0L) {
            nextPlaybackGeneration++;
        }
        currentPlaybackGeneration = nextPlaybackGeneration;
        return currentPlaybackGeneration;
    }

    private static boolean isCurrentPlayback(int alertKind, long generation) {
        return generation != 0L
                && currentRingtone != null
                && currentAlertKind == alertKind
                && currentPlaybackGeneration == generation;
    }

    private static void requestPlaybackStop(final Context app, final int alertKind,
                                            final long generation, final String reason) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    if (!isCurrentPlayback(alertKind, generation)) {
                        return;
                    }
                    stopLocked(app, reason);
                }
            }
        });
    }

    private static void scheduleTimeoutStop(final Context app, final int alertKind,
                                            final long generation, long playMillis) {
        stopRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    if (stopRunnable != this || !isCurrentPlayback(alertKind, generation)) {
                        return;
                    }
                    stopLocked(app, "timeout");
                }
            }
        };
        MAIN.postDelayed(stopRunnable, playMillis);
    }

    private static void scheduleSingleCycleStop(final Context app, final Ringtone ringtone,
                                                final long generation, final long maxMillis) {
        final long startedAt = System.currentTimeMillis();
        stopRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    if (stopRunnable != this
                            || currentRingtone != ringtone
                            || !isCurrentPlayback(ALERT_KIND_HIGH_PRIORITY, generation)) {
                        return;
                    }
                    boolean stillPlaying = false;
                    try {
                        stillPlaying = ringtone.isPlaying();
                    } catch (RuntimeException e) {
                        LogStore.append(app, "alert", "Could not poll alert playback: " + e.getMessage());
                    }
                    if (!stillPlaying) {
                        stopLocked(app, "single-cycle-complete");
                        return;
                    }
                    if (System.currentTimeMillis() - startedAt >= maxMillis) {
                        stopLocked(app, "single-cycle-timeout");
                        return;
                    }
                    MAIN.postDelayed(this, 500L);
                }
            }
        };
        MAIN.postDelayed(stopRunnable, 500L);
    }

    private static void prepareAlarmVolume(Context app, AudioManager audio, Config config) {
        shouldRestoreAlarmVolume = false;
        originalAlarmVolume = -1;
        if (!config.highPriorityRaiseAlarmVolume() || audio == null || audio.isVolumeFixed()) {
            return;
        }

        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int current = audio.getStreamVolume(AudioManager.STREAM_ALARM);
            int target = Math.max(1, Math.round(max * (config.highPriorityAlarmVolumePercent() / 100f)));
            if (current < target) {
                originalAlarmVolume = current;
                shouldRestoreAlarmVolume = true;
                audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0);
                LogStore.append(app, "alert", "Raised alarm volume " + current + "/" + max + " -> " + target + "/" + max);
            }
        } catch (SecurityException e) {
            LogStore.append(app, "alert", "Alarm volume change blocked: " + e.getMessage());
        } catch (RuntimeException e) {
            LogStore.append(app, "alert", "Alarm volume change failed: " + e.getMessage());
        }
    }

    private static void stopLocked(Context app, String reason) {
        if (stopRunnable != null) {
            MAIN.removeCallbacks(stopRunnable);
            stopRunnable = null;
        }

        int stoppedAlertKind = currentAlertKind;
        currentAlertKind = ALERT_KIND_NONE;
        currentPlaybackGeneration = 0L;

        unregisterUnlockStop(app);
        unregisterScreenOnStop(app);
        unregisterHighPriorityPowerStop(app);
        SystemManagerAccessibilityService.setHighPriorityKeyCaptureEnabled(false);
        releaseHardwareDismissSession(app);
        cancelVibration(app);

        if (currentRingtone != null) {
            try {
                currentRingtone.stop();
                String alertName = stoppedAlertKind == ALERT_KIND_REMOTE_ALARM
                        ? "remote alarm"
                        : "high-priority alert";
                LogStore.append(app, "alert", "Stopped " + alertName + " reason=" + reason);
            } catch (RuntimeException e) {
                LogStore.append(app, "alert", "Could not stop alert tone: " + e.getMessage());
            }
            currentRingtone = null;
        }

        AudioManager audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        restoreAlarmVolume(app, audio);
    }

    private static void registerHighPriorityHardwareDismiss(final Context app,
                                                            final long generation,
                                                            boolean interactiveAtPlaybackStart) {
        registerHardwareVolumeStop(app, generation);
        registerHighPriorityPowerStop(app, generation, interactiveAtPlaybackStart);
        SystemManagerAccessibilityService.setHighPriorityKeyCaptureEnabled(true);
    }

    private static void registerHardwareVolumeStop(final Context app, final long generation) {
        releaseHardwareDismissSession(app);

        MediaSession session = null;
        try {
            session = new MediaSession(app, "SystemManagerHighPriorityAlert");
            session.setPlaybackToRemote(new VolumeProvider(
                    VolumeProvider.VOLUME_CONTROL_RELATIVE,
                    DISMISS_VOLUME_MAX,
                    DISMISS_VOLUME_CURRENT) {
                @Override
                public void onAdjustVolume(int direction) {
                    if (direction == AudioManager.ADJUST_RAISE) {
                        requestPlaybackStop(app, ALERT_KIND_HIGH_PRIORITY, generation,
                                "hardware-volume-up");
                    } else if (direction == AudioManager.ADJUST_LOWER) {
                        requestPlaybackStop(app, ALERT_KIND_HIGH_PRIORITY, generation,
                                "hardware-volume-down");
                    }
                }
            });
            session.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build());
            session.setActive(true);
            hardwareDismissSession = session;
            LogStore.append(app, "alert", "Hardware volume dismissal session active");
        } catch (RuntimeException e) {
            if (hardwareDismissSession == session) {
                hardwareDismissSession = null;
            }
            if (session != null) {
                try {
                    session.release();
                } catch (RuntimeException ignored) {
                }
            }
            LogStore.append(app, "alert", "Could not activate hardware volume dismissal: "
                    + e.getMessage());
        }
    }

    private static void releaseHardwareDismissSession(Context app) {
        MediaSession session = hardwareDismissSession;
        hardwareDismissSession = null;
        if (session == null) {
            return;
        }
        try {
            session.setActive(false);
        } catch (RuntimeException e) {
            LogStore.append(app, "alert", "Could not deactivate hardware volume dismissal: "
                    + e.getMessage());
        }
        try {
            session.release();
        } catch (RuntimeException e) {
            LogStore.append(app, "alert", "Could not release hardware volume dismissal: "
                    + e.getMessage());
        }
    }

    private static void registerHighPriorityPowerStop(final Context app, final long generation,
                                                      boolean interactiveAtPlaybackStart) {
        unregisterHighPriorityPowerStop(app);
        powerStateAtPlaybackStart = interactiveAtPlaybackStart;

        // Android does not reliably expose the power key itself to applications. A short
        // press normally changes the interactive state, so use both broadcasts and a poll;
        // either path is sufficient and the playback generation makes duplicates harmless.
        powerStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    requestPlaybackStop(app, ALERT_KIND_HIGH_PRIORITY, generation,
                            "hardware-power-screen-on");
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    requestPlaybackStop(app, ALERT_KIND_HIGH_PRIORITY, generation,
                            "hardware-power-screen-off");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(powerStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                app.registerReceiver(powerStateReceiver, filter);
            }
            powerStateReceiverRegistered = true;
        } catch (RuntimeException e) {
            powerStateReceiver = null;
            powerStateReceiverRegistered = false;
            LogStore.append(app, "alert", "Could not register power-state receiver: "
                    + e.getMessage());
        }
        if (powerStateReceiverRegistered) {
            LogStore.append(app, "alert", "Registered high-priority power-state receiver");
        }

        powerStatePollRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    if (powerStatePollRunnable != this) {
                        return;
                    }
                    if (!isCurrentPlayback(ALERT_KIND_HIGH_PRIORITY, generation)) {
                        powerStatePollRunnable = null;
                        return;
                    }
                    if (DeviceState.read(app).interactive != powerStateAtPlaybackStart) {
                        stopLocked(app, "hardware-power-state-change");
                        return;
                    }
                    MAIN.postDelayed(this, POWER_STATE_POLL_MILLIS);
                }
            }
        };

        if (DeviceState.read(app).interactive != powerStateAtPlaybackStart) {
            requestPlaybackStop(app, ALERT_KIND_HIGH_PRIORITY, generation,
                    "hardware-power-state-change");
        }
        MAIN.postDelayed(powerStatePollRunnable, POWER_STATE_POLL_MILLIS);
    }

    private static void unregisterHighPriorityPowerStop(Context app) {
        if (powerStatePollRunnable != null) {
            MAIN.removeCallbacks(powerStatePollRunnable);
            powerStatePollRunnable = null;
        }
        if (!powerStateReceiverRegistered || powerStateReceiver == null) {
            powerStateReceiver = null;
            powerStateReceiverRegistered = false;
            return;
        }
        try {
            app.unregisterReceiver(powerStateReceiver);
        } catch (RuntimeException e) {
            LogStore.append(app, "alert", "Could not unregister power-state receiver: "
                    + e.getMessage());
        } finally {
            powerStateReceiver = null;
            powerStateReceiverRegistered = false;
        }
    }

    private static void registerUnlockStop(final Context app, final long generation) {
        if (!unlockReceiverRegistered) {
            unlockReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent != null && Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                        requestPlaybackStop(app, ALERT_KIND_HIGH_PRIORITY, generation, "unlocked");
                    }
                }
            };

            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    app.registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    app.registerReceiver(unlockReceiver, filter);
                }
                unlockReceiverRegistered = true;
                LogStore.append(app, "alert", "Registered unlock stop receiver");
            } catch (RuntimeException e) {
                unlockReceiver = null;
                unlockReceiverRegistered = false;
                LogStore.append(app, "alert", "Could not register unlock receiver: " + e.getMessage());
            }
        }
        scheduleUnlockPoll(app, generation);
    }

    private static void scheduleUnlockPoll(final Context app, final long generation) {
        if (unlockPollRunnable != null) {
            return;
        }
        unlockPollRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    if (unlockPollRunnable != this) {
                        return;
                    }
                    if (!isCurrentPlayback(ALERT_KIND_HIGH_PRIORITY, generation)) {
                        unlockPollRunnable = null;
                        return;
                    }
                    if (!DeviceState.read(app).locked) {
                        LogStore.append(app, "alert", "Device unlock detected by poll; stopping high-priority alert");
                        stopLocked(app, "unlocked");
                        return;
                    }
                    MAIN.postDelayed(this, 500L);
                }
            }
        };
        MAIN.postDelayed(unlockPollRunnable, 500L);
    }

    private static void registerScreenOnStop(final Context app, final long generation) {
        if (!screenOnReceiverRegistered) {
            screenOnReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent != null && Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                        requestPlaybackStop(app, ALERT_KIND_REMOTE_ALARM, generation, "screen-on");
                    }
                }
            };

            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    app.registerReceiver(screenOnReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    app.registerReceiver(screenOnReceiver, filter);
                }
                screenOnReceiverRegistered = true;
                LogStore.append(app, "alert", "Registered screen-on stop receiver");
            } catch (RuntimeException e) {
                screenOnReceiver = null;
                screenOnReceiverRegistered = false;
                LogStore.append(app, "alert", "Could not register screen-on receiver: " + e.getMessage());
            }
        }
        scheduleScreenOnPoll(app, generation);
    }

    private static void scheduleScreenOnPoll(final Context app, final long generation) {
        if (screenOnPollRunnable != null) {
            return;
        }
        // Only a transition to interactive should stop the alarm; if the display
        // is already on when the alarm starts, keep playing until it goes off and
        // back on (or the timeout fires).
        screenOnPollSawNonInteractive = !DeviceState.read(app).interactive;
        screenOnPollRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    if (screenOnPollRunnable != this) {
                        return;
                    }
                    if (!isCurrentPlayback(ALERT_KIND_REMOTE_ALARM, generation)) {
                        screenOnPollRunnable = null;
                        return;
                    }
                    if (!DeviceState.read(app).interactive) {
                        screenOnPollSawNonInteractive = true;
                    } else if (screenOnPollSawNonInteractive) {
                        LogStore.append(app, "alert", "Display turn-on detected by poll; stopping remote alarm");
                        stopLocked(app, "screen-on");
                        return;
                    }
                    MAIN.postDelayed(this, 500L);
                }
            }
        };
        MAIN.postDelayed(screenOnPollRunnable, 500L);
    }

    private static void unregisterScreenOnStop(Context app) {
        if (screenOnPollRunnable != null) {
            MAIN.removeCallbacks(screenOnPollRunnable);
            screenOnPollRunnable = null;
        }
        if (!screenOnReceiverRegistered || screenOnReceiver == null) {
            screenOnReceiver = null;
            screenOnReceiverRegistered = false;
            return;
        }
        try {
            app.unregisterReceiver(screenOnReceiver);
        } catch (RuntimeException e) {
            LogStore.append(app, "alert", "Could not unregister screen-on receiver: " + e.getMessage());
        } finally {
            screenOnReceiver = null;
            screenOnReceiverRegistered = false;
        }
    }

    private static void unregisterUnlockStop(Context app) {
        if (unlockPollRunnable != null) {
            MAIN.removeCallbacks(unlockPollRunnable);
            unlockPollRunnable = null;
        }
        if (!unlockReceiverRegistered || unlockReceiver == null) {
            unlockReceiver = null;
            unlockReceiverRegistered = false;
            return;
        }
        try {
            app.unregisterReceiver(unlockReceiver);
        } catch (RuntimeException e) {
            LogStore.append(app, "alert", "Could not unregister unlock receiver: " + e.getMessage());
        } finally {
            unlockReceiver = null;
            unlockReceiverRegistered = false;
        }
    }

    private static void restoreAlarmVolume(Context app, AudioManager audio) {
        if (!shouldRestoreAlarmVolume || originalAlarmVolume < 0 || audio == null) {
            shouldRestoreAlarmVolume = false;
            originalAlarmVolume = -1;
            return;
        }

        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0);
            LogStore.append(app, "alert", "Restored alarm volume to " + originalAlarmVolume);
        } catch (SecurityException e) {
            LogStore.append(app, "alert", "Alarm volume restore blocked: " + e.getMessage());
        } catch (RuntimeException e) {
            LogStore.append(app, "alert", "Alarm volume restore failed: " + e.getMessage());
        } finally {
            shouldRestoreAlarmVolume = false;
            originalAlarmVolume = -1;
        }
    }

    private static Uri findToneUri(Context context, String requestedTitle) {
        String wanted = requestedTitle == null ? "" : requestedTitle.trim();
        if (!wanted.isEmpty()) {
            Uri exact = findToneUri(context, wanted, true);
            if (exact != null) {
                return exact;
            }
            Uri contains = findToneUri(context, wanted, false);
            if (contains != null) {
                return contains;
            }
            LogStore.append(context, "alert", "Tone title not found; using default alarm title=" + wanted);
        }

        Uri defaultAlarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (defaultAlarm != null) {
            return defaultAlarm;
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    private static Uri findRemoteAlarmToneUri(Context context, String requestedTitle) {
        String wanted = requestedTitle == null ? "" : requestedTitle.trim();
        if (wanted.isEmpty()) {
            return findToneUri(context, "");
        }
        Uri exact = findToneUri(context, wanted, true);
        if (exact != null) {
            return exact;
        }
        Uri contains = findToneUri(context, wanted, false);
        if (contains != null) {
            return contains;
        }
        LogStore.append(context, "alert", "Remote alarm tone title not found title=" + safeText(wanted));
        return null;
    }

    private static Uri findToneUri(Context context, String wanted, boolean exact) {
        int[] types = new int[]{
                RingtoneManager.TYPE_ALARM,
                RingtoneManager.TYPE_RINGTONE,
                RingtoneManager.TYPE_NOTIFICATION
        };

        for (int type : types) {
            RingtoneManager manager = new RingtoneManager(context);
            manager.setType(type);
            Cursor cursor = null;
            try {
                cursor = manager.getCursor();
                if (cursor == null) {
                    continue;
                }
                for (int i = 0; i < cursor.getCount(); i++) {
                    if (!cursor.moveToPosition(i)) {
                        continue;
                    }
                    String title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                    if (matchesTitle(title, wanted, exact)) {
                        LogStore.append(context, "alert", "Matched tone title=" + title + " type=" + type);
                        return manager.getRingtoneUri(i);
                    }
                }
            } catch (RuntimeException e) {
                LogStore.append(context, "alert", "Tone lookup failed type=" + type + ": " + e.getMessage());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return null;
    }

    private static boolean matchesTitle(String title, String wanted, boolean exact) {
        if (title == null) {
            return false;
        }
        String normalizedTitle = title.trim().toLowerCase();
        String normalizedWanted = wanted.trim().toLowerCase();
        if (exact) {
            return normalizedTitle.equals(normalizedWanted);
        }
        return normalizedTitle.contains(normalizedWanted);
    }

    private static String safeText(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }

    @SuppressWarnings("deprecation")
    private static void vibrate(Context context, String reason, long durationMillis) {
        if (durationMillis <= 0L) {
            return;
        }
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            LogStore.append(context, "alert", "High-priority vibration unavailable reason=" + reason);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMillis);
            }
            LogStore.append(context, "alert", "Vibrating high-priority alert for " + durationMillis + "ms reason=" + reason);
        } catch (RuntimeException e) {
            LogStore.append(context, "alert", "High-priority vibration failed: " + e.getMessage());
        }
    }

    private static void cancelVibration(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) {
            return;
        }
        try {
            vibrator.cancel();
        } catch (RuntimeException ignored) {
        }
    }

    private static final class DeviceState {
        final boolean interactive;
        final boolean locked;

        DeviceState(boolean interactive, boolean locked) {
            this.interactive = interactive;
            this.locked = locked;
        }

        static DeviceState read(Context context) {
            boolean interactive = true;
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                interactive = powerManager.isInteractive();
            }

            boolean locked = false;
            KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                locked = keyguardManager.isKeyguardLocked();
            }
            return new DeviceState(interactive, locked);
        }
    }

    private static final class StartResult {
        final boolean ok;
        final String reason;

        StartResult(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }
    }
}
