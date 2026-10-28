package com.oaiss.chain.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryptor for protecting RSA private keys at rest.
 *
 * <p>Uses a Key Encryption Key (KEK) provided via the {@code RSA_KEK} environment
 * variable. Each encryption generates a random 12-byte IV, producing output in
 * the format {@code Base64(IV || ciphertext || GCM-tag)}.</p>
 *
 * @author OAISS Team
 */
@Component
public final class AesGcmEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptor(@Value("${RSA_KEK:}") String kekBase64) {
        if (kekBase64 == null || kekBase64.isBlank()) {
            throw new IllegalStateException(
                    "RSA_KEK environment variable is required for private key encryption. "
                            + "Generate with: openssl rand -base64 32");
        }
        byte[] keyBytes = Base64.getDecoder().decode(kekBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "RSA_KEK must decode to exactly 32 bytes (256 bits) for AES-256, got " + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypt plaintext and return Base64-encoded result (IV || ciphertext || tag).
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt Base64-encoded data (IV || ciphertext || tag) back to plaintext.
     */
    public String decrypt(String encryptedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);

            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }
}
