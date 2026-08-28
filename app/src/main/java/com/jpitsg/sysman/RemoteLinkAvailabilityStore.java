package com.jpitsg.sysman;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** Persists Remote Link down intervals and calculates availability over a rolling day. */
final class RemoteLinkAvailabilityStore {
    static final long WINDOW_MILLIS = 24L * 60L * 60L * 1000L;

    private static final String PREFS = "system_manager_remote_link_availability";
    private static final String KEY_TRACKING_SINCE = "tracking_since";
    private static final String KEY_CONNECTED = "connected";
    private static final String KEY_STATE_SINCE = "state_since";
    private static final String KEY_DOWN_INTERVALS = "down_intervals";
    private static final Object LOCK = new Object();

    private RemoteLinkAvailabilityStore() {
    }

    static final class Snapshot {
        final long observedMillis;
        final long downMillis;
        final double upPercent;

        Snapshot(long observedMillis, long downMillis, double upPercent) {
            this.observedMillis = observedMillis;
            this.downMillis = downMillis;
            this.upPercent = upPercent;
        }
    }

    static final class DownInterval {
        final long startMillis;
        final long endMillis;

        DownInterval(long startMillis, long endMillis) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }
    }

    static void recordState(Context context, boolean connected) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            recordStateLocked(prefs(app), connected, now);
        }
    }

    static Snapshot snapshot(Context context) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        boolean connected = RemoteLinkStateStore.isConnected(app);
        synchronized (LOCK) {
            SharedPreferences state = prefs(app);
            if (!state.contains(KEY_TRACKING_SINCE)
                    || state.getBoolean(KEY_CONNECTED, connected) != connected) {
                recordStateLocked(state, connected, now);
            }

            long trackingSince = state.getLong(KEY_TRACKING_SINCE, now);
            long stateSince = state.getLong(KEY_STATE_SINCE, now);
            List<DownInterval> intervals = parseIntervals(
                    state.getString(KEY_DOWN_INTERVALS, ""));
            if (!historyIsSane(trackingSince, stateSince, intervals, now)) {
                resetLocked(state, connected, now);
                trackingSince = now;
                stateSince = now;
                intervals.clear();
            }
            return calculate(trackingSince, connected, stateSince, intervals, now);
        }
    }

    static Snapshot calculate(long trackingSince, boolean connected, long stateSince,
                              List<DownInterval> completedIntervals, long now) {
        long windowStart = Math.max(0L, now - WINDOW_MILLIS);
        long observedStart = Math.max(windowStart, Math.min(now, trackingSince));
        long observedMillis = Math.max(0L, now - observedStart);

        List<DownInterval> downIntervals = new ArrayList<>(completedIntervals);
        if (!connected && stateSince < now) {
            downIntervals.add(new DownInterval(stateSince, now));
        }
        Collections.sort(downIntervals, new Comparator<DownInterval>() {
            @Override
            public int compare(DownInterval first, DownInterval second) {
                return Long.compare(first.startMillis, second.startMillis);
            }
        });

        long downMillis = 0L;
        long mergedStart = -1L;
        long mergedEnd = -1L;
        for (DownInterval interval : downIntervals) {
            long start = Math.max(observedStart, interval.startMillis);
            long end = Math.min(now, interval.endMillis);
            if (end <= start) {
                continue;
            }
            if (mergedStart < 0L) {
                mergedStart = start;
                mergedEnd = end;
            } else if (start <= mergedEnd) {
                mergedEnd = Math.max(mergedEnd, end);
            } else {
                downMillis = addClamped(downMillis, mergedEnd - mergedStart, observedMillis);
                mergedStart = start;
                mergedEnd = end;
            }
        }
        if (mergedStart >= 0L) {
            downMillis = addClamped(downMillis, mergedEnd - mergedStart, observedMillis);
        }

        double upPercent;
        if (observedMillis <= 0L) {
            upPercent = connected ? 100.0 : 0.0;
        } else {
            upPercent = 100.0 * (observedMillis - downMillis) / observedMillis;
        }
        return new Snapshot(observedMillis, downMillis,
                Math.max(0.0, Math.min(100.0, upPercent)));
    }

    private static void recordStateLocked(SharedPreferences state, boolean connected, long now) {
        if (!state.contains(KEY_TRACKING_SINCE)) {
            resetLocked(state, connected, now);
            return;
        }

        long trackingSince = state.getLong(KEY_TRACKING_SINCE, now);
        long stateSince = state.getLong(KEY_STATE_SINCE, now);
        List<DownInterval> intervals = parseIntervals(
                state.getString(KEY_DOWN_INTERVALS, ""));
        if (!historyIsSane(trackingSince, stateSince, intervals, now)) {
            resetLocked(state, connected, now);
            return;
        }

        boolean previousConnected = state.getBoolean(KEY_CONNECTED, connected);
        if (previousConnected == connected) {
            return;
        }
        if (!previousConnected && now > stateSince) {
            intervals.add(new DownInterval(stateSince, now));
        }
        long cutoff = Math.max(0L, now - WINDOW_MILLIS);
        for (Iterator<DownInterval> iterator = intervals.iterator(); iterator.hasNext(); ) {
            if (iterator.next().endMillis <= cutoff) {
                iterator.remove();
            }
        }
        state.edit()
                .putBoolean(KEY_CONNECTED, connected)
                .putLong(KEY_STATE_SINCE, now)
                .putString(KEY_DOWN_INTERVALS, encodeIntervals(intervals))
                .apply();
    }

    private static void resetLocked(SharedPreferences state, boolean connected, long now) {
        state.edit()
                .putLong(KEY_TRACKING_SINCE, now)
                .putBoolean(KEY_CONNECTED, connected)
                .putLong(KEY_STATE_SINCE, now)
                .remove(KEY_DOWN_INTERVALS)
                .apply();
    }

    private static boolean historyIsSane(long trackingSince, long stateSince,
                                         List<DownInterval> intervals, long now) {
        if (trackingSince <= 0L || trackingSince > now
                || stateSince < trackingSince || stateSince > now) {
            return false;
        }
        for (DownInterval interval : intervals) {
            if (interval.startMillis < trackingSince
                    || interval.endMillis < interval.startMillis
                    || interval.endMillis > now) {
                return false;
            }
        }
        return true;
    }

    private static long addClamped(long total, long value, long maximum) {
        if (value <= 0L || total >= maximum) {
            return Math.min(total, maximum);
        }
        return value >= maximum - total ? maximum : total + value;
    }

    private static List<DownInterval> parseIntervals(String encoded) {
        List<DownInterval> result = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }
        String[] entries = encoded.split(";");
        for (String entry : entries) {
            int separator = entry.indexOf(',');
            if (separator <= 0 || separator >= entry.length() - 1) {
                continue;
            }
            try {
                long start = Long.parseLong(entry.substring(0, separator));
                long end = Long.parseLong(entry.substring(separator + 1));
                if (start >= 0L && end >= start) {
                    result.add(new DownInterval(start, end));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static String encodeIntervals(List<DownInterval> intervals) {
        StringBuilder result = new StringBuilder();
        for (DownInterval interval : intervals) {
            if (result.length() > 0) {
                result.append(';');
            }
            result.append(interval.startMillis).append(',').append(interval.endMillis);
        }
        return result.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
