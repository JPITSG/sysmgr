package com.jpitsg.sysman;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a committed profile by actually launching the embedded openvpn
 * binary with --management-hold: if it parses the config and loads all files it
 * parks at hold; an early exit means an options/file error. Also resolves the
 * engine version string for the UI footer.
 */
final class OpenVpnHoldTester {
    private static final long HOLD_DEADLINE_MS = 5_000L;
    private static volatile String cachedVersion;

    interface HoldCallback {
        void onResult(boolean passed, String version, String openssl, String failureTail);
    }

    interface VersionCallback {
        void onVersion(String version);
    }

    private OpenVpnHoldTester() {
    }

    static void runHoldTestAsync(final Context context, final HoldCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                runHoldTest(context.getApplicationContext(), callback);
            }
        }, "VpnHoldTest").start();
    }

    private static void runHoldTest(Context context, HoldCallback callback) {
        OpenVpnProfileStore.Meta meta = OpenVpnProfileStore.readMeta(context);
        if (!meta.allSlotsSatisfied()) {
            callback.onResult(false, "", "", "awaiting certificate files");
            return;
        }
        File validateDir = new File(OpenVpnProfileStore.dir(context), "validate");
        //noinspection ResultOfMethodCallIgnored
        validateDir.mkdirs();
        File socket = new File(validateDir, "mgmt.sock");
        if (socket.exists()) {
            //noinspection ResultOfMethodCallIgnored
            socket.delete();
        }

        String binary = new File(context.getApplicationInfo().nativeLibraryDir, "libopenvpn.so").getAbsolutePath();
        List<String> argv = new ArrayList<>();
        argv.add(binary);
        argv.add("--config");
        argv.add(OpenVpnProfileStore.profileConf(context).getAbsolutePath());
        argv.add("--management");
        argv.add(socket.getAbsolutePath());
        argv.add("unix");
        argv.add("--management-hold");
        argv.add("--verb");
        argv.add("3");
        argv.add("--suppress-timestamps");

        ArrayDeque<String> tail = new ArrayDeque<>();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(argv);
            builder.redirectErrorStream(true);
            builder.directory(OpenVpnProfileStore.dir(context));
            builder.environment().put("LD_LIBRARY_PATH", context.getApplicationInfo().nativeLibraryDir);
            builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
            process = builder.start();

            final Process readProcess = process;
            final ArrayDeque<String> sharedTail = tail;
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(readProcess.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            synchronized (sharedTail) {
                                sharedTail.addLast(line);
                                while (sharedTail.size() > 200) {
                                    sharedTail.removeFirst();
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }, "VpnHoldTestReader");
            reader.start();

            long deadline = System.currentTimeMillis() + HOLD_DEADLINE_MS;
            boolean exited = false;
            int exitCode = 0;
            while (System.currentTimeMillis() < deadline) {
                try {
                    exitCode = process.exitValue();
                    exited = true;
                    break;
                } catch (IllegalThreadStateException alive) {
                    Thread.sleep(100L);
                }
            }

            if (exited) {
                String failure = lastError(tail);
                callback.onResult(false, "", "", failure.isEmpty()
                        ? "openvpn exited during validation (code " + exitCode + ")" : failure);
            } else {
                // Parked at hold => config + files are valid.
                String version = resolveVersion(context);
                callback.onResult(true, versionOnly(version), opensslOnly(version), "");
            }
        } catch (Exception e) {
            callback.onResult(false, "", "", "validation error: " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
            deleteRecursive(validateDir);
        }
    }

    static void resolveVersionAsync(final Context context, final VersionCallback callback) {
        String cached = cachedVersion;
        if (cached != null) {
            callback.onVersion(cached);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String version = resolveVersion(context.getApplicationContext());
                callback.onVersion(version);
            }
        }, "VpnVersion").start();
    }

    private static synchronized String resolveVersion(Context context) {
        if (cachedVersion != null) {
            return cachedVersion;
        }
        String binary = new File(context.getApplicationInfo().nativeLibraryDir, "libopenvpn.so").getAbsolutePath();
        String versionLine = "";
        String opensslLine = "";
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(binary, "--version");
            builder.redirectErrorStream(true);
            builder.environment().put("LD_LIBRARY_PATH", context.getApplicationInfo().nativeLibraryDir);
            process = builder.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("OpenVPN ") && versionLine.isEmpty()) {
                        versionLine = line;
                    }
                    if (line.contains("OpenSSL") && opensslLine.isEmpty()) {
                        opensslLine = line;
                    }
                }
            }
            // --version exits non-zero by design; don't inspect exit code.
            waitBounded(process, 5_000L);
        } catch (Exception e) {
            cachedVersion = "OpenVPN engine unavailable";
            return cachedVersion;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        String vpn = firstToken(versionLine, "OpenVPN engine");
        String ssl = extractOpenssl(opensslLine);
        cachedVersion = ssl.isEmpty() ? vpn : vpn + " · " + ssl;
        return cachedVersion;
    }

    private static void waitBounded(Process p, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            try {
                p.exitValue();
                return;
            } catch (IllegalThreadStateException alive) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static String firstToken(String versionLine, String fallback) {
        if (versionLine.isEmpty()) {
            return fallback;
        }
        // "OpenVPN 2.7.5 aarch64-..." -> "OpenVPN 2.7.5"
        String[] parts = versionLine.split("\\s+");
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1];
        }
        return versionLine;
    }

    private static String extractOpenssl(String line) {
        int idx = line.indexOf("OpenSSL");
        if (idx < 0) {
            return "";
        }
        String rest = line.substring(idx);
        String[] parts = rest.split("\\s+");
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1];
        }
        return parts[0];
    }

    private static String versionOnly(String combined) {
        int dot = combined.indexOf(" · ");
        return dot < 0 ? combined : combined.substring(0, dot);
    }

    private static String opensslOnly(String combined) {
        int dot = combined.indexOf(" · ");
        return dot < 0 ? "" : combined.substring(dot + 3);
    }

    private static String lastError(ArrayDeque<String> tail) {
        synchronized (tail) {
            String best = "";
            for (String line : tail) {
                if (line.contains("Options error") || line.startsWith("ERROR:")
                        || line.contains("Cannot") || line.contains("FATAL")) {
                    best = line;
                }
            }
            if (best.isEmpty() && !tail.isEmpty()) {
                best = tail.getLast();
            }
            // Strip the machine-readable prefix "<ts> <flags> " if present.
            String[] parts = best.split("\\s+", 3);
            if (parts.length == 3 && parts[0].contains(".")) {
                return parts[2];
            }
            return best;
        }
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursive(c);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
