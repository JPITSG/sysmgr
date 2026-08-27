package com.jpitsg.sysman;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Resolves the version declared by the currently installed application. */
final class AppVersion {
    private AppVersion() {
    }

    static String name(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            String versionName = info.versionName;
            if (versionName != null && !versionName.trim().isEmpty()) {
                return versionName.trim();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return "Unknown";
    }

    static long code(Context context) {
        try {
            return code(context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0));
        } catch (PackageManager.NameNotFoundException ignored) {
            return 0L;
        }
    }

    @SuppressWarnings("deprecation")
    static long code(PackageInfo info) {
        if (info == null) {
            return 0L;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode()
                : info.versionCode;
    }
}
