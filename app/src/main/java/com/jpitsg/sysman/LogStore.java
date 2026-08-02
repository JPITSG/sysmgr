package com.jpitsg.sysman;

import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

final class LogStore {
    static final String ACTION_CHANGED = "com.jpitsg.sysman.action.LOG_CHANGED";
    private static final String LOG_FILE = "system-manager.log";
    private static final String LOG_TMP_FILE = "system-manager.log.tmp";
    private static final Object LOCK = new Object();

    private LogStore() {
    }

    static void append(Context context, String tag, String message) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (!Config.get(app).logEnabled()) {
                return;
            }
            File file = logFile(app);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String line = timestamp + " [" + tag + "] " + message + "\n";
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                return;
            }
            trim(app, Config.get(app).logMaxLines());
        }
        broadcastChanged(app);
    }

    static String readTail(Context context, int maxLines) {
        synchronized (LOCK) {
            ArrayDeque<String> lines = new ArrayDeque<>();
            File file = logFile(context.getApplicationContext());
            if (!file.exists()) {
                return "";
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.addLast(line);
                    while (lines.size() > maxLines) {
                        lines.removeFirst();
                    }
                }
            } catch (Exception e) {
                return "Could not read log: " + e.getMessage();
            }
            StringBuilder builder = new StringBuilder();
            for (Iterator<String> iterator = lines.descendingIterator(); iterator.hasNext(); ) {
                String line = iterator.next();
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    static void clear(Context context) {
        Context app = context.getApplicationContext();
        boolean changed;
        synchronized (LOCK) {
            File file = logFile(app);
            changed = !file.exists() || file.delete();
        }
        if (changed) {
            broadcastChanged(app);
        }
    }

    static void replaceWithSingleEntry(Context context, String tag, String message) {
        Context app = context.getApplicationContext();
        boolean changed;
        synchronized (LOCK) {
            File file = logFile(app);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String line = timestamp + " [" + tag + "] " + message + "\n";
            changed = writeAtomically(app, file, line);
        }
        if (changed) {
            broadcastChanged(app);
        }
    }

    private static File logFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILE);
    }

    private static void trim(Context context, int maxLines) {
        File file = logFile(context);
        if (!file.exists() || file.length() < 256 * 1024L) {
            return;
        }
        ArrayDeque<String> lines = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.addLast(line);
                while (lines.size() > maxLines) {
                    lines.removeFirst();
                }
            }
        } catch (Exception ignored) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        writeAtomically(context, file, builder.toString());
    }

    private static boolean writeAtomically(Context context, File target, String content) {
        File temp = new File(context.getFilesDir(), LOG_TMP_FILE);
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        } catch (Exception ignored) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }

        if (target.exists() && !target.delete()) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }
        return true;
    }

    private static void broadcastChanged(Context context) {
        context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
    }
}
