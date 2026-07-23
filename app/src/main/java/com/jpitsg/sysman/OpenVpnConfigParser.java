package com.jpitsg.sysman;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizes an OpenVPN config into directives, honouring quotes/backslash
 * escapes and collecting inline &lt;tag&gt;...&lt;/tag&gt; blocks. Throws
 * {@link ParseException} on structural errors (unterminated quote/block).
 */
final class OpenVpnConfigParser {

    static final class ParseException extends Exception {
        ParseException(String message) {
            super(message);
        }
    }

    static final class Directive {
        final String name;              // lowercased
        final List<String> args;
        final String inlineBody;        // verbatim body for <tag> blocks, else null
        final int line;

        Directive(String name, List<String> args, String inlineBody, int line) {
            this.name = name;
            this.args = args;
            this.inlineBody = inlineBody;
            this.line = line;
        }

        String arg(int index) {
            return index < args.size() ? args.get(index) : "";
        }
    }

    private static final java.util.Set<String> INLINE_TAGS = new java.util.HashSet<>(java.util.Arrays.asList(
            "ca", "cert", "key", "tls-auth", "tls-crypt", "tls-crypt-v2",
            "pkcs12", "crl-verify", "extra-certs", "dh", "secret", "http-proxy-user-pass"));

    private OpenVpnConfigParser() {
    }

    static List<Directive> parse(String text) throws ParseException {
        List<Directive> directives = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String raw = stripComment(lines[i]);
            int lineNumber = i + 1;
            i++;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("<") && !trimmed.startsWith("</")) {
                if (trimmed.length() < 3 || !trimmed.endsWith(">")) {
                    throw new ParseException("malformed block open at line " + lineNumber);
                }
                String tag = trimmed.substring(1, trimmed.length() - 1).trim().toLowerCase(java.util.Locale.US);
                StringBuilder body = new StringBuilder();
                boolean closed = false;
                while (i < lines.length) {
                    String bodyLine = lines[i];
                    i++;
                    if (bodyLine.trim().equalsIgnoreCase("</" + tag + ">")) {
                        closed = true;
                        break;
                    }
                    body.append(bodyLine).append('\n');
                }
                if (!closed) {
                    throw new ParseException("unterminated <" + tag + "> block (line " + lineNumber + ")");
                }
                directives.add(new Directive(tag, new ArrayList<String>(), body.toString(), lineNumber));
                continue;
            }
            List<String> tokens = tokenize(trimmed, lineNumber);
            if (tokens.isEmpty()) {
                continue;
            }
            String name = tokens.remove(0).toLowerCase(java.util.Locale.US);
            directives.add(new Directive(name, tokens, null, lineNumber));
        }
        return directives;
    }

    static boolean isInlineTag(String tag) {
        return INLINE_TAGS.contains(tag);
    }

    private static String stripComment(String line) {
        // A leading # or ; comments the whole line; inline comments are not
        // stripped mid-line to avoid mangling quoted values.
        String trimmed = line.trim();
        if (trimmed.startsWith("#") || trimmed.startsWith(";")) {
            return "";
        }
        return line;
    }

    private static List<String> tokenize(String line, int lineNumber) throws ParseException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inToken = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < line.length()) {
                    char next = line.charAt(i + 1);
                    if (next == quote || next == '\\') {
                        current.append(next);
                        i++;
                        continue;
                    }
                    current.append(c);
                } else if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                inToken = true;
                continue;
            }
            if (c == '\\' && i + 1 < line.length()) {
                current.append(line.charAt(i + 1));
                i++;
                inToken = true;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
                continue;
            }
            current.append(c);
            inToken = true;
        }
        if (quote != 0) {
            throw new ParseException("unterminated quote at line " + lineNumber);
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
