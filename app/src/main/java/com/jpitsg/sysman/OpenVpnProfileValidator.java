package com.jpitsg.sysman;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Applies the import policy to a parsed OpenVPN profile: rejects unsafe/managed
 * directives, rewrites certificate references to managed slot files, extracts
 * inline key material, runs structural crypto checks, and emits a normalized
 * profile.conf. Runtime/management flags are added at launch, not here.
 */
final class OpenVpnProfileValidator {
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    // Directives that must be rejected outright.
    private static final Set<String> REJECT = new HashSet<>(Arrays.asList(
            "secret", "plugin", "daemon", "inetd", "log", "log-append", "syslog",
            "dev-node", "user", "group", "chroot",
            "up", "down", "up-restart", "route-up", "route-pre-down", "ipchange",
            "client-connect", "client-disconnect", "learn-address",
            "auth-user-pass-verify", "tls-verify", "tls-export-cert"));

    // Certificate/key directives rewritten to managed slot files.
    private static final Set<String> REWRITE_SLOTS = new HashSet<>(Arrays.asList(
            "ca", "cert", "key", "tls-auth", "tls-crypt", "tls-crypt-v2",
            "pkcs12", "crl-verify", "extra-certs"));

    // Directives kept verbatim but flagged as deprecated/inert.
    private static final Set<String> WARN_KEEP = new HashSet<>(Arrays.asList(
            "comp-lzo", "compress", "ncp-ciphers", "keysize", "no-replay", "tls-remote",
            "route-method", "win-sys", "block-outside-dns", "service", "explicit-exit-notify"));

    private OpenVpnProfileValidator() {
    }

