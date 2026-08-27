package com.jpitsg.sysman;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

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
}
