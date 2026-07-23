package com.jpitsg.sysman;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the embedded openvpn binary and pumps its (merged) stdout/stderr into
 * LogStore, then reports the exit code back to the service. One instance per
 * connection attempt.
 */
final class OpenVpnProcessThread implements Runnable {

    interface Listener {
        /** Called once, after the process exits. requested=true if we asked it to stop. */
        void onProcessExited(int exitCode, boolean requested, String lastFatal);
    }

    private final Context context;
    private final List<String> argv;
    private final File workingDir;
    private final String nativeLibraryDir;
    private final Listener listener;
    private final boolean logDebugLines;

    private volatile Process process;
    private volatile boolean stopRequested;
    private volatile String lastFatal = "";

    OpenVpnProcessThread(Context context, List<String> argv, File workingDir,
                         String nativeLibraryDir, boolean logDebugLines, Listener listener) {
        this.context = context.getApplicationContext();
        this.argv = argv;
        this.workingDir = workingDir;
        this.nativeLibraryDir = nativeLibraryDir;
        this.logDebugLines = logDebugLines;
        this.listener = listener;
    }

    @Override
    public void run() {
        int exitCode = -1;
        try {
            ProcessBuilder builder = new ProcessBuilder(argv);
            builder.redirectErrorStream(true);
            if (workingDir != null) {
                builder.directory(workingDir);
            }
            builder.environment().put("LD_LIBRARY_PATH", nativeLibraryDir);
            builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
            process = builder.start();
            LogStore.append(context, "vpn", "openvpn process started pid-tracked");

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    consumeLine(line);
                }
            }
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LogStore.append(context, "vpn", "openvpn process wait interrupted");
        } catch (Exception e) {
            LogStore.append(context, "vpn", "openvpn process error: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (listener != null) {
                listener.onProcessExited(exitCode, stopRequested, lastFatal);
            }
        }
    }

    /**
     * Parses a machine-readable openvpn log line
     * ("<unix-ts>.<frac> <hex-flags> <message>") and routes it to LogStore.
     */
    private void consumeLine(String line) {
        String message = line;
        String flags = "";
        int firstSpace = line.indexOf(' ');
        int secondSpace = firstSpace < 0 ? -1 : line.indexOf(' ', firstSpace + 1);
        if (firstSpace > 0 && secondSpace > firstSpace && isMachineTimestamp(line.substring(0, firstSpace))) {
            flags = line.substring(firstSpace + 1, secondSpace);
            message = line.substring(secondSpace + 1);
        }
        if (!logDebugLines && flags.indexOf('D') >= 0) {
            return; // drop debug-level lines at verb 4 to avoid thrashing the log trim
        }
        if (message.contains("FATAL") || message.startsWith("ERROR:") || message.contains("Options error")) {
            lastFatal = message.trim();
        }
        LogStore.append(context, "vpn", "ovpn: " + message.trim());
    }

    private static boolean isMachineTimestamp(String token) {
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c != '.' && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }

    boolean isAlive() {
        Process p = process;
        if (p == null) {
            return false;
        }
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException alive) {
            return true;
        }
    }

    /**
     * Requests process shutdown: SIGINT via management is preferred by the
     * caller; this is the hard fallback that destroys the process directly.
     */
    void stop() {
        stopRequested = true;
        Process p = process;
        if (p == null) {
            return;
        }
        if (waitForExit(p, 5000L)) {
            return;
        }
        p.destroy();
        if (waitForExit(p, 2000L)) {
            return;
        }
        p.destroyForcibly();
    }

    /** Marks that the coming exit was requested (used when management SIGINT is sent). */
    void markStopRequested() {
        stopRequested = true;
    }

    private static boolean waitForExit(Process p, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            try {
                p.exitValue();
                return true;
            } catch (IllegalThreadStateException alive) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    static List<String> buildArgv(String binaryPath, File profileConf, File managementSocket,
                                   boolean hold, int verb) {
        List<String> argv = new ArrayList<>();
        argv.add(binaryPath);
        argv.add("--config");
        argv.add(profileConf.getAbsolutePath());
        argv.add("--management");
        argv.add(managementSocket.getAbsolutePath());
        argv.add("unix");
        argv.add("--management-client");
        if (hold) {
            argv.add("--management-hold");
        }
        argv.add("--management-query-passwords");
        argv.add("--verb");
        argv.add(Integer.toString(verb));
        argv.add("--machine-readable-output");
        argv.add("--suppress-timestamps");
        argv.add("--auth-nocache");
        return argv;
    }
}
