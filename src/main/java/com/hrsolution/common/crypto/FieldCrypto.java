package com.hrsolution.common.crypto;

import com.hrsolution.common.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Authenticated encryption for the sensitive worker columns, plus the blind
 * index that makes them searchable.
 *
 * <h2>Why a random IV, and what that costs</h2>
 *
 * <p>Every {@link #encrypt} call uses a fresh 12-byte IV, so the same Aadhaar
 * number produces different ciphertext every time. That is the correct way to
 * use AES-GCM - <strong>reusing an IV with GCM is catastrophic</strong>, not
 * merely weak: two messages under one IV leak their XOR and, worse, allow
 * recovery of the authentication subkey, which lets an attacker forge
 * ciphertexts at will.
 *
 * <p>The cost is that {@code WHERE aadhaar_encrypted = ?} can never work. A
 * deterministic mode would have made it work and is exactly the trade this
 * design refuses.
 *
 * <h2>The blind index</h2>
 *
 * <p>Duplicate detection is a real requirement - the same person must not be
 * onboarded twice, and the statutory registers assume one record per Aadhaar.
 * {@link #blindIndex} provides it: an HMAC-SHA256 of the normalised value,
 * stored in its own column with a unique index. Equality lookups go through
 * that; the ciphertext is only ever decrypted, never compared.
 *
 * <p>The HMAC key is <em>derived</em> from the master key rather than
 * configured separately, so there is one secret to manage and rotate. It is a
 * distinct key in cryptographic terms, which matters: using the encryption key
 * directly for HMAC mixes purposes.
 *
 * <p>A blind index does leak equality - two workers with the same Aadhaar
 * produce the same hash. That is precisely what it is for, and the alternative
 * (no duplicate detection at all) is worse.
 *
 * <h2>Format</h2>
 *
 * <p>{@code v1:base64(iv || ciphertext || tag)}. The version prefix is not
 * decoration: rotating the key later means being able to tell which key
 * encrypted a given row, and retrofitting a prefix onto existing data is
 * painful. {@link #decrypt} rejects anything it does not recognise rather than
 * guessing.
 */
@Slf4j
@Service
public class FieldCrypto {

    /** Current format version. Bump alongside any change to the scheme. */
    static final String VERSION_PREFIX = "v1:";

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String HMAC = "HmacSHA256";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    /**
     * Domain separation string for the derived HMAC key. Changing it
     * invalidates every stored blind index, so don't.
     */
    private static final byte[] BLIND_INDEX_INFO =
            "hrsolution/blind-index/v1".getBytes(StandardCharsets.UTF_8);

    private final AppProperties appProperties;
    private final SecureRandom random = new SecureRandom();

    private SecretKey encryptionKey;
    private SecretKey blindIndexKey;

    public FieldCrypto(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Validates and derives the keys at startup rather than on first use.
     *
     * <p>A bad key must stop the application now, not at 2am when the first
     * worker is onboarded.
     */
    @PostConstruct
    void initialise() {
        String configured = appProperties.getCrypto().getFieldEncryptionKey();

        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("""
                    app.crypto.field-encryption-key is not set, so the encrypted worker \
                    columns (Aadhaar, PAN, bank account) cannot be read or written. Set \
                    FIELD_ENCRYPTION_KEY to a base64-encoded 256-bit key. Generate one with:
                      openssl rand -base64 32
                    Back it up separately from the database - losing it makes those columns \
                    permanently unreadable.""");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.crypto.field-encryption-key is not valid base64. Generate one with: "
                            + "openssl rand -base64 32", e);
        }

        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                    ("app.crypto.field-encryption-key decodes to %d bytes; AES-256 needs %d. "
                            + "Generate one with: openssl rand -base64 32")
                            .formatted(keyBytes.length, KEY_BYTES));
        }

        this.encryptionKey = new SecretKeySpec(keyBytes, "AES");
        this.blindIndexKey = deriveBlindIndexKey(keyBytes);

        log.info("Field encryption initialised (AES-256-GCM, random IV, HMAC-SHA256 blind index)");
    }

    /**
     * Encrypts a value, or returns null for a null or blank input.
     *
     * <p>Blank in means null out on purpose: an empty string in an encrypted
     * column is 44 bytes of ciphertext that decrypts to nothing, and it makes
     * "not provided" indistinguishable from "provided as empty".
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            // Deliberately does not include the plaintext in the message.
            throw new FieldCryptoException("Could not encrypt a sensitive field.", e);
        }
    }

    /** Decrypts a value produced by {@link #encrypt}, or null for null. */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!stored.startsWith(VERSION_PREFIX)) {
            // Either plaintext written before encryption was introduced, or a
            // value from a scheme this build does not know. Guessing would be
            // worse than refusing.
            throw new FieldCryptoException(
                    "A sensitive field is not in the expected '" + VERSION_PREFIX
                            + "' format. It may predate field encryption or have been written "
                            + "by a different version.");
        }

        try {
            byte[] combined = Base64.getDecoder()
                    .decode(stored.substring(VERSION_PREFIX.length()));
            if (combined.length <= IV_BYTES) {
                throw new FieldCryptoException("A sensitive field is truncated.");
            }

            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            byte[] ciphertext = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // A GCM tag mismatch lands here. It means the ciphertext was
            // altered or the key is wrong - both worth shouting about, and
            // neither is something to paper over by returning null.
            throw new FieldCryptoException(
                    "Could not decrypt a sensitive field. The encryption key may be wrong, "
                            + "or the stored value may have been tampered with.", e);
        }
    }

    /**
     * A searchable, non-reversible fingerprint of a value.
     *
     * <p>Normalises first - trimmed and upper-cased - so {@code "abcde1234f"}
     * and {@code " ABCDE1234F "} index identically. Without that, a PAN typed
     * in lower case would not match the same PAN typed in upper case, and the
     * duplicate check would silently let a second record through.
     */
    public String blindIndex(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        String normalised = plaintext.trim().toUpperCase();
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(blindIndexKey);
            return HexFormat.of().formatHex(
                    mac.doFinal(normalised.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new FieldCryptoException("Could not index a sensitive field.", e);
        }
    }

    /**
     * Derives the HMAC key from the master key by HMAC-ing a fixed label with
     * it - the extract-and-expand idea from HKDF, which is enough here because
     * the input is already a uniformly random 256-bit key.
     */
    private static SecretKey deriveBlindIndexKey(byte[] masterKey) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(masterKey, HMAC));
            return new SecretKeySpec(mac.doFinal(BLIND_INDEX_INFO), HMAC);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not derive the blind index key.", e);
        }
    }
}
