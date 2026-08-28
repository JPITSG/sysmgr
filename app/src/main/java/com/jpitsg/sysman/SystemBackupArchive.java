package com.jpitsg.sysman;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Creates and restores the complete app-private state as a tar.gz archive. */
final class SystemBackupArchive {
    private static final String FORMAT = "system-manager-backup";
    private static final int FORMAT_VERSION = 1;
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String PREFERENCES_ENTRY = "preferences.json";
    private static final String FILES_PREFIX = "files/";
    private static final int TAR_BLOCK_SIZE = 512;
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final int MAX_METADATA_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L * 1024L;

    // Include stores even when their XML has not been flushed to disk yet.
    private static final List<String> KNOWN_PREFERENCE_STORES = Arrays.asList(
            "battery_alert_state",
            "system_manager_backup_state",
            "system_manager_beacon_state",
            "system_manager_config",
            "system_manager_network_state",
            "system_manager_notification_backup_state",
            "system_manager_remote_link_availability",
            "system_manager_remote_link_state",
            "system_manager_remote_link_test_state",
            "system_manager_vnc_secret",
            "system_manager_vnc_state",
            "system_manager_vpn_state");

    private SystemBackupArchive() {
    }

    static final class RestoreResult {
        final long createdAtMillis;
        final int preferenceStoreCount;
        final int fileCount;

        RestoreResult(long createdAtMillis, int preferenceStoreCount, int fileCount) {
            this.createdAtMillis = createdAtMillis;
            this.preferenceStoreCount = preferenceStoreCount;
            this.fileCount = fileCount;
        }
    }

    static File create(Context context) throws Exception {
        Context app = context.getApplicationContext();
        long createdAt = System.currentTimeMillis();
        File archive = new File(app.getCacheDir(),
                "system-manager-backup-" + UUID.randomUUID().toString() + ".tar.gz");
        JSONObject manifest = new JSONObject();
        manifest.put("format", FORMAT);
        manifest.put("version", FORMAT_VERSION);
        manifest.put("package", app.getPackageName());
        manifest.put("created_at", createdAt);

        byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
        byte[] preferenceBytes = serializePreferences(app).getBytes(StandardCharsets.UTF_8);
        List<FileEntry> files = collectPersistentFiles(app.getFilesDir());

        try (FileOutputStream fileOutput = new FileOutputStream(archive, false);
             GZIPOutputStream gzip = new GZIPOutputStream(fileOutput, COPY_BUFFER_SIZE);
             TarWriter tar = new TarWriter(gzip)) {
            tar.addBytes(MANIFEST_ENTRY, manifestBytes, createdAt);
            tar.addBytes(PREFERENCES_ENTRY, preferenceBytes, createdAt);
            for (FileEntry entry : files) {
                tar.addFile(FILES_PREFIX + entry.relativePath, entry.file);
            }
            tar.finish();
            gzip.finish();
            fileOutput.flush();
            fileOutput.getFD().sync();
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            archive.delete();
            throw e;
        }
        return archive;
    }

    /**
     * Validates and stages the whole archive before invoking {@code beforeApply}.
     * The callback can stop active services only once the downloaded backup is
     * known to be complete and safe to apply.
     */
    static RestoreResult restore(Context context, File archive, Runnable beforeApply)
            throws Exception {
        Context app = context.getApplicationContext();
        if (archive == null || !archive.isFile() || archive.length() < 1L) {
            throw new IOException("Backup archive is empty");
        }
        File stage = new File(app.getCacheDir(),
                "system-manager-restore-stage-" + UUID.randomUUID().toString());
        if (!stage.mkdirs()) {
            throw new IOException("Could not prepare restore staging area");
        }
        try {
            ExtractedBackup extracted = extractAndValidate(app, archive, stage);
            if (beforeApply != null) {
                beforeApply.run();
            }
            apply(app, stage, extracted.preferences);
            RestoreResult result = new RestoreResult(extracted.createdAtMillis,
                    extracted.preferences.size(), extracted.fileCount);
            deleteRecursiveQuietly(stage);
            return result;
        } catch (Exception primary) {
            try {
                deleteRecursive(stage);
            } catch (Exception cleanupFailure) {
                primary.addSuppressed(cleanupFailure);
            }
            throw primary;
        }
    }

