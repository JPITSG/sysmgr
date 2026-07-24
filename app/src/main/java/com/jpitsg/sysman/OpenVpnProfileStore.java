package com.jpitsg.sysman;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * On-disk store for the single OpenVPN profile under filesDir/openvpn/. Holds
 * the normalized profile.conf, the imported certificate/key files, and meta.json
 * (written last, acting as the commit marker). All key material lives as files
 * here — never in SharedPreferences — so it is excluded from Settings XML export.
 *
 * Atomic writes mirror NotificationHistoryStore (temp + fsync + rename).
 */
final class OpenVpnProfileStore {
    private static final Object LOCK = new Object();
    private static final String DIR = "openvpn";
    static final String PROFILE_CONF = "profile.conf";
    static final String ORIGINAL_CONF = "original.conf";
    private static final String META = "meta.json";

    // slot id -> on-disk file name
    private static final Map<String, String> SLOT_FILES = new LinkedHashMap<>();

    static {
        SLOT_FILES.put("ca", "ca.crt");
        SLOT_FILES.put("cert", "client.crt");
        SLOT_FILES.put("key", "client.key");
        SLOT_FILES.put("tls-auth", "ta.key");
        SLOT_FILES.put("tls-crypt", "tc.key");
        SLOT_FILES.put("tls-crypt-v2", "tcv2.key");
        SLOT_FILES.put("pkcs12", "client.p12");
        SLOT_FILES.put("crl-verify", "crl.pem");
        SLOT_FILES.put("extra-certs", "extra.crt");
    }

    private OpenVpnProfileStore() {
    }

