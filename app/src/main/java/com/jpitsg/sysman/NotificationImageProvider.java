package com.jpitsg.sysman;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * Minimal read-only content provider that exposes stored notification-history
 * images so they can be shared with other apps (the app has no androidx
 * FileProvider). Serves only well-formed notification image files.
 */
public final class NotificationImageProvider extends ContentProvider {
    static final String AUTHORITY = "com.jpitsg.sysman.notificationimages";
    private static final String IMAGE_DIR = "notification-history-images";

    static Uri uriFor(String imageFileName) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(imageFileName)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolve(uri);
        if (file == null || !file.exists()) {
            throw new FileNotFoundException("No such image");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return mimeOf(resolve(uri));
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = resolve(uri);
        if (file == null || !file.exists()) {
            return null;
        }
        String[] columns = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = file.length();
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File resolve(Uri uri) {
        if (getContext() == null || uri == null) {
            return null;
        }
        String name = uri.getLastPathSegment();
        // Prevent path traversal; only serve well-formed notification image names.
        if (name == null || name.contains("/") || name.contains("\\") || !name.startsWith("notification-")) {
            return null;
        }
        return new File(new File(getContext().getFilesDir(), IMAGE_DIR), name);
    }

    /** Sniffs a concrete image MIME type from the file header; falls back to image/*. */
    static String mimeOf(File file) {
        if (file == null || !file.exists()) {
            return "image/*";
        }
        byte[] header = new byte[12];
        int read;
        try (FileInputStream in = new FileInputStream(file)) {
            read = in.read(header);
        } catch (Exception e) {
            return "image/*";
        }
        if (read < 4) {
            return "image/*";
        }
        int b0 = header[0] & 0xFF, b1 = header[1] & 0xFF, b2 = header[2] & 0xFF, b3 = header[3] & 0xFF;
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
            return "image/png";
        }
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
            return "image/jpeg";
        }
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46) {
            return "image/gif";
        }
        if (read >= 12 && b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46
                && (header[8] & 0xFF) == 0x57 && (header[9] & 0xFF) == 0x45
                && (header[10] & 0xFF) == 0x42 && (header[11] & 0xFF) == 0x50) {
            return "image/webp";
        }
        return "image/*";
    }
}
