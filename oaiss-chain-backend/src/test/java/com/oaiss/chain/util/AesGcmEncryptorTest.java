package com.oaiss.chain.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AesGcmEncryptor unit tests.
 * Uses a fixed 256-bit KEK for deterministic test behavior.
 */
class AesGcmEncryptorTest {

    private static final String TEST_KEK_BASE64 = Base64.getEncoder()
            .encodeToString(new byte[32]); // 256-bit zero key

    private AesGcmEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new AesGcmEncryptor(TEST_KEK_BASE64);
    }

    @Test
    @DisplayName("Round-trip: encrypt then decrypt returns original plaintext")
    void testRoundTrip() {
        String plaintext = "Hello, AES-256-GCM!";
        String encrypted = encryptor.encrypt(plaintext);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Different IV per call: encrypting twice produces different ciphertext")
    void testDifferentIvPerCall() {
        String plaintext = "same-input";
        String encrypted1 = encryptor.encrypt(plaintext);
        String encrypted2 = encryptor.encrypt(plaintext);
        assertNotEquals(encrypted1, encrypted2, "Each encryption should use a random IV");
        // Both should still decrypt to the same plaintext
        assertEquals(plaintext, encryptor.decrypt(encrypted1));
        assertEquals(plaintext, encryptor.decrypt(encrypted2));
    }

    @Test
    @DisplayName("Wrong key throws exception on decrypt")
    void testWrongKeyThrowsException() {
        String plaintext = "secret-data";
        String encrypted = encryptor.encrypt(plaintext);

        // Create encryptor with a different KEK
        String wrongKekBase64 = Base64.getEncoder()
                .encodeToString(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});
        AesGcmEncryptor wrongKeyEncryptor = new AesGcmEncryptor(wrongKekBase64);

        assertThrows(RuntimeException.class, () -> wrongKeyEncryptor.decrypt(encrypted));
    }

    @Test
    @DisplayName("Tampered ciphertext throws exception on decrypt")
    void testTamperedCiphertextThrowsException() {
        String plaintext = "important-data";
        String encrypted = encryptor.encrypt(plaintext);

        // Tamper with the ciphertext by flipping a byte
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        encryptedBytes[encryptedBytes.length - 1] ^= 0xFF; // flip last byte
        String tampered = Base64.getEncoder().encodeToString(encryptedBytes);

        assertThrows(RuntimeException.class, () -> encryptor.decrypt(tampered));
    }

    @Test
    @DisplayName("Blank KEK throws IllegalStateException")
    void testBlankKekThrowsException() {
        assertThrows(IllegalStateException.class, () -> new AesGcmEncryptor(""));
        assertThrows(IllegalStateException.class, () -> new AesGcmEncryptor("   "));
        assertThrows(IllegalStateException.class, () -> new AesGcmEncryptor(null));
    }

    @Test
    @DisplayName("Encrypt handles empty string")
    void testEncryptEmptyString() {
        String encrypted = encryptor.encrypt("");
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    @DisplayName("Wrong-length KEK throws IllegalStateException")
    void testWrongLengthKekThrowsException() {
        // 16 bytes instead of 32 — valid base64 but wrong decoded length
        String shortKek = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new AesGcmEncryptor(shortKek));
    }
}
