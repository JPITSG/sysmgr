package com.jpitsg.sysman;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only, URI-grant-only access to the APK downloaded for installation. */
public final class UpgradeApkProvider extends ContentProvider {
    static final String AUTHORITY = "com.jpitsg.sysman.upgradeapk";
    static final String MIME_TYPE = "application/vnd.android.package-archive";
    private static final String DIRECTORY = "upgrade";
    private static final String FILE_NAME = "SystemManager-upgrade.apk";

    static File apkFile(Context context) {
        return new File(new File(context.getCacheDir(), DIRECTORY), FILE_NAME);
    }

    static Uri apkUri() {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(FILE_NAME)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Upgrade APK is read-only");
        }
        File file = resolve(uri);
        if (file == null || !file.isFile()) {
            throw new FileNotFoundException("Upgrade APK is unavailable");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return resolve(uri) == null ? null : MIME_TYPE;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = resolve(uri);
        if (file == null || !file.isFile()) {
            return null;
        }
        String[] columns = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int index = 0; index < columns.length; index += 1) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
                row[index] = FILE_NAME;
            } else if (OpenableColumns.SIZE.equals(columns[index])) {
                row[index] = file.length();
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
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }

    private File resolve(Uri uri) {
        if (getContext() == null || uri == null
                || !"content".equals(uri.getScheme())
                || !AUTHORITY.equals(uri.getAuthority())
                || uri.getPathSegments().size() != 1
                || !FILE_NAME.equals(uri.getLastPathSegment())) {
            return null;
        }
        return apkFile(getContext());
    }
}
