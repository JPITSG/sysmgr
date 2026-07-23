package com.jpitsg.sysman;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outcome of parsing + validating an imported OpenVPN profile: errors and
 * warnings for the user, the summary the store persists, the required/satisfied
 * slot maps, any inline key material extracted, and the normalized config.
 */
final class OpenVpnValidationResult {
    final List<String> errors = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();

    String remoteHost = "";
    int remotePort = 1194;
    String remoteProto = "udp";
    String devType = "tun";
    String cipherSummary = "";
    boolean authUserPass;
    boolean keyEncrypted;
    int keyDirection = -1;
    String certSubject = "";
    long certNotAfterMillis;
    long caNotAfterMillis;

    final List<String> requiredSlots = new ArrayList<>();
    final Map<String, String> satisfiedSlots = new LinkedHashMap<>();
    final Map<String, byte[]> inlineSlotBytes = new LinkedHashMap<>();

    String normalizedConf = "";

    boolean ok() {
        return errors.isEmpty();
    }

    void error(String message) {
        errors.add(message);
    }

    void warn(String message) {
        warnings.add(message);
    }

    void requireSlot(String slotId) {
        if (!requiredSlots.contains(slotId)) {
            requiredSlots.add(slotId);
        }
    }
}
