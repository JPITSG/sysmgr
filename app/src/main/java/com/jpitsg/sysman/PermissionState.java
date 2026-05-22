package com.jpitsg.sysman;

import android.Manifest;
import android.content.ComponentName;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

final class PermissionState {
    private PermissionState() {
    }

    static boolean hasFineLocation(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasCoarseLocation(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasBackgroundLocation(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return hasFineLocation(context) || hasCoarseLocation(context);
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasNearbyWifi(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean locationServicesEnabled(Context context) {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return manager.isLocationEnabled();
        }
        try {
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean notificationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return true;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager == null || manager.areNotificationsEnabled();
    }

    static boolean notificationListenerEnabled(Context context) {
        ComponentName component = new ComponentName(context, HighPriorityNotificationListener.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager != null && manager.isNotificationListenerAccessGranted(component);
        }

        String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(component.flattenToString());
    }

    static boolean accessibilityServiceEnabled(Context context) {
        ComponentName component = new ComponentName(context, SystemManagerAccessibilityService.class);
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        String flattened = component.flattenToString();
        String shortFlattened = component.flattenToShortString();
        String[] services = enabled.split(":");
        for (String service : services) {
            if (flattened.equalsIgnoreCase(service) || shortFlattened.equalsIgnoreCase(service)) {
                return true;
            }
        }
        return false;
    }

    static boolean ignoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return manager != null && manager.isIgnoringBatteryOptimizations(context.getPackageName());
    }
}
