package com.example.kms.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JPA AttributeConverter that encrypts/decrypts String columns using AES-256-GCM.
 *
 * Storage format: Base64(iv[12] || ciphertext_with_auth_tag)
 *
 * Master key is loaded from the VAULT_COLUMN_MASTER_KEY environment variable
 * (Base64-encoded 32-byte key). A hardcoded development fallback is used when
 * the env var is absent — this must never be used in production.
 */
@Converter(autoApply = false)
public class EncryptDecryptConverter implements AttributeConverter<String, String> {

    private static final Logger LOG = Logger.getLogger(EncryptDecryptConverter.class.getName());

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // Dev-only fallback key (32 bytes). NEVER use in production.
    private static final String DEV_FALLBACK_KEY_B64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private static final SecretKeySpec MASTER_KEY;

    static {
        String envKey = System.getenv("VAULT_COLUMN_MASTER_KEY");
        if (envKey == null || envKey.isBlank()) {
            LOG.log(Level.WARNING,
                    "VAULT_COLUMN_MASTER_KEY env var is not set. " +
                    "Using insecure development fallback key — DO NOT use in production!");
            envKey = DEV_FALLBACK_KEY_B64;
        }
        byte[] keyBytes = Base64.getDecoder().decode(envKey);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "VAULT_COLUMN_MASTER_KEY must be a Base64-encoded 32-byte (256-bit) key, " +
                    "but got " + keyBytes.length + " bytes.");
        }
        MASTER_KEY = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, MASTER_KEY, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV: iv[12] || ciphertext_with_auth_tag
            byte[] combined = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH_BYTES, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting column value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, MASTER_KEY, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting column value", e);
        }
    }
}
