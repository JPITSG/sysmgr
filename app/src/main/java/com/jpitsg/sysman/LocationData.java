package com.jpitsg.sysman;

final class LocationData {
    final double latitude;
    final double longitude;
    final float accuracyMeters;
    final long timeMillis;
    final String provider;
    final String source;

    LocationData(double latitude, double longitude, float accuracyMeters, long timeMillis, String provider, String source) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.timeMillis = timeMillis;
        this.provider = provider == null ? "" : provider;
        this.source = source == null ? "" : source;
    }

    String summary() {
        return latitude + "," + longitude + " acc=" + accuracyMeters + "m provider=" + provider + " source=" + source;
    }
}
