package com.hrsolution.common.crypto;

import com.hrsolution.common.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The encryption behind the worker Aadhaar, PAN and bank account columns.
 *
 * <p>The tests that matter most are {@link Ciphertext#sameInputEncryptsDifferentlyEveryTime()}
 * and {@link BlindIndexing#sameInputIndexesIdentically()}. Together they state
 * the whole design: ciphertext is non-deterministic so it is safe, and a
 * separate HMAC is deterministic so it is searchable. Get either backwards and
 * the system either leaks or cannot detect a duplicate worker.
 */
class FieldCryptoTest {

    private static final String AADHAAR = "234512341234";
    private static final String PAN = "AABCS1234A";

    private FieldCrypto crypto;

    @BeforeEach
    void setUp() {
        crypto = cryptoWithKey(randomKey());
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("decrypts back to the original value")
        void roundTrips() {
            String encrypted = crypto.encrypt(AADHAAR);

            assertThat(encrypted).isNotEqualTo(AADHAAR);
            assertThat(crypto.decrypt(encrypted)).isEqualTo(AADHAAR);
        }

        @Test
        @DisplayName("survives unicode and punctuation")
        void handlesAwkwardInput() {
            String value = "Shri Rāmeśvar • A/C 123-456";
            assertThat(crypto.decrypt(crypto.encrypt(value))).isEqualTo(value);
        }

        @Test
        @DisplayName("null and blank in, null out, both ways")
        void nullAndBlankAreNull() {
            // An empty string in an encrypted column would be 44 bytes of
            // ciphertext that decrypts to nothing, and would make "not
            // provided" indistinguishable from "provided as empty".
            assertThat(crypto.encrypt(null)).isNull();
            assertThat(crypto.encrypt("")).isNull();
            assertThat(crypto.encrypt("   ")).isNull();
            assertThat(crypto.decrypt(null)).isNull();
            assertThat(crypto.decrypt("")).isNull();
        }
    }

    @Nested
    @DisplayName("ciphertext")
    class Ciphertext {

        @Test
        @DisplayName("the same input encrypts differently every time")
        void sameInputEncryptsDifferentlyEveryTime() {
            // The central property. A fresh IV per call is the only safe way to
            // use GCM: reusing one leaks the XOR of two messages AND allows
            // recovery of the authentication subkey, which lets an attacker
            // forge ciphertexts.
            //
            // The cost is that WHERE aadhaar_encrypted = ? can never work,
            // which is what the blind index exists for.
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                seen.add(crypto.encrypt(AADHAAR));
            }
            assertThat(seen).hasSize(50);
        }

        @Test
        @DisplayName("carries a version prefix")
        void isVersioned() {
            // Rotating the key later means knowing which key encrypted a row.
            // Retrofitting a prefix onto existing data is painful, so it goes
            // in from the start.
            assertThat(crypto.encrypt(AADHAAR)).startsWith("v1:");
        }

        @Test
        @DisplayName("never contains the plaintext")
        void doesNotLeakPlaintext() {
            assertThat(crypto.encrypt(AADHAAR)).doesNotContain(AADHAAR);
            assertThat(crypto.encrypt(PAN)).doesNotContain(PAN);
        }
    }

    @Nested
    @DisplayName("tamper detection")
    class TamperDetection {

        @Test
        @DisplayName("a modified ciphertext is refused, not silently returned as garbage")
        void detectsTampering() {
            String encrypted = crypto.encrypt(AADHAAR);

            // Flip a character in the base64 body. GCM's tag catches it.
            char[] chars = encrypted.toCharArray();
            int target = encrypted.length() - 5;
            chars[target] = chars[target] == 'A' ? 'B' : 'A';

            assertThatThrownBy(() -> crypto.decrypt(new String(chars)))
                    .isInstanceOf(FieldCryptoException.class)
                    .hasMessageContaining("Could not decrypt");
        }

        @Test
        @DisplayName("a value from a different key is refused")
        void detectsWrongKey() {
            String encryptedElsewhere = cryptoWithKey(randomKey()).encrypt(AADHAAR);

            // Returning null here would quietly blank a worker's Aadhaar after
            // a key misconfiguration, which is worse than failing.
            assertThatThrownBy(() -> crypto.decrypt(encryptedElsewhere))
                    .isInstanceOf(FieldCryptoException.class);
        }

        @Test
        @DisplayName("an unversioned value is refused rather than guessed at")
        void refusesUnknownFormat() {
            // Either plaintext predating encryption, or another scheme. Both
            // need a human.
            assertThatThrownBy(() -> crypto.decrypt("234512341234"))
                    .isInstanceOf(FieldCryptoException.class)
                    .hasMessageContaining("not in the expected");
        }

        @Test
        @DisplayName("a truncated value is refused")
        void refusesTruncated() {
            assertThatThrownBy(() -> crypto.decrypt("v1:AAAA"))
                    .isInstanceOf(FieldCryptoException.class);
        }
    }

    @Nested
    @DisplayName("blind indexing")
    class BlindIndexing {

        @Test
        @DisplayName("the same input indexes identically, so duplicates are findable")
        void sameInputIndexesIdentically() {
            // The counterpart to non-deterministic ciphertext. Without this
            // there is no way to answer "is this Aadhaar already on file", and
            // the statutory registers assume one record per Aadhaar.
            assertThat(crypto.blindIndex(AADHAAR)).isEqualTo(crypto.blindIndex(AADHAAR));
        }

        @Test
        @DisplayName("normalises case and surrounding whitespace")
        void normalisesInput() {
            // A PAN typed in lower case must match the same PAN in upper case,
            // or the duplicate check lets a second record through.
            assertThat(crypto.blindIndex("aabcs1234a"))
                    .isEqualTo(crypto.blindIndex("  AABCS1234A  "));
        }

        @Test
        @DisplayName("different inputs index differently")
        void differentInputsDiffer() {
            assertThat(crypto.blindIndex(AADHAAR)).isNotEqualTo(crypto.blindIndex("234512341235"));
        }

        @Test
        @DisplayName("is not reversible and does not contain the plaintext")
        void isOpaque() {
            assertThat(crypto.blindIndex(AADHAAR))
                    .doesNotContain(AADHAAR)
                    .hasSize(64)          // hex SHA-256
                    .matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("differs from the ciphertext, so the index is not the encryption")
        void indexIsNotCiphertext() {
            assertThat(crypto.blindIndex(AADHAAR)).isNotEqualTo(crypto.encrypt(AADHAAR));
        }

        @Test
        @DisplayName("is key-specific, so one deployment's index is useless against another")
        void isKeyed() {
            assertThat(crypto.blindIndex(AADHAAR))
                    .isNotEqualTo(cryptoWithKey(randomKey()).blindIndex(AADHAAR));
        }

        @Test
        @DisplayName("null and blank index to null")
        void nullsAreNull() {
            // So an absent Aadhaar does not collide with every other absent one
            // under the unique index.
            assertThat(crypto.blindIndex(null)).isNull();
            assertThat(crypto.blindIndex("  ")).isNull();
        }
    }

    @Nested
    @DisplayName("key validation at startup")
    class KeyValidation {

        @Test
        @DisplayName("a missing key stops the application with instructions")
        void missingKeyFailsFast() {
            // Must not start and silently write plaintext into a column called
            // *_encrypted.
            assertThatThrownBy(() -> cryptoWithKey(""))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FIELD_ENCRYPTION_KEY")
                    .hasMessageContaining("openssl rand -base64 32");
        }

        @Test
        @DisplayName("a non-base64 key is rejected")
        void malformedKeyFailsFast() {
            assertThatThrownBy(() -> cryptoWithKey("not!valid!base64!"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("base64");
        }

        @Test
        @DisplayName("a key of the wrong length is rejected, naming the length")
        void wrongLengthFailsFast() {
            // 16 bytes is a valid AES key but not the one this scheme declares,
            // so accepting it would make the configuration lie.
            String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

            assertThatThrownBy(() -> cryptoWithKey(shortKey))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("16 bytes")
                    .hasMessageContaining("32");
        }

        @Test
        @DisplayName("surrounding whitespace in the configured key is tolerated")
        void trimsTheKey() {
            // Keys get pasted into .env files and CI secret boxes; a trailing
            // newline must not take the application down.
            String key = randomKey();
            assertThat(cryptoWithKey("  " + key + "\n").decrypt(
                    cryptoWithKey(key).encrypt(AADHAAR))).isEqualTo(AADHAAR);
        }
    }

    // ------------------------------------------------------------------

    private static FieldCrypto cryptoWithKey(String base64Key) {
        AppProperties properties = new AppProperties();
        properties.getCrypto().setFieldEncryptionKey(base64Key);
        FieldCrypto instance = new FieldCrypto(properties);
        instance.initialise();
        return instance;
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
