package com.jpitsg.sysman;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class LocationHelper {
    private LocationHelper() {
    }

    static LocationData acquireBest(Context context, Config config) {
        if (!hasLocationPermission(context)) {
            LogStore.append(context, "location", "Missing foreground location permission");
            return null;
        }
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            LogStore.append(context, "location", "LocationManager unavailable");
            return null;
        }

        List<String> providers = enabledProviders(manager, config);
        if (providers.isEmpty()) {
            LogStore.append(context, "location", "No configured location providers are enabled");
            return newestAcceptableCachedLocation(context, manager, providers, config);
        }

        LocationData cached = newestAcceptableCachedLocation(context, manager, providers, config);
        if (cached != null && config.useCachedBeforeFresh() && cached.accuracyMeters <= config.desiredAccuracyMeters()) {
            LogStore.append(context, "location", "Using cached location before fresh request: " + cached.summary());
            return cached;
        }

        LocationData fresh = requestFreshLocation(context, manager, providers, config);
        if (fresh != null) {
            return fresh;
        }
        if (cached != null) {
            LogStore.append(context, "location", "Fresh location unavailable; using cached location: " + cached.summary());
            return cached;
        }
        return null;
    }

    private static LocationData requestFreshLocation(Context context, LocationManager manager, List<String> providers, Config config) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Location> best = new AtomicReference<>();
        HandlerThread thread = new HandlerThread("SystemManagerLocation");
        thread.start();

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location == null) {
                    return;
                }
                Location previous = best.get();
                if (previous == null || isBetter(location, previous)) {
                    best.set(location);
                }
                if (location.hasAccuracy() && location.getAccuracy() <= config.desiredAccuracyMeters()) {
                    latch.countDown();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        try {
            for (String provider : providers) {
                manager.requestLocationUpdates(provider, 1000L, 0f, listener, thread.getLooper());
            }
            LogStore.append(context, "location", "Requested fresh location providers=" + providers + " timeout=" + config.locationTimeoutSeconds() + "s");
            latch.await(config.locationTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (SecurityException e) {
            LogStore.append(context, "location", "Location permission denied by OS: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LogStore.append(context, "location", "Location wait interrupted");
        } finally {
            try {
                manager.removeUpdates(listener);
            } catch (RuntimeException ignored) {
            }
            thread.quitSafely();
        }

        Location location = best.get();
        if (location == null) {
            LogStore.append(context, "location", "No fresh location received");
            return null;
        }
        LocationData data = fromLocation(location, "fresh");
        LogStore.append(context, "location", "Fresh location selected: " + data.summary());
        return data;
    }

    private static LocationData newestAcceptableCachedLocation(Context context, LocationManager manager, List<String> providers, Config config) {
        if (config.maxCachedLocationMinutes() <= 0) {
            return null;
        }
        Location best = null;
        for (String provider : providers.isEmpty() ? allProviders(manager) : providers) {
            try {
                Location location = manager.getLastKnownLocation(provider);
                if (location != null && ageMillis(location) <= config.maxCachedLocationMinutes() * 60_000L) {
                    if (best == null || isBetter(location, best)) {
                        best = location;
                    }
                }
            } catch (SecurityException ignored) {
                return null;
            } catch (RuntimeException ignored) {
            }
        }
        return best == null ? null : fromLocation(best, "cached");
    }

    private static List<String> enabledProviders(LocationManager manager, Config config) {
        List<String> providers = new ArrayList<>();
        if (config.useGpsProvider() && isProviderEnabled(manager, LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER);
        }
        if (config.useNetworkProvider() && isProviderEnabled(manager, LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER);
        }
        return providers;
    }

    private static List<String> allProviders(LocationManager manager) {
        try {
            return manager.getProviders(true);
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    private static boolean isProviderEnabled(LocationManager manager, String provider) {
        try {
            return manager.isProviderEnabled(provider);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static LocationData fromLocation(Location location, String source) {
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : -1f;
        return new LocationData(
                location.getLatitude(),
                location.getLongitude(),
                accuracy,
                location.getTime(),
                location.getProvider(),
                source);
    }

    private static boolean isBetter(Location candidate, Location current) {
        boolean candidateHasAccuracy = candidate.hasAccuracy();
        boolean currentHasAccuracy = current.hasAccuracy();
        if (candidateHasAccuracy && currentHasAccuracy) {
            if (candidate.getAccuracy() < current.getAccuracy()) {
                return true;
            }
            return candidate.getAccuracy() == current.getAccuracy() && candidate.getTime() > current.getTime();
        }
        if (candidateHasAccuracy) {
            return true;
        }
        return !currentHasAccuracy && candidate.getTime() > current.getTime();
    }

    private static long ageMillis(Location location) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return Math.max(0L, (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()) / 1_000_000L);
        }
        return Math.max(0L, System.currentTimeMillis() - location.getTime());
    }

    private static boolean hasLocationPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