    static OpenVpnValidationResult validate(Context context, String text) {
        OpenVpnValidationResult result = new OpenVpnValidationResult();
        List<OpenVpnConfigParser.Directive> directives;
        try {
            directives = OpenVpnConfigParser.parse(text);
        } catch (OpenVpnConfigParser.ParseException e) {
            result.error(e.getMessage());
            return result;
        }

        StringBuilder conf = new StringBuilder();
        conf.append("# Managed by System Manager - imported ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()))
                .append('\n');
        conf.append("cd ").append(OpenVpnProfileStore.dir(context).getAbsolutePath()).append('\n');

        boolean hasRemote = false;
        boolean hasDev = false;
        boolean clientMode = false;
        boolean serverMode = false;
        boolean hasDataCiphers = false;
        boolean hasCipher = false;
        boolean hasKeepalive = false;
        int tlsKeyVariants = 0;
        String globalProto = "";

        for (OpenVpnConfigParser.Directive d : directives) {
            String name = d.name;

            // Inline blocks.
            if (d.inlineBody != null) {
                handleInlineBlock(result, conf, d);
                if ("tls-auth".equals(name) || "tls-crypt".equals(name) || "tls-crypt-v2".equals(name)) {
                    tlsKeyVariants++;
                }
                continue;
            }

            if ("remote".equals(name)) {
                hasRemote = true;
                if (result.remoteHost.isEmpty()) {
                    result.remoteHost = d.arg(0);
                    result.remotePort = parseIntOr(d.arg(1), 1194);
                    if (!d.arg(2).isEmpty()) {
                        result.remoteProto = normalizeProto(d.arg(2));
                    }
                }
                emitVerbatim(conf, d);
                continue;
            }
            if ("proto".equals(name)) {
                globalProto = normalizeProto(d.arg(0));
                emitVerbatim(conf, d);
                continue;
            }
            if ("dev".equals(name) || "dev-type".equals(name)) {
                String devName = d.arg(0).toLowerCase(Locale.US);
                if (devName.startsWith("tap")) {
                    result.devType = "tap";
                } else if (devName.startsWith("tun")) {
                    result.devType = "tun";
                } else if (devName.equals("null")) {
                    result.error("dev null is not supported (line " + d.line + ")");
                }
                if ("dev".equals(name)) {
                    hasDev = true;
                }
                emitVerbatim(conf, d);
                continue;
            }
            if ("client".equals(name) || "tls-client".equals(name) || "pull".equals(name)) {
                clientMode = true;
                emitVerbatim(conf, d);
                continue;
            }
            if ("mode".equals(name) && "server".equalsIgnoreCase(d.arg(0))) {
                serverMode = true;
            }
            if ("server".equals(name) || "server-bridge".equals(name)) {
                serverMode = true;
            }

            // REJECT.
            if (REJECT.contains(name)) {
                result.error(rejectMessage(name) + " (line " + d.line + ")");
                continue;
            }
            if (name.startsWith("management")) {
                result.error("the app manages the management channel; remove '" + name + "' (line " + d.line + ")");
                continue;
            }
            if ("script-security".equals(name) && parseIntOr(d.arg(0), 0) >= 2) {
                result.error("script-security >= 2 is not allowed (line " + d.line + ")");
                continue;
            }
            if ("capath".equals(name)) {
                result.error("capath is not importable; bundle a ca file instead (line " + d.line + ")");
                continue;
            }
            if ("askpass".equals(name) && !d.arg(0).isEmpty()) {
                result.warn("askpass file stripped; the app supplies the passphrase");
                continue;
            }
            if ("auth-retry".equals(name)) {
                // Dropped so it can't loop on bad credentials; the launch args
                // pin auth-retry to none.
                if (!"none".equalsIgnoreCase(d.arg(0))) {
                    result.warn("auth-retry " + d.arg(0) + " removed; the app supplies credentials once");
                }
                continue;
            }
            if ("keepalive".equals(name) || "ping".equals(name) || "ping-restart".equals(name)
                    || "ping-exit".equals(name)) {
                hasKeepalive = true;
                emitVerbatim(conf, d);
                continue;
            }
            if ("key-direction".equals(name)) {
                result.keyDirection = parseIntOr(d.arg(0), result.keyDirection);
                emitVerbatim(conf, d);
                continue;
            }
            if ("data-ciphers".equals(name)) {
                hasDataCiphers = true;
                if (result.cipherSummary.isEmpty()) {
                    result.cipherSummary = d.arg(0);
                }
                emitVerbatim(conf, d);
                continue;
            }
            if ("cipher".equals(name)) {
                hasCipher = true;
                if (result.cipherSummary.isEmpty()) {
                    result.cipherSummary = d.arg(0);
                }
                emitVerbatim(conf, d);
                continue;
            }

            // REWRITE (file-reference form).
            if (REWRITE_SLOTS.contains(name)) {
                handleFileSlot(result, conf, d);
                if ("tls-auth".equals(name) || "tls-crypt".equals(name) || "tls-crypt-v2".equals(name)) {
                    tlsKeyVariants++;
                }
                continue;
            }
            if ("auth-user-pass".equals(name)) {
                result.authUserPass = true;
                conf.append("auth-user-pass\n"); // strip any file arg; creds via management
                continue;
            }

            // WARN + keep.
            if (WARN_KEEP.contains(name)) {
                result.warn(warnMessage(name));
                emitVerbatim(conf, d);
                continue;
            }

            // PASSTHROUGH.
            emitVerbatim(conf, d);
            if (!isSilentPassthrough(name)) {
                result.warn("passed through: " + name);
            }
        }

        if (result.cipherSummary.isEmpty()) {
            result.cipherSummary = "default ciphers";
        }
        if (tlsKeyVariants > 1) {
            result.error("only one of tls-auth / tls-crypt / tls-crypt-v2 may be used");
        }
        if (!hasRemote) {
            result.error("no remote server configured");
        }
        if (!hasDev) {
            result.error("no dev (tun/tap) configured");
        }
        if (!clientMode) {
            result.error("not a client configuration (missing client/tls-client)");
        }
        if (serverMode) {
            result.error("this is a server configuration, not a client profile");
        }
        if (result.remoteProto.equals("udp") && !globalProto.isEmpty()) {
            result.remoteProto = globalProto;
        }
        boolean hasCa = result.requiredSlots.contains("ca") || result.satisfiedSlots.containsKey("ca");
        boolean hasP12 = result.requiredSlots.contains("pkcs12") || result.satisfiedSlots.containsKey("pkcs12");
        if (!hasCa && !hasP12) {
            result.warn("no CA configured; server verification is weakened");
        }
        if (hasCipher && !hasDataCiphers) {
            result.warn("cipher without data-ciphers; negotiation may override it");
        }
        if (!hasKeepalive) {
            // No dead-peer detection in the profile (and the server may not push
            // any). Inject a sane default so a silently-dropped link is detected
            // and openvpn triggers its own reconnect. A server push overrides it.
            conf.append("ping 10\n").append("ping-restart 60\n");
            result.warn("no keepalive/ping-restart; added ping 10 / ping-restart 60 for dead-peer detection");
        }

        result.normalizedConf = conf.toString();
        return result;
    }

    private static void handleInlineBlock(OpenVpnValidationResult result, StringBuilder conf,
                                          OpenVpnConfigParser.Directive d) {
        String tag = d.name;
        if ("secret".equals(tag)) {
            result.error("secret/static-key mode was removed in OpenVPN 2.7 (line " + d.line + ")");
            return;
        }
        if (!REWRITE_SLOTS.contains(tag)) {
            // e.g. dh (unused by clients) or http-proxy-user-pass — keep verbatim.
            conf.append('<').append(tag).append(">\n").append(d.inlineBody);
            if (!d.inlineBody.endsWith("\n")) {
                conf.append('\n');
            }
            conf.append("</").append(tag).append(">\n");
            return;
        }
        byte[] bytes;
        if ("pkcs12".equals(tag)) {
            bytes = decodeBase64(d.inlineBody);
        } else {
            bytes = d.inlineBody.getBytes(StandardCharsets.UTF_8);
        }
        result.inlineSlotBytes.put(tag, bytes);
        result.satisfiedSlots.put(tag, "inline");
        result.requireSlot(tag);
        checkSlotMaterial(result, tag, bytes);
        conf.append(tag).append(' ').append(OpenVpnProfileStore.slotFileName(tag)).append('\n');
    }

    private static void handleFileSlot(OpenVpnValidationResult result, StringBuilder conf,
                                       OpenVpnConfigParser.Directive d) {
        String tag = d.name;
        if ("crl-verify".equals(tag) && "dir".equalsIgnoreCase(d.arg(1))) {
            result.error("crl-verify directory form is not supported (line " + d.line + ")");
            return;
        }
        result.requireSlot(tag);
        StringBuilder line = new StringBuilder(tag).append(' ').append(OpenVpnProfileStore.slotFileName(tag));
        if ("tls-auth".equals(tag) && !d.arg(1).isEmpty()) {
            line.append(' ').append(d.arg(1));
            result.keyDirection = parseIntOr(d.arg(1), result.keyDirection);
        }
        conf.append(line).append('\n');
    }

    /** Structural crypto check for inline or freshly-imported slot bytes. */
    static void checkSlotMaterial(OpenVpnValidationResult result, String slot, byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        switch (slot) {
            case "ca":
            case "cert":
            case "extra-certs": {
                X509Certificate cert = parseFirstCert(bytes);
                if (cert == null) {
                    result.error(slot + ": could not parse an X.509 certificate");
                    return;
                }
                long notAfter = cert.getNotAfter().getTime();
                if ("cert".equals(slot)) {
                    result.certSubject = cert.getSubjectX500Principal().getName();
                    result.certNotAfterMillis = notAfter;
                } else if ("ca".equals(slot)) {
                    result.caNotAfterMillis = notAfter;
                }
                long now = System.currentTimeMillis();
                if (notAfter < now) {
                    result.warn(slot + " certificate expired " + formatDate(notAfter));
                } else if (notAfter - now < THIRTY_DAYS_MS) {
                    result.warn(slot + " certificate expires soon (" + formatDate(notAfter) + ")");
                }
                break;
            }
            case "key": {
                if (!content.contains("PRIVATE KEY")) {
                    result.error("key: no PEM private key found");
                    return;
                }
                if (content.contains("Proc-Type: 4,ENCRYPTED") || content.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
                    result.keyEncrypted = true;
                }
                break;
            }
            case "tls-auth":
            case "tls-crypt": {
                if (!content.contains("OpenVPN Static key V1")) {
                    result.error(slot + ": not a valid OpenVPN static key");
                }
                break;
            }
            case "pkcs12": {
                if (bytes.length == 0 || (bytes[0] & 0xFF) != 0x30) {
                    result.error("pkcs12: not a valid PKCS#12 (DER) bundle");
                }
                break;
            }
            default:
                break;
        }
    }

    private static X509Certificate parseFirstCert(byte[] bytes) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Collection<? extends java.security.cert.Certificate> certs =
                    factory.generateCertificates(new ByteArrayInputStream(bytes));
            for (java.security.cert.Certificate c : certs) {
                if (c instanceof X509Certificate) {
                    return (X509Certificate) c;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static byte[] decodeBase64(String body) {
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (t.startsWith("-----") || t.isEmpty()) {
                continue;
            }
            sb.append(t);
        }
        try {
            return android.util.Base64.decode(sb.toString(), android.util.Base64.DEFAULT);
        } catch (Exception e) {
            return body.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static void emitVerbatim(StringBuilder conf, OpenVpnConfigParser.Directive d) {
        conf.append(d.name);
        for (String arg : d.args) {
            conf.append(' ').append(quoteIfNeeded(arg));
        }
        conf.append('\n');
    }

    private static String quoteIfNeeded(String arg) {
        if (arg.isEmpty() || arg.indexOf(' ') >= 0 || arg.indexOf('\t') >= 0 || arg.indexOf('"') >= 0) {
            return '"' + arg.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
        return arg;
    }

    private static String normalizeProto(String proto) {
        String p = proto.toLowerCase(Locale.US);
        if (p.startsWith("tcp")) {
            return "tcp";
        }
        if (p.startsWith("udp")) {
            return "udp";
        }
        return p;
    }

    private static boolean isSilentPassthrough(String name) {
        // Common, unremarkable directives that should not generate a warning.
        switch (name) {
            case "proto":
            case "port":
            case "nobind":
            case "persist-key":
            case "persist-tun":
            case "resolv-retry":
            case "remote-cert-tls":
            case "remote-cert-eku":
            case "verify-x509-name":
            case "auth":
            case "auth-nocache":
            case "tls-version-min":
            case "tls-version-max":
            case "verb":
            case "mute":
            case "reneg-sec":
            case "connect-retry":
            case "connect-retry-max":
            case "float":
            case "fast-io":
            case "pull-filter":
            case "route":
            case "route-nopull":
            case "redirect-gateway":
            case "redirect-private":
            case "ping":
            case "ping-restart":
            case "ping-exit":
            case "mssfix":
            case "tun-mtu":
            case "sndbuf":
            case "rcvbuf":
                return true;
            default:
                return false;
        }
    }

    private static String rejectMessage(String name) {
        switch (name) {
            case "secret":
                return "secret/static-key mode was removed in OpenVPN 2.7";
            case "plugin":
                return "plugins are not supported";
            case "daemon":
            case "inetd":
                return "daemonizing is controlled by the app; remove '" + name + "'";
            case "log":
            case "log-append":
            case "syslog":
                return "logging is controlled by the app; remove '" + name + "'";
            case "user":
            case "group":
            case "chroot":
            case "dev-node":
                return "'" + name + "' is not usable on Android";
            default:
                return "'" + name + "' (a script hook) is not allowed";
        }
    }

    private static String warnMessage(String name) {
        switch (name) {
            case "comp-lzo":
            case "compress":
                return name + ": OpenVPN 2.7 only decompresses; consider removing";
            case "ncp-ciphers":
                return "ncp-ciphers is a deprecated alias of data-ciphers";
            case "tls-remote":
                return "tls-remote is deprecated; use verify-x509-name";
            default:
                return name + ": deprecated or platform-specific; kept as-is";
        }
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String formatDate(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    }
}