    private static String serializePreferences(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        JSONObject stores = new JSONObject();
        for (String name : preferenceStoreNames(context)) {
            JSONObject values = new JSONObject();
            Map<String, ?> all = context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll();
            List<String> keys = new ArrayList<>(all.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                Object value = all.get(key);
                if (key != null && value != null) {
                    values.put(key, encodePreferenceValue(value));
                }
            }
            stores.put(name, values);
        }
        root.put("stores", stores);
        return root.toString();
    }

    private static JSONObject encodePreferenceValue(Object value) throws Exception {
        JSONObject encoded = new JSONObject();
        if (value instanceof Boolean) {
            encoded.put("type", "boolean");
            encoded.put("value", value);
        } else if (value instanceof Integer) {
            encoded.put("type", "int");
            encoded.put("value", value);
        } else if (value instanceof Long) {
            encoded.put("type", "long");
            encoded.put("value", value);
        } else if (value instanceof Float) {
            encoded.put("type", "float");
            encoded.put("value", ((Float) value).doubleValue());
        } else if (value instanceof Set) {
            encoded.put("type", "string-set");
            JSONArray values = new JSONArray();
            @SuppressWarnings("unchecked")
            Set<String> strings = (Set<String>) value;
            List<String> sorted = new ArrayList<>();
            for (String item : strings) {
                sorted.add(item == null ? "" : item);
            }
            Collections.sort(sorted);
            for (String item : sorted) {
                values.put(item);
            }
            encoded.put("value", values);
        } else {
            encoded.put("type", "string");
            encoded.put("value", String.valueOf(value));
        }
        return encoded;
    }

