package com.jpitsg.sysman;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

final class VolumeControlManager {
    private VolumeControlManager() {
    }

    static void sync(Context context, String reason) {
        Context app = context.getApplicationContext();
        List<Config.VolumeRule> rules = Config.get(app).volumeRules();
        if (rules.isEmpty()) {
            if ("rule-deleted".equals(reason) || "settings-import".equals(reason)) {
                AlarmScheduler.cancelVolumeRule(app);
                LogStore.append(app, "volume", "Volume rule scheduling skipped; no rules configured reason=" + reason);
            }
            return;
        }
        applyCurrentRule(app, reason);
        AlarmScheduler.scheduleNextVolumeRule(app, reason);
    }

    static void handleScheduledAlarm(Context context, String reason) {
        Context app = context.getApplicationContext();
        applyCurrentRule(app, reason);
        AlarmScheduler.scheduleNextVolumeRule(app, "after-rule:" + reason);
    }

    static void applyCurrentRule(Context context, String reason) {
        Context app = context.getApplicationContext();
        Config.VolumeRule rule = currentRuleForNow(Config.get(app).volumeRules(), Calendar.getInstance());
        if (rule == null) {
            LogStore.append(app, "volume", "No volume rule applies reason=" + reason);
            return;
        }
        applyRule(app, rule, reason);
    }

    static Config.VolumeRule currentRuleForNow(List<Config.VolumeRule> rules, Calendar now) {
        if (rules == null || rules.isEmpty() || now == null) {
            return null;
        }
        int currentMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        Config.VolumeRule bestToday = null;
        Config.VolumeRule bestPreviousDay = null;
        for (Config.VolumeRule rule : rules) {
            if (rule.minuteOfDay() <= currentMinute) {
                if (bestToday == null || rule.minuteOfDay() >= bestToday.minuteOfDay()) {
                    bestToday = rule;
                }
            }
            if (bestPreviousDay == null || rule.minuteOfDay() >= bestPreviousDay.minuteOfDay()) {
                bestPreviousDay = rule;
            }
        }
        return bestToday == null ? bestPreviousDay : bestToday;
    }

    static Calendar nextRuleTimeAfter(List<Config.VolumeRule> rules, Calendar now) {
        if (rules == null || rules.isEmpty() || now == null) {
            return null;
        }
        int currentMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        Config.VolumeRule nextToday = null;
        Config.VolumeRule firstTomorrow = null;
        for (Config.VolumeRule rule : rules) {
            if (rule.minuteOfDay() > currentMinute) {
                if (nextToday == null || rule.minuteOfDay() < nextToday.minuteOfDay()) {
                    nextToday = rule;
                }
            }
            if (firstTomorrow == null || rule.minuteOfDay() < firstTomorrow.minuteOfDay()) {
                firstTomorrow = rule;
            }
        }
        Config.VolumeRule next = nextToday == null ? firstTomorrow : nextToday;
        if (next == null) {
            return null;
        }
        Calendar trigger = (Calendar) now.clone();
        trigger.set(Calendar.HOUR_OF_DAY, next.hour);
        trigger.set(Calendar.MINUTE, next.minute);
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);
        if (nextToday == null || !trigger.after(now)) {
            trigger.add(Calendar.DAY_OF_YEAR, 1);
        }
        return trigger;
    }

    private static void applyRule(Context context, Config.VolumeRule rule, String reason) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            LogStore.append(context, "volume", "AudioManager unavailable rule=" + rule.displayTime() + " reason=" + reason);
            return;
        }

        int applied = 0;
        applied += applyStream(context, audioManager, AudioManager.STREAM_MUSIC, "media", rule.mediaPercent);
        applied += applyStream(context, audioManager, AudioManager.STREAM_RING, "ring", rule.ringPercent);
        applied += applyStream(context, audioManager, AudioManager.STREAM_NOTIFICATION, "notification", rule.notificationPercent);
        applied += applyStream(context, audioManager, AudioManager.STREAM_ALARM, "alarm", rule.alarmPercent);
        LogStore.append(context, "volume", "Applied volume rule time=" + rule.displayTime()
                + " applied=" + applied
                + " media=" + Config.volumeDisplay(rule.mediaPercent)
                + " ring=" + Config.volumeDisplay(rule.ringPercent)
                + " notification=" + Config.volumeDisplay(rule.notificationPercent)
                + " alarm=" + Config.volumeDisplay(rule.alarmPercent)
                + " reason=" + reason);
    }

    private static int applyStream(
            Context context,
            AudioManager audioManager,
            int stream,
            String label,
            int percent) {
        if (percent == Config.VOLUME_UNCHANGED) {
            return 0;
        }
        try {
            int max = Math.max(1, audioManager.getStreamMaxVolume(stream));
            int min = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                min = audioManager.getStreamMinVolume(stream);
            }
            int target = Math.round(max * (percent / 100f));
            target = Math.max(min, Math.min(max, target));
            audioManager.setStreamVolume(stream, target, 0);
            return 1;
        } catch (RuntimeException e) {
            LogStore.append(context, "volume", "Volume stream failed stream=" + label
                    + " percent=" + percent
                    + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return 0;
        }
    }
}
