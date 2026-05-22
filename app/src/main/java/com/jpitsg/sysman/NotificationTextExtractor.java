package com.jpitsg.sysman;

import android.app.Notification;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;

import java.util.LinkedHashSet;
import java.util.List;

final class NotificationTextExtractor {
    private NotificationTextExtractor() {
    }

    static NotificationPayload extract(StatusBarNotification sbn) {
        Notification notification = sbn == null ? null : sbn.getNotification();
        if (notification == null) {
            return new NotificationPayload("", "");
        }

        Bundle extras = notification.extras;
        LinkedHashSet<String> titleParts = new LinkedHashSet<>();
        LinkedHashSet<String> textParts = new LinkedHashSet<>();

        if (extras != null) {
            add(titleParts, charSequence(extras, Notification.EXTRA_TITLE));
            add(titleParts, charSequence(extras, Notification.EXTRA_TITLE_BIG));

            add(textParts, charSequence(extras, Notification.EXTRA_TEXT));
            add(textParts, charSequence(extras, Notification.EXTRA_BIG_TEXT));
            add(textParts, charSequence(extras, Notification.EXTRA_SUB_TEXT));
            add(textParts, charSequence(extras, Notification.EXTRA_SUMMARY_TEXT));
            add(textParts, notification.tickerText);
            addArray(textParts, charSequenceArray(extras, Notification.EXTRA_TEXT_LINES));
            addMessages(textParts, extras, Notification.EXTRA_MESSAGES);
            addMessages(textParts, extras, Notification.EXTRA_HISTORIC_MESSAGES);
        }

        return new NotificationPayload(join(titleParts), join(textParts));
    }

    private static void addMessages(LinkedHashSet<String> parts, Bundle extras, String key) {
        Parcelable[] bundles;
        try {
            bundles = extras.getParcelableArray(key);
        } catch (RuntimeException e) {
            return;
        }
        if (bundles == null || bundles.length == 0) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                List<Notification.MessagingStyle.Message> messages =
                        Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);
                for (Notification.MessagingStyle.Message message : messages) {
                    add(parts, message.getText());
                }
                return;
            } catch (RuntimeException ignored) {
                // Fall through to the bundle-key path used on older Android releases.
            }
        }

        for (Parcelable parcelable : bundles) {
            if (parcelable instanceof Bundle) {
                add(parts, ((Bundle) parcelable).getCharSequence("text"));
            }
        }
    }

    private static CharSequence charSequence(Bundle extras, String key) {
        try {
            return extras.getCharSequence(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static CharSequence[] charSequenceArray(Bundle extras, String key) {
        try {
            return extras.getCharSequenceArray(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void addArray(LinkedHashSet<String> parts, CharSequence[] values) {
        if (values == null) {
            return;
        }
        for (CharSequence value : values) {
            add(parts, value);
        }
    }

    private static void add(LinkedHashSet<String> parts, CharSequence value) {
        if (value == null) {
            return;
        }
        String normalized = value.toString().replace('\n', ' ').replace('\r', ' ').trim();
        if (!normalized.isEmpty()) {
            parts.add(normalized);
        }
    }

    private static String join(LinkedHashSet<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(part);
        }
        return builder.toString();
    }
}
