package com.example.kms.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Converter
public class EncryptDecryptConverter implements AttributeConverter<String, String> {

    private static final String ALGO = "AES";
    // ⚠️ Replace with a 16-char secret key stored securely (env/config, not hardcoded in production!)
    private static final String SECRET = "MySuperSecretKey";

    private final SecretKeySpec secretKey;

    public EncryptDecryptConverter() {
        this.secretKey = new SecretKeySpec(SECRET.getBytes(), ALGO);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(attribute.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting field", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(dbData)));
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting field", e);
        }
    }
}

