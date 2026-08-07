package com.jpitsg.sysman;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFB security type 2, "VNC Authentication".
 *
 * <p>The server sends sixteen random bytes and the client returns them
 * DES-encrypted with the password as the key. The quirk that catches everyone
 * out is that each key byte has its bits reversed first — a mistake nobody in
 * 1998 could take back, and every client still does it.
 *
 * <p>This is weak by construction: the key is eight characters of DES, and a
 * captured challenge/response pair is brute-forceable offline. The server
 * throttles failed attempts because of it, and the panel says out loud that
 * only the first eight characters count.
 */
final class VncAuth {
    static final int CHALLENGE_LENGTH = 16;

    private static final Charset PASSWORD_CHARSET = Charset.forName("ISO-8859-1");
    private static final SecureRandom RANDOM = new SecureRandom();

    private VncAuth() {
    }

    static byte[] newChallenge() {
        byte[] challenge = new byte[CHALLENGE_LENGTH];
        RANDOM.nextBytes(challenge);
        return challenge;
    }

    /**
     * @return true when the client's response matches what the password would
     *         have produced. Never throws for a bad response.
     */
    static boolean verify(String password, byte[] challenge, byte[] response) {
        if (challenge == null || response == null || response.length != CHALLENGE_LENGTH) {
            return false;
        }
        byte[] expected = encrypt(password, challenge);
        if (expected == null) {
            return false;
        }
        // Constant-time: a timing oracle on an eight-character key is worth
        // avoiding even on a private network.
        return MessageDigest.isEqual(expected, response);
    }

    private static byte[] encrypt(String password, byte[] challenge) {
        try {
            Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(desKey(password), "DES"));
            return cipher.doFinal(challenge);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The password as a DES key: eight bytes, zero padded, truncated, and every
     * byte bit-reversed.
     */
    private static byte[] desKey(String password) {
        byte[] raw = (password == null ? "" : password).getBytes(PASSWORD_CHARSET);
        byte[] key = new byte[8];
        for (int i = 0; i < key.length; i++) {
            key[i] = i < raw.length ? reverseBits(raw[i]) : 0;
        }
        return key;
    }

    private static byte reverseBits(byte value) {
        int in = value & 0xFF;
        int out = 0;
        for (int i = 0; i < 8; i++) {
            out = (out << 1) | (in & 1);
            in >>= 1;
        }
        return (byte) out;
    }
}
