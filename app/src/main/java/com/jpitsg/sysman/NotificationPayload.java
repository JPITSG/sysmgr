package com.jpitsg.sysman;

final class NotificationPayload {
    final String title;
    final String text;

    NotificationPayload(String title, String text) {
        this.title = title == null ? "" : title;
        this.text = text == null ? "" : text;
    }

    boolean textContains(String needle) {
        return needle != null && !needle.isEmpty() && text.contains(needle);
    }

    String shortTitle() {
        return shorten(title, 80);
    }

    String shortText() {
        return shorten(text, 140);
    }

    private static String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
