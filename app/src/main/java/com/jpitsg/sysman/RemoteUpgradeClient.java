package com.jpitsg.sysman;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Downloads the configured APK over the authenticated Remote Link endpoint. */
final class RemoteUpgradeClient {
    private static final long MAX_APK_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 64 * 1024;

    private RemoteUpgradeClient() {
    }

    static File download(Context context) throws Exception {
        Context app = context.getApplicationContext();
        HttpURLConnection connection = RemoteBackupClient.open(
                app, "GET", "/upgrade.apk", UpgradeApkProvider.MIME_TYPE + ", text/plain");
        File target = UpgradeApkProvider.apkFile(app);
        File directory = target.getParentFile();
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) {
            connection.disconnect();
            throw new IOException("Could not prepare the upgrade download directory");
        }
        File temporary = new File(directory, target.getName() + ".download");
        boolean moved = false;
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                    UpgradeStateStore.setServerState(app, true, false, 0L, 0L);
                } else if (status == HttpURLConnection.HTTP_UNAVAILABLE) {
                    UpgradeStateStore.setServerState(app, false, false, 0L, 0L);
                }
                throw RemoteBackupClient.responseError(connection, status,
                        status == HttpURLConnection.HTTP_NOT_FOUND
                                ? "Upgrade APK is not available" : "Upgrade download failed");
            }

            long expectedBytes = connection.getContentLengthLong();
            if (expectedBytes == 0L || expectedBytes > MAX_APK_BYTES) {
                throw new IOException("Upgrade APK size is invalid");
            }
            long copied;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary, false)) {
                copied = copy(input, output);
                output.flush();
                output.getFD().sync();
            }
            if (copied < 1L || (expectedBytes >= 0L && copied != expectedBytes)) {
                throw new IOException("Upgrade APK download was incomplete");
            }
            try {
                Files.move(temporary.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            long modifiedAt = connection.getLastModified();
            if (modifiedAt > 0L) {
                //noinspection ResultOfMethodCallIgnored
                target.setLastModified(modifiedAt);
            }
            UpgradeStateStore.setServerState(app, true, true, copied,
                    modifiedAt > 0L ? modifiedAt : System.currentTimeMillis());
            return target;
        } finally {
            connection.disconnect();
            if (!moved) {
                //noinspection ResultOfMethodCallIgnored
                temporary.delete();
            }
        }
    }

    private static long copy(InputStream input, FileOutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_APK_BYTES) {
                throw new IOException("Upgrade APK is too large");
            }
            output.write(buffer, 0, read);
        }
        return total;
    }
}
