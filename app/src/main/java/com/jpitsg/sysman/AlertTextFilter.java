package com.jpitsg.sysman;

/**
 * The title and message tests shared by the package-based and Remote Link
 * high-priority alerts. Every clause that is filled in has to pass; blank
 * clauses are ignored. A filter with all four clauses blank never matches, so
 * an unconfigured alert source stays silent instead of firing on everything.
 */
final class AlertTextFilter {
    final String titleContains;
    final String titleExcludes;
    final String messageContains;
    final String messageExcludes;

    AlertTextFilter(String titleContains, String titleExcludes, String messageContains, String messageExcludes) {
        this.titleContains = trim(titleContains);
        this.titleExcludes = trim(titleExcludes);
        this.messageContains = trim(messageContains);
        this.messageExcludes = trim(messageExcludes);
    }

    boolean isConfigured() {
        return !titleContains.isEmpty()
                || !titleExcludes.isEmpty()
                || !messageContains.isEmpty()
                || !messageExcludes.isEmpty();
    }

    boolean matches(String title, String message) {
        return rejection(title, message) == null;
    }

    /** Null when the alert passes, otherwise the clause that dropped it, for the log. */
    String rejection(String title, String message) {
        if (!isConfigured()) {
            return "no filter configured";
        }
        String haveTitle = title == null ? "" : title;
        String haveMessage = message == null ? "" : message;
        if (!titleContains.isEmpty() && !haveTitle.contains(titleContains)) {
            return "title does not contain " + quote(titleContains);
        }
        if (!titleExcludes.isEmpty() && haveTitle.contains(titleExcludes)) {
            return "title contains " + quote(titleExcludes);
        }
        if (!messageContains.isEmpty() && !haveMessage.contains(messageContains)) {
            return "message does not contain " + quote(messageContains);
        }
        if (!messageExcludes.isEmpty() && haveMessage.contains(messageExcludes)) {
            return "message contains " + quote(messageExcludes);
        }
        return null;
    }

    String describe() {
        StringBuilder out = new StringBuilder();
        append(out, "title~", titleContains);
        append(out, "title!~", titleExcludes);
        append(out, "message~", messageContains);
        append(out, "message!~", messageExcludes);
        return out.length() == 0 ? "none" : out.toString();
    }

    private static void append(StringBuilder out, String label, String value) {
        if (value.isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append(' ');
        }
        out.append(label).append(quote(value));
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
