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
    private static final long LOCKED_PLAY_DELAY_MILLIS = 1_000L;

    private static Ringtone currentRingtone;
    private static Runnable stopRunnable;
    private static Runnable unlockPollRunnable;
    private static boolean shouldRestoreAlarmVolume;
    private static int originalAlarmVolume = -1;
    private static BroadcastReceiver unlockReceiver;
    private static boolean unlockReceiverRegistered;

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
            long maxPlayMillis = Math.max(1000L, config.highPriorityPlaySeconds() * 1000L);
            if (vibrateWithTone) {
                vibrate(app, reason + ":tone", maxPlayMillis);
            }
            if (stopOnUnlock) {
                registerUnlockStop(app);
            }
            if (mode == MODE_SINGLE_CYCLE) {
                scheduleSingleCycleStop(app, ringtone, maxPlayMillis);
            } else {
                scheduleTimeoutStop(app, maxPlayMillis);
            }
            LogStore.append(app, "alert", "Playing high-priority alert tone=" + config.highPriorityToneTitle()
                    + " reason=" + reason
                    + " mode=" + (mode == MODE_SINGLE_CYCLE ? "single-cycle" : "looping")
                    + " stopOnUnlock=" + stopOnUnlock
                    + " vibrateWithTone=" + vibrateWithTone
                    + " maxDuration=" + config.highPriorityPlaySeconds() + "s");
        }
    }

    private static void scheduleTimeoutStop(final Context app, long playMillis) {
        stopRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    stopLocked(app, "timeout");
                }
            }
        };
        MAIN.postDelayed(stopRunnable, playMillis);
    }

    private static void scheduleSingleCycleStop(final Context app, final Ringtone ringtone, final long maxMillis) {
        final long startedAt = System.currentTimeMillis();
        stopRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    if (currentRingtone != ringtone) {
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
        unregisterUnlockStop(app);
        cancelVibration(app);

        if (currentRingtone != null) {
            try {
                currentRingtone.stop();
                LogStore.append(app, "alert", "Stopped high-priority alert reason=" + reason);
            } catch (RuntimeException e) {
                LogStore.append(app, "alert", "Could not stop alert tone: " + e.getMessage());
            }
            currentRingtone = null;
        }

        AudioManager audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        restoreAlarmVolume(app, audio);
    }

    private static void registerUnlockStop(final Context app) {
        if (!unlockReceiverRegistered) {
            unlockReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent != null && Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                        LogStore.append(app, "alert", "Device unlocked; stopping high-priority alert");
                        stop(app, "unlocked");
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
        scheduleUnlockPoll(app);
    }

    private static void scheduleUnlockPoll(final Context app) {
        if (unlockPollRunnable != null) {
            return;
        }
        unlockPollRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                    if (currentRingtone == null) {
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
}