    static File dir(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    static File profileConf(Context context) {
        return new File(dir(context), PROFILE_CONF);
    }

    static File slotFile(Context context, String slotId) {
        String name = SLOT_FILES.get(slotId);
        return name == null ? null : new File(dir(context), name);
    }

    /** The effective (normalized) profile.conf text, for the in-app editor. */
    static String readProfileText(Context context) {
        synchronized (LOCK) {
            File file = profileConf(context);
            if (!file.exists()) {
                return "";
            }
            try {
                return new String(readFile(file), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "";
            }
        }
    }

    /** Overwrites profile.conf with hand-edited text (atomic); slots/meta untouched. */
    static void writeProfileText(Context context, String text) {
        synchronized (LOCK) {
            writeAtomically(context, profileConf(context), text.getBytes(StandardCharsets.UTF_8));
        }
    }

    static String slotFileName(String slotId) {
        return SLOT_FILES.get(slotId);
    }

    static boolean hasProfile(Context context) {
        synchronized (LOCK) {
            File meta = new File(dir(context), META);
            return meta.exists() && meta.length() > 0;
        }
    }

    static Meta readMeta(Context context) {
        synchronized (LOCK) {
            File meta = new File(dir(context), META);
            if (!meta.exists()) {
                return new Meta();
            }
            try {
                byte[] bytes = readFile(meta);
                return Meta.fromJson(new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
            } catch (Exception e) {
                return new Meta();
            }
        }
    }

    /**
     * Writes the profile: extracted inline slot files, original.conf,
     * profile.conf, then meta.json. Removes prior slot files no longer used.
     */
    static void commitProfile(Context context, OpenVpnValidationResult result, byte[] originalBytes) {
        synchronized (LOCK) {
            File dir = dir(context);
            // Remove slot files not satisfied by this profile (stale keys must not linger).
            for (Map.Entry<String, String> entry : SLOT_FILES.entrySet()) {
                if (!result.satisfiedSlots.containsKey(entry.getKey())) {
                    File f = new File(dir, entry.getValue());
                    if (f.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            }
            // Extract inline slot bodies.
            for (Map.Entry<String, byte[]> entry : result.inlineSlotBytes.entrySet()) {
                File slot = slotFile(context, entry.getKey());
                if (slot != null) {
                    writeAtomically(context, slot, entry.getValue());
                }
            }
            if (originalBytes != null) {
                writeAtomically(context, new File(dir, ORIGINAL_CONF), originalBytes);
            }
            writeAtomically(context, new File(dir, PROFILE_CONF),
                    result.normalizedConf.getBytes(StandardCharsets.UTF_8));

            Meta meta = new Meta();
            meta.devType = result.devType;
            meta.remoteHost = result.remoteHost;
            meta.remotePort = result.remotePort;
            meta.remoteProto = result.remoteProto;
            meta.cipherSummary = result.cipherSummary;
            meta.authUserPass = result.authUserPass;
            meta.keyEncrypted = result.keyEncrypted;
            meta.keyDirection = result.keyDirection;
            meta.requiredSlots = new ArrayList<>(result.requiredSlots);
            meta.satisfiedSlots = new LinkedHashMap<>(result.satisfiedSlots);
            meta.warnings = new ArrayList<>(result.warnings);
            meta.certSubject = result.certSubject;
            meta.certNotAfterMillis = result.certNotAfterMillis;
            meta.caNotAfterMillis = result.caNotAfterMillis;
            meta.importedAtMillis = System.currentTimeMillis();
            if ("tap".equals(result.devType)) {
                // Keep a stable MAC across reimports of the same profile.
                byte[] mac = TapBridge.parseMac(readMetaLocked(context).tapMac);
                if (mac == null) {
                    mac = newLocalMac();
                }
                meta.tapMac = macToString(mac);
            }
            writeMetaLocked(context, meta);
        }
    }

    /** Records that a per-slot file was imported. */
    static void writeSlot(Context context, String slotId, byte[] data) {
        synchronized (LOCK) {
            File slot = slotFile(context, slotId);
            if (slot == null) {
                return;
            }
            writeAtomically(context, slot, data);
            Meta meta = readMetaLocked(context);
            meta.satisfiedSlots.put(slotId, "file");
            writeMetaLocked(context, meta);
        }
    }

    static void updateAfterHoldTest(Context context, boolean passed, String version,
                                    String openssl, String failureTail) {
        synchronized (LOCK) {
            Meta meta = readMetaLocked(context);
            if (passed) {
                meta.validatedWithVersion = version;
                meta.opensslVersion = openssl;
                meta.validatedAtMillis = System.currentTimeMillis();
                meta.validationFailure = "";
            } else {
                meta.validatedWithVersion = "";
                meta.validationFailure = failureTail == null ? "validation failed" : failureTail;
            }
            writeMetaLocked(context, meta);
        }
    }

    static byte[] tapMac(Context context) {
        synchronized (LOCK) {
            return existingOrNewMac(context);
        }
    }

    static void clear(Context context) {
        synchronized (LOCK) {
            File dir = dir(context);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }

    // ---- internals ---------------------------------------------------------

    private static byte[] existingOrNewMac(Context context) {
        Meta meta = readMetaLocked(context);
        byte[] parsed = TapBridge.parseMac(meta.tapMac);
        if (parsed != null) {
            return parsed;
        }
        byte[] mac = newLocalMac();
        // Only persist into an existing profile; never create a stub meta.json
        // (that would make hasProfile() report a phantom profile).
        if (meta.hasProfile()) {
            meta.tapMac = macToString(mac);
            writeMetaLocked(context, meta);
        }
        return mac;
    }

    private static byte[] newLocalMac() {
        byte[] mac = new byte[6];
        new SecureRandom().nextBytes(mac);
        mac[0] = (byte) ((mac[0] & 0xFC) | 0x02); // locally administered, unicast
        return mac;
    }

    private static Meta readMetaLocked(Context context) {
        File meta = new File(dir(context), META);
        if (!meta.exists()) {
            return new Meta();
        }
        try {
            byte[] bytes = readFile(meta);
            return Meta.fromJson(new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return new Meta();
        }
    }

    private static void writeMetaLocked(Context context, Meta meta) {
        writeAtomically(context, new File(dir(context), META),
                meta.toJson().toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readFile(File file) throws Exception {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int off = 0;
            int read;
            while (off < data.length && (read = in.read(data, off, data.length - off)) != -1) {
                off += read;
            }
        }
        return data;
    }

    private static void writeAtomically(Context context, File target, byte[] content) {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(content);
            out.flush();
            out.getFD().sync();
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return;
        }
        if (target.exists() && !target.delete()) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        temp.renameTo(target);
    }

    static String macToString(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.US, "%02x", mac[i] & 0xFF));
        }
        return sb.toString();
    }

    /** Immutable-ish snapshot of meta.json. */
    static final class Meta {
        String devType = "tun";
        String remoteHost = "";
        int remotePort = 1194;
        String remoteProto = "udp";
        String cipherSummary = "";
        boolean authUserPass;
        boolean keyEncrypted;
        int keyDirection = -1;
        List<String> requiredSlots = new ArrayList<>();
        Map<String, String> satisfiedSlots = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        String validatedWithVersion = "";
        String opensslVersion = "";
        String validationFailure = "";
        String certSubject = "";
        long certNotAfterMillis;
        long caNotAfterMillis;
        String tapMac = "";
        long importedAtMillis;
        long validatedAtMillis;

        boolean isTap() {
            return "tap".equals(devType);
        }

        boolean hasProfile() {
            return !remoteHost.isEmpty();
        }

        String remoteSummary() {
            if (remoteHost.isEmpty()) {
                return "";
            }
            return remoteHost + ":" + remotePort + " " + remoteProto;
        }

        boolean allSlotsSatisfied() {
            for (String slot : requiredSlots) {
                if (!satisfiedSlots.containsKey(slot)) {
                    return false;
                }
            }
            return true;
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("devType", devType);
                o.put("remoteHost", remoteHost);
                o.put("remotePort", remotePort);
                o.put("remoteProto", remoteProto);
                o.put("cipherSummary", cipherSummary);
                o.put("authUserPass", authUserPass);
                o.put("keyEncrypted", keyEncrypted);
                o.put("keyDirection", keyDirection);
                o.put("requiredSlots", new JSONArray(requiredSlots));
                o.put("satisfiedSlots", new JSONObject(satisfiedSlots));
                o.put("warnings", new JSONArray(warnings));
                o.put("validatedWithVersion", validatedWithVersion);
                o.put("opensslVersion", opensslVersion);
                o.put("validationFailure", validationFailure);
                o.put("certSubject", certSubject);
                o.put("certNotAfterMillis", certNotAfterMillis);
                o.put("caNotAfterMillis", caNotAfterMillis);
                o.put("tapMac", tapMac);
                o.put("importedAtMillis", importedAtMillis);
                o.put("validatedAtMillis", validatedAtMillis);
            } catch (Exception ignored) {
            }
            return o;
        }

        static Meta fromJson(JSONObject o) {
            Meta m = new Meta();
            m.devType = o.optString("devType", "tun");
            m.remoteHost = o.optString("remoteHost", "");
            m.remotePort = o.optInt("remotePort", 1194);
            m.remoteProto = o.optString("remoteProto", "udp");
            m.cipherSummary = o.optString("cipherSummary", "");
            m.authUserPass = o.optBoolean("authUserPass", false);
            m.keyEncrypted = o.optBoolean("keyEncrypted", false);
            m.keyDirection = o.optInt("keyDirection", -1);
            m.requiredSlots = toStringList(o.optJSONArray("requiredSlots"));
            m.satisfiedSlots = toStringMap(o.optJSONObject("satisfiedSlots"));
            m.warnings = toStringList(o.optJSONArray("warnings"));
            m.validatedWithVersion = o.optString("validatedWithVersion", "");
            m.opensslVersion = o.optString("opensslVersion", "");
            m.validationFailure = o.optString("validationFailure", "");
            m.certSubject = o.optString("certSubject", "");
            m.certNotAfterMillis = o.optLong("certNotAfterMillis", 0L);
            m.caNotAfterMillis = o.optLong("caNotAfterMillis", 0L);
            m.tapMac = o.optString("tapMac", "");
            m.importedAtMillis = o.optLong("importedAtMillis", 0L);
            m.validatedAtMillis = o.optLong("validatedAtMillis", 0L);
            return m;
        }

        private static List<String> toStringList(JSONArray array) {
            List<String> list = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    String s = array.optString(i, "");
                    if (!s.isEmpty()) {
                        list.add(s);
                    }
                }
            }
            return list;
        }

        private static Map<String, String> toStringMap(JSONObject obj) {
            Map<String, String> map = new LinkedHashMap<>();
            if (obj != null) {
                for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                    String key = it.next();
                    map.put(key, obj.optString(key, ""));
                }
            }
            return map;
        }
    }
}
