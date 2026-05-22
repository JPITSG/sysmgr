package com.jpitsg.sysman;

import java.util.regex.Pattern;

final class PatternMatcher {
    private PatternMatcher() {
    }

    static boolean simpleMatch(String pattern, String value, boolean caseSensitive) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return value == null || value.isEmpty();
        }
        if (value == null) {
            value = "";
        }
        StringBuilder regex = new StringBuilder();
        String source = pattern.trim();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile("^" + regex + "$", flags).matcher(value).matches();
    }
}