    private static Set<String> preferenceStoreNames(Context context) {
        Set<String> names = new TreeSet<>(KNOWN_PREFERENCE_STORES);
        File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = prefsDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (file.isFile() && name.endsWith(".xml")) {
                    String store = name.substring(0, name.length() - 4);
                    if (validPreferenceStoreName(store)) {
                        names.add(store);
                    }
                }
            }
        }
        return names;
    }

    private static List<FileEntry> collectPersistentFiles(File root) throws IOException {
        List<FileEntry> entries = new ArrayList<>();
        collectPersistentFiles(root, root, entries);
        Collections.sort(entries, new Comparator<FileEntry>() {
            @Override
            public int compare(FileEntry left, FileEntry right) {
                return left.relativePath.compareTo(right.relativePath);
            }
        });
        return entries;
    }

    private static void collectPersistentFiles(File root, File current, List<FileEntry> entries)
            throws IOException {
        if (!current.exists() || Files.isSymbolicLink(current.toPath())) {
            return;
        }
        if (current.isDirectory()) {
            File[] children = current.listFiles();
            if (children == null) {
                throw new IOException("Could not read app data directory " + current.getName());
            }
            Arrays.sort(children, new Comparator<File>() {
                @Override
                public int compare(File left, File right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (File child : children) {
                collectPersistentFiles(root, child, entries);
            }
            return;
        }
        if (!current.isFile() || current.getName().endsWith(".tmp")) {
            return;
        }
        String rootPath = root.getCanonicalPath();
        String filePath = current.getCanonicalPath();
        if (!filePath.startsWith(rootPath + File.separator)) {
            throw new IOException("App data file escaped its private directory");
        }
        String relative = filePath.substring(rootPath.length() + 1)
                .replace(File.separatorChar, '/');
        validateRelativePath(relative);
        entries.add(new FileEntry(relative, current));
    }

    private static ExtractedBackup extractAndValidate(Context context, File archive, File stage)
            throws Exception {
        File stagedFiles = new File(stage, "files");
        if (!stagedFiles.mkdirs()) {
            throw new IOException("Could not prepare restored files");
        }
        byte[] manifestBytes = null;
        byte[] preferenceBytes = null;
        int entries = 0;
        int fileCount = 0;
        long totalBytes = 0L;
        Set<String> seen = new HashSet<>();

        try (InputStream fileInput = new FileInputStream(archive);
             GZIPInputStream input = new GZIPInputStream(fileInput, COPY_BUFFER_SIZE)) {
            while (true) {
                byte[] header = readTarBlock(input);
                if (header == null) {
                    throw new EOFException("Backup archive ended before the tar footer");
                }
                if (isZeroBlock(header)) {
                    byte[] second = readTarBlock(input);
                    if (second == null || !isZeroBlock(second)) {
                        throw new IOException("Backup archive has an invalid tar footer");
                    }
                    drainGzip(input);
                    break;
                }
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw new IOException("Backup archive contains too many files");
                }
                TarEntry entry = parseTarHeader(header);
                if (entry.size < 0L
                        || entry.size > MAX_UNCOMPRESSED_BYTES - totalBytes) {
                    throw new IOException("Backup archive expands beyond the supported size");
                }
                totalBytes += entry.size;
                if (!seen.add(entry.name)) {
                    throw new IOException("Backup archive contains duplicate entry " + entry.name);
                }

                if (entry.directory) {
                    if (entry.name.startsWith(FILES_PREFIX)) {
                        String relative = trimDirectorySuffix(entry.name.substring(FILES_PREFIX.length()));
                        if (!relative.isEmpty()) {
                            validateRelativePath(relative);
                            File directory = safeStageFile(stagedFiles, relative);
                            if (!directory.exists() && !directory.mkdirs()) {
                                throw new IOException("Could not prepare restored directory "
                                        + relative);
                            }
                        }
                    }
                    skipExactly(input, entry.size);
                } else if (!entry.regularFile) {
                    throw new IOException("Backup archive contains an unsupported entry type");
                } else if (MANIFEST_ENTRY.equals(entry.name)) {
                    manifestBytes = readMetadata(input, entry.size);
                } else if (PREFERENCES_ENTRY.equals(entry.name)) {
                    preferenceBytes = readMetadata(input, entry.size);
                } else if (entry.name.startsWith(FILES_PREFIX)) {
                    String relative = entry.name.substring(FILES_PREFIX.length());
                    validateRelativePath(relative);
                    File target = safeStageFile(stagedFiles, relative);
                    File parent = target.getParentFile();
                    if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                        throw new IOException("Could not prepare restored file " + relative);
                    }
                    try (FileOutputStream output = new FileOutputStream(target, false)) {
                        copyExactly(input, output, entry.size);
                        output.flush();
                    }
                    if (entry.modifiedAtMillis > 0L) {
                        //noinspection ResultOfMethodCallIgnored
                        target.setLastModified(entry.modifiedAtMillis);
                    }
                    fileCount++;
                } else {
                    skipExactly(input, entry.size);
                }
                skipExactly(input, tarPadding(entry.size));
            }
        }

        if (manifestBytes == null || preferenceBytes == null) {
            throw new IOException("Backup archive is missing required metadata");
        }
        JSONObject manifest = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
        if (!FORMAT.equals(manifest.optString("format", ""))) {
            throw new IOException("This is not a System Manager backup");
        }
        if (manifest.optInt("version", -1) != FORMAT_VERSION) {
            throw new IOException("Backup format version is not supported");
        }
        if (!context.getPackageName().equals(manifest.optString("package", ""))) {
            throw new IOException("Backup belongs to a different app");
        }
        Map<String, Map<String, Object>> preferences = parsePreferences(preferenceBytes);
        if (!preferences.containsKey("system_manager_config")) {
            throw new IOException("Backup is missing System Manager settings");
        }
        return new ExtractedBackup(manifest.optLong("created_at", 0L), preferences, fileCount);
    }

    private static Map<String, Map<String, Object>> parsePreferences(byte[] bytes)
            throws Exception {
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (root.optInt("version", -1) != FORMAT_VERSION) {
            throw new IOException("Backup preference format is not supported");
        }
        JSONObject stores = root.optJSONObject("stores");
        if (stores == null) {
            throw new IOException("Backup preferences are missing");
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        List<String> storeNames = jsonKeys(stores);
        Collections.sort(storeNames);
        for (String storeName : storeNames) {
            if (!validPreferenceStoreName(storeName)) {
                throw new IOException("Backup contains an invalid preference store");
            }
            JSONObject values = stores.optJSONObject(storeName);
            if (values == null) {
                throw new IOException("Backup contains invalid preferences for " + storeName);
            }
            Map<String, Object> decoded = new LinkedHashMap<>();
            List<String> keys = jsonKeys(values);
            Collections.sort(keys);
            for (String key : keys) {
                if (key == null || key.indexOf('\u0000') >= 0) {
                    throw new IOException("Backup contains an invalid preference key");
                }
                JSONObject encoded = values.optJSONObject(key);
                if (encoded == null) {
                    throw new IOException("Backup contains an invalid preference value");
                }
                decoded.put(key, decodePreferenceValue(encoded));
            }
            result.put(storeName, decoded);
        }
        return result;
    }

    private static Object decodePreferenceValue(JSONObject encoded) throws Exception {
        String type = encoded.optString("type", "");
        if ("boolean".equals(type)) {
            Object value = encoded.opt("value");
            if (!(value instanceof Boolean)) {
                throw new IOException("Invalid boolean preference");
            }
            return value;
        }
        if ("int".equals(type)) {
            return Integer.valueOf(encoded.getInt("value"));
        }
        if ("long".equals(type)) {
            return Long.valueOf(encoded.getLong("value"));
        }
        if ("float".equals(type)) {
            double value = encoded.getDouble("value");
            if (!Double.isFinite(value)) {
                throw new IOException("Invalid float preference");
            }
            return Float.valueOf((float) value);
        }
        if ("string".equals(type)) {
            return encoded.getString("value");
        }
        if ("string-set".equals(type)) {
            JSONArray array = encoded.optJSONArray("value");
            if (array == null) {
                throw new IOException("Invalid string-set preference");
            }
            Set<String> values = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                values.add(array.getString(i));
            }
            return values;
        }
        throw new IOException("Backup contains an unknown preference type");
    }

    private static void apply(Context context, File stage,
                              Map<String, Map<String, Object>> restoredPreferences)
            throws Exception {
        Map<String, Map<String, Object>> originalPreferences = capturePreferences(context);
        File dataDir = new File(context.getApplicationInfo().dataDir);
        File liveFiles = context.getFilesDir();
        File stagedFiles = new File(stage, "files");
        File rollbackFiles = new File(dataDir, "files.system-backup-rollback");
        deleteRecursive(rollbackFiles);

        boolean movedOriginal = false;
        boolean installedRestored = false;
        try {
            if (liveFiles.exists()) {
                if (!liveFiles.renameTo(rollbackFiles)) {
                    throw new IOException("Could not stage current app files for restore");
                }
                movedOriginal = true;
            }
            if (!stagedFiles.renameTo(liveFiles)) {
                // A service finishing its shutdown may briefly recreate files/.
                // Remove that fresh directory and make one bounded retry.
                deleteRecursive(liveFiles);
                if (!stagedFiles.renameTo(liveFiles)) {
                    throw new IOException("Could not install restored app files");
                }
            }
            installedRestored = true;
            applyPreferences(context, restoredPreferences);
            // The restored state is already committed. A cleanup failure must
            // not trigger a rollback from a directory that may be half removed.
            deleteRecursiveQuietly(rollbackFiles);
        } catch (Exception primary) {
            Exception rollbackFailure = null;
            try {
                if (installedRestored || liveFiles.exists()) {
                    deleteRecursive(liveFiles);
                }
                if (movedOriginal && !rollbackFiles.renameTo(liveFiles)) {
                    throw new IOException("Could not put the original app files back");
                }
            } catch (Exception e) {
                rollbackFailure = e;
            }
            try {
                applyPreferences(context, originalPreferences);
            } catch (Exception e) {
                if (rollbackFailure == null) {
                    rollbackFailure = e;
                } else {
                    rollbackFailure.addSuppressed(e);
                }
            }
            if (rollbackFailure != null) {
                primary.addSuppressed(rollbackFailure);
            }
            throw primary;
        }
    }

    private static Map<String, Map<String, Object>> capturePreferences(Context context) {
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>();
        for (String name : preferenceStoreNames(context)) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry : context
                    .getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<String> source = (Set<String>) value;
                    value = new HashSet<>(source);
                }
                if (entry.getKey() != null && value != null) {
                    values.put(entry.getKey(), value);
                }
            }
            snapshot.put(name, values);
        }
        return snapshot;
    }

    private static void applyPreferences(Context context,
                                         Map<String, Map<String, Object>> snapshot)
            throws IOException {
        Set<String> names = new TreeSet<>(preferenceStoreNames(context));
        names.addAll(snapshot.keySet());
        for (String name : names) {
            SharedPreferences.Editor editor = context
                    .getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear();
            Map<String, Object> values = snapshot.get(name);
            if (values != null) {
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    putPreference(editor, entry.getKey(), entry.getValue());
                }
            }
            if (!editor.commit()) {
                throw new IOException("Could not restore preferences " + name);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void putPreference(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Set) {
            editor.putStringSet(key, new HashSet<>((Set<String>) value));
        } else {
            editor.putString(key, String.valueOf(value));
        }
    }

    private static File safeStageFile(File root, String relative) throws IOException {
        File target = new File(root, relative.replace('/', File.separatorChar));
        String rootPath = root.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        if (!targetPath.startsWith(rootPath + File.separator)) {
            throw new IOException("Backup entry escapes the app data directory");
        }
        return target;
    }

    private static void validateRelativePath(String value) throws IOException {
        if (value == null || value.isEmpty() || value.length() > 1024
                || value.startsWith("/") || value.endsWith("/")
                || value.indexOf('\\') >= 0 || value.indexOf('\u0000') >= 0) {
            throw new IOException("Backup contains an invalid file path");
        }
        for (String part : value.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new IOException("Backup contains an unsafe file path");
            }
        }
    }

    private static boolean validPreferenceStoreName(String value) {
        return value != null && !value.isEmpty() && value.length() <= 200
                && !".".equals(value) && !"..".equals(value)
                && value.indexOf('/') < 0 && value.indexOf('\\') < 0
                && value.indexOf('\u0000') < 0;
    }

    private static List<String> jsonKeys(JSONObject object) {
        List<String> keys = new ArrayList<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        return keys;
    }

    private static byte[] readMetadata(InputStream input, long size) throws IOException {
        if (size < 0L || size > MAX_METADATA_BYTES) {
            throw new IOException("Backup metadata is too large");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) size);
        copyExactly(input, output, size);
        return output.toByteArray();
    }

    private static byte[] readTarBlock(InputStream input) throws IOException {
        byte[] block = new byte[TAR_BLOCK_SIZE];
        int offset = 0;
        while (offset < block.length) {
            int read = input.read(block, offset, block.length - offset);
            if (read < 0) {
                return offset == 0 ? null : throwTruncatedTar();
            }
            offset += read;
        }
        return block;
    }

    private static byte[] throwTruncatedTar() throws EOFException {
        throw new EOFException("Backup archive contains a truncated tar block");
    }

    private static TarEntry parseTarHeader(byte[] header) throws IOException {
        long expectedChecksum = parseOctal(header, 148, 8);
        long checksum = 0L;
        for (int i = 0; i < header.length; i++) {
            checksum += (i >= 148 && i < 156) ? 0x20 : (header[i] & 0xff);
        }
        if (expectedChecksum != checksum) {
            throw new IOException("Backup archive tar checksum is invalid");
        }
        String magic = readTarString(header, 257, 6);
        if (!"ustar".equals(magic)) {
            throw new IOException("Backup archive is not in ustar format");
        }
        String name = readTarString(header, 0, 100);
        String prefix = readTarString(header, 345, 155);
        if (!prefix.isEmpty()) {
            name = prefix + "/" + name;
        }
        if (name.isEmpty()) {
            throw new IOException("Backup archive contains an unnamed entry");
        }
        long size = parseOctal(header, 124, 12);
        long modifiedSeconds = parseOctal(header, 136, 12);
        int type = header[156] & 0xff;
        boolean regular = type == 0 || type == '0';
        boolean directory = type == '5';
        return new TarEntry(name, size, modifiedSeconds * 1000L, regular, directory);
    }

    private static String readTarString(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long parseOctal(byte[] bytes, int offset, int length) throws IOException {
        long value = 0L;
        boolean found = false;
        for (int i = offset; i < offset + length; i++) {
            int c = bytes[i] & 0xff;
            if (c == 0 || c == ' ') {
                if (found) {
                    break;
                }
                continue;
            }
            if (c < '0' || c > '7') {
                throw new IOException("Backup archive contains an invalid tar number");
            }
            found = true;
            if (value > (Long.MAX_VALUE - (c - '0')) / 8L) {
                throw new IOException("Backup archive tar number is too large");
            }
            value = value * 8L + (c - '0');
        }
        return value;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte value : block) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static long tarPadding(long size) {
        long remainder = size % TAR_BLOCK_SIZE;
        return remainder == 0L ? 0L : TAR_BLOCK_SIZE - remainder;
    }

    private static void copyExactly(InputStream input, OutputStream output, long length)
            throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long remaining = length;
        while (remaining > 0L) {
            int wanted = (int) Math.min(buffer.length, remaining);
            int read = input.read(buffer, 0, wanted);
            if (read < 0) {
                throw new EOFException("Backup archive ended inside an entry");
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipExactly(InputStream input, long length) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long remaining = length;
        while (remaining > 0L) {
            int wanted = (int) Math.min(buffer.length, remaining);
            int read = input.read(buffer, 0, wanted);
            if (read < 0) {
                throw new EOFException("Backup archive ended unexpectedly");
            }
            remaining -= read;
        }
    }

    private static void drainGzip(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        while (input.read(buffer) != -1) {
            // Reading through EOF validates the gzip trailer and CRC.
        }
    }

    private static String trimDirectorySuffix(String value) {
        String result = value == null ? "" : value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static void deleteRecursive(File target) throws IOException {
        if (target == null || !target.exists()) {
            return;
        }
        if (!Files.isSymbolicLink(target.toPath()) && target.isDirectory()) {
            File[] children = target.listFiles();
            if (children == null) {
                throw new IOException("Could not inspect " + target.getAbsolutePath());
            }
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        if (!target.delete() && target.exists()) {
            throw new IOException("Could not remove " + target.getAbsolutePath());
        }
    }

    private static void deleteRecursiveQuietly(File target) {
        try {
            deleteRecursive(target);
        } catch (IOException ignored) {
            // A later restore attempt retries cleanup of this exact private
            // rollback directory before making any new state changes.
        }
    }

    private static final class FileEntry {
        final String relativePath;
        final File file;

        FileEntry(String relativePath, File file) {
            this.relativePath = relativePath;
            this.file = file;
        }
    }

    private static final class ExtractedBackup {
        final long createdAtMillis;
        final Map<String, Map<String, Object>> preferences;
        final int fileCount;

        ExtractedBackup(long createdAtMillis,
                        Map<String, Map<String, Object>> preferences, int fileCount) {
            this.createdAtMillis = createdAtMillis;
            this.preferences = preferences;
            this.fileCount = fileCount;
        }
    }

    private static final class TarEntry {
        final String name;
        final long size;
        final long modifiedAtMillis;
        final boolean regularFile;
        final boolean directory;

        TarEntry(String name, long size, long modifiedAtMillis,
                 boolean regularFile, boolean directory) {
            this.name = name;
            this.size = size;
            this.modifiedAtMillis = modifiedAtMillis;
            this.regularFile = regularFile;
            this.directory = directory;
        }
    }

    private static final class TarWriter implements Closeable {
        private final OutputStream output;
        private boolean finished;

        TarWriter(OutputStream output) {
            this.output = output;
        }

        void addBytes(String name, byte[] data, long modifiedAtMillis) throws IOException {
            writeHeader(name, data.length, modifiedAtMillis);
            output.write(data);
            writePadding(data.length);
        }

        void addFile(String name, File file) throws IOException {
            try (FileInputStream input = new FileInputStream(file)) {
                // Open first, then size that exact inode. Several stores use
                // atomic rename, so sizing the path first could pair an old
                // length with a newly replaced file.
                long size = input.getChannel().size();
                writeHeader(name, size, file.lastModified());
                copyExactly(input, output, size);
                writePadding(size);
            }
        }

        void finish() throws IOException {
            if (finished) {
                return;
            }
            output.write(new byte[TAR_BLOCK_SIZE * 2]);
            finished = true;
        }

        @Override
        public void close() throws IOException {
            finish();
        }

        private void writeHeader(String name, long size, long modifiedAtMillis)
                throws IOException {
            if (size < 0L) {
                throw new IOException("Cannot archive a file with an invalid size");
            }
            String[] split = splitTarPath(name);
            byte[] header = new byte[TAR_BLOCK_SIZE];
            putTarString(header, 0, 100, split[1]);
            putOctal(header, 100, 8, 0600L);
            putOctal(header, 108, 8, 0L);
            putOctal(header, 116, 8, 0L);
            putOctal(header, 124, 12, size);
            putOctal(header, 136, 12, Math.max(0L, modifiedAtMillis / 1000L));
            Arrays.fill(header, 148, 156, (byte) ' ');
            header[156] = '0';
            putTarString(header, 257, 6, "ustar");
            putTarString(header, 263, 2, "00");
            putTarString(header, 345, 155, split[0]);
            long checksum = 0L;
            for (byte value : header) {
                checksum += value & 0xff;
            }
            putChecksum(header, checksum);
            output.write(header);
        }

        private void writePadding(long size) throws IOException {
            long padding = tarPadding(size);
            if (padding > 0L) {
                output.write(new byte[(int) padding]);
            }
        }

        private static String[] splitTarPath(String name) throws IOException {
            if (name == null || name.isEmpty() || name.startsWith("/")) {
                throw new IOException("Cannot archive an invalid path");
            }
            if (utf8Length(name) <= 100) {
                return new String[]{"", name};
            }
            for (int slash = name.lastIndexOf('/'); slash > 0;
                 slash = name.lastIndexOf('/', slash - 1)) {
                String prefix = name.substring(0, slash);
                String suffix = name.substring(slash + 1);
                if (utf8Length(prefix) <= 155 && utf8Length(suffix) <= 100) {
                    return new String[]{prefix, suffix};
                }
            }
            throw new IOException("App data path is too long for tar: " + name);
        }

        private static int utf8Length(String value) {
            return value.getBytes(StandardCharsets.UTF_8).length;
        }

        private static void putTarString(byte[] header, int offset, int length, String value)
                throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > length) {
                throw new IOException("Tar header field is too long");
            }
            System.arraycopy(bytes, 0, header, offset, bytes.length);
        }

        private static void putOctal(byte[] header, int offset, int length, long value)
                throws IOException {
            String octal = Long.toOctalString(value);
            if (octal.length() > length - 1) {
                throw new IOException("File is too large for tar");
            }
            Arrays.fill(header, offset, offset + length, (byte) '0');
            int start = offset + length - 1 - octal.length();
            byte[] bytes = octal.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(bytes, 0, header, start, bytes.length);
            header[offset + length - 1] = 0;
        }

        private static void putChecksum(byte[] header, long checksum) throws IOException {
            String octal = Long.toOctalString(checksum);
            if (octal.length() > 6) {
                throw new IOException("Tar checksum overflow");
            }
            Arrays.fill(header, 148, 154, (byte) '0');
            byte[] bytes = octal.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(bytes, 0, header, 154 - bytes.length, bytes.length);
            header[154] = 0;
            header[155] = ' ';
        }
    }
}
