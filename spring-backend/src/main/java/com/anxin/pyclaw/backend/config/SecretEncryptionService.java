package com.anxin.pyclaw.backend.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Encrypts and decrypts sensitive values (e.g. API keys) using AES-256-GCM.
 * <p>
 * The encryption key is derived from {@link PyclawSecurityProperties#encryptionSecret()}.
 * Encrypted values are stored as base64-encoded {@code IV (12 bytes) || ciphertext || GCM tag (16 bytes)}.
 * </p>
 */
@Service
public class SecretEncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String ENCRYPTED_PREFIX = "aes$";

    private final byte[] keyBytes;

    public SecretEncryptionService(PyclawSecurityProperties properties) {
        String secret = properties.encryptionSecret();
        if (secret == null || secret.isBlank()) {
            this.keyBytes = null;
        } else {
            this.keyBytes = deriveKey(secret);
        }
    }

    /** Returns true if encryption is configured. */
    public boolean isAvailable() {
        return keyBytes != null;
    }

    /**
     * Encrypts a plaintext value. If encryption is not configured, returns the value unchanged.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        if (!isAvailable()) {
            return plaintext;
        }
        // If already encrypted, return as-is
        if (plaintext.startsWith(ENCRYPTED_PREFIX)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret value", e);
        }
    }

    /**
     * Decrypts a value. If encryption is not configured or the value is not encrypted,
     * returns the value unchanged.
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (!isAvailable()) {
            return stored;
        }
        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            return stored;
        }
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(ENCRYPTED_PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] iv = new byte[GCM_IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt secret value", e);
        }
    }

    /**
     * Derives a 256-bit (32 byte) key from the configured secret using SHA-256.
     */
    private static byte[] deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(hash, 32); // 256 bits
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }
}
