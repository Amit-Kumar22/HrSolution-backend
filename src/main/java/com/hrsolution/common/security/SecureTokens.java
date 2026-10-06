package com.hrsolution.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generation and hashing of the opaque tokens: refresh tokens, email
 * verification tokens and password reset tokens.
 *
 * <p>Two rules apply to all three:
 *
 * <ol>
 *   <li><strong>Generated from {@link SecureRandom}</strong>, 256 bits of
 *       entropy, URL-safe Base64. Not {@code UUID.randomUUID()}, which carries
 *       roughly 122 bits and is designed for uniqueness rather than
 *       unguessability.</li>
 *   <li><strong>Only the SHA-256 hash is stored.</strong> A database leak then
 *       yields nothing usable - the reader cannot reverse a hash into a working
 *       token. This is also why "resend my verification link" has to issue a
 *       new token rather than re-send the old one: the server genuinely no
 *       longer knows it.</li>
 * </ol>
 *
 * <p>Plain SHA-256 is correct here and BCrypt would be wrong. These tokens are
 * full-entropy random values, so there is no dictionary to attack and no need
 * for a slow hash; and lookup is by hash, which a per-row salt would make
 * impossible without scanning the table.
 */
public final class SecureTokens {

    /** 32 bytes = 256 bits, the floor set by the security conventions. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SecureTokens() {
    }

    /**
     * A fresh opaque token. The caller must hash it for storage and hand the
     * plaintext to the client exactly once - it cannot be recovered later.
     */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** Hex-encoded SHA-256 of {@code token}; 64 characters, matching the column. */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // Every JVM is required to ship SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable on this JVM", e);
        }
    }

    /**
     * Constant-time comparison of two hashes.
     *
     * <p>Lookups go through a unique index on the hash, so this is belt and
     * braces - but where a comparison is done in Java, doing it in constant
     * time avoids leaking how many leading characters matched.
     */
    public static boolean hashesMatch(String firstHash, String secondHash) {
        if (firstHash == null || secondHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                firstHash.getBytes(StandardCharsets.UTF_8),
                secondHash.getBytes(StandardCharsets.UTF_8));
    }
}
