package com.example.kms.service;

import com.example.kms.util.CryptoUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Random;
import java.security.SecureRandom;

@Service
public class CryptoService {
    private static final String KEY_AGREEMENT_ALG = "X25519";
    private static final String AES_ALG = "AES/GCM/NoPadding";
    private static final int AES_KEY_BYTES = 32; // AES-256
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private KeyPair serverKeyPair;
    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    public void init() throws Exception {
        serverKeyPair = generateX25519KeyPair();
    }

    public KeyPair generateX25519KeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_AGREEMENT_ALG);
        return kpg.generateKeyPair();
    }

    public String getServerPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded());
    }

    public PrivateKey getServerPrivateKey() {
        return serverKeyPair.getPrivate();
    }

    public PublicKey getServerPublicKey() {
        return serverKeyPair.getPublic();
    }

    public byte[] computeSharedSecret(PrivateKey ourPriv, PublicKey theirPub) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance(KEY_AGREEMENT_ALG);
        ka.init(ourPriv);
        ka.doPhase(theirPub, true);
        return ka.generateSecret(); // X25519 returns 32 bytes
    }

    public PublicKey publicKeyFromBase64(String base64X509) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64X509);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance(KEY_AGREEMENT_ALG);
        return kf.generatePublic(spec);
    }

    // HKDF using CryptoUtils (extract & expand)
    public byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int outLen) throws Exception {
        return CryptoUtils.hkdfExtractAndExpand(salt, ikm, info, outLen);
    }

    /**
     * Compatibility wrapper — some code (DecryptService) expects deriveKey(...)
     * Use this instead of changing many call sites.
     */
    public byte[] deriveKey(byte[] salt, byte[] ikm, byte[] info, int outputLength) throws Exception {
        return hkdf(salt, ikm, info, outputLength);
    }

    // AES-GCM encrypt: returns iv||ciphertext as bytes
    public byte[] aesGcmEncryptBytes(byte[] key, byte[] plaintext) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_ALG);
        SecretKeySpec ks = new SecretKeySpec(key, "AES");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, ks, spec);
        byte[] ct = cipher.doFinal(plaintext);

        ByteBuffer bb = ByteBuffer.allocate(iv.length + ct.length);
        bb.put(iv);
        bb.put(ct);
        return bb.array();
    }

    // AES-GCM decrypt expecting iv||ciphertext bytes
    public byte[] aesGcmDecryptBytes(byte[] key, byte[] ivAndCiphertext) throws Exception {
        if (ivAndCiphertext.length < GCM_IV_BYTES + 16) throw new IllegalArgumentException("ciphertext too short");
        byte[] iv = new byte[GCM_IV_BYTES];
        System.arraycopy(ivAndCiphertext, 0, iv, 0, GCM_IV_BYTES);
        byte[] ct = new byte[ivAndCiphertext.length - GCM_IV_BYTES];
        System.arraycopy(ivAndCiphertext, GCM_IV_BYTES, ct, 0, ct.length);

        Cipher cipher = Cipher.getInstance(AES_ALG);
        SecretKeySpec ks = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, ks, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ct);
    }

    // Helper: produce separate base64 iv and ciphertext
    public EncryptResult encryptAndSplit(byte[] key, byte[] plaintext) throws Exception {
        byte[] combined = aesGcmEncryptBytes(key, plaintext);
        byte[] iv = new byte[GCM_IV_BYTES];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
        byte[] ciphertext = new byte[combined.length - GCM_IV_BYTES];
        System.arraycopy(combined, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);
        return new EncryptResult(Base64.getEncoder().encodeToString(ciphertext), Base64.getEncoder().encodeToString(iv));
    }

    // Wrap DEK for recipientPublicKeyBase64 using server private and HKDF
    public String wrapDekForRecipient(byte[] dek, PrivateKey ourPriv, String recipientPublicKeyBase64, byte[] hkdfSalt, byte[] hkdfInfo) throws Exception {
        PublicKey recipientPub = publicKeyFromBase64(recipientPublicKeyBase64);
        byte[] shared = computeSharedSecret(ourPriv, recipientPub);
        byte[] kek = hkdf(hkdfSalt, shared, hkdfInfo, AES_KEY_BYTES);
        byte[] wrapped = aesGcmEncryptBytes(kek, dek); // iv||ciphertext
        return Base64.getEncoder().encodeToString(wrapped);
    }

    // Unwrap DEK (caller must supply appropriate private key and sender public)
    public byte[] unwrapDek(String wrappedBase64, PrivateKey ourPriv, String senderPublicKeyBase64, byte[] hkdfSalt, byte[] hkdfInfo) throws Exception {
        PublicKey senderPub = publicKeyFromBase64(senderPublicKeyBase64);
        byte[] shared = computeSharedSecret(ourPriv, senderPub);
        byte[] kek = hkdf(hkdfSalt, shared, hkdfInfo, AES_KEY_BYTES);
        byte[] wrapped = Base64.getDecoder().decode(wrappedBase64);
        return aesGcmDecryptBytes(kek, wrapped);
    }

    public byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        random.nextBytes(b);
        return b;
    }

    public String encodeKey(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    // Simple holder
    public static class EncryptResult {
        private final String ciphertextBase64;
        private final String ivBase64;

        public EncryptResult(String ciphertextBase64, String ivBase64) {
            this.ciphertextBase64 = ciphertextBase64;
            this.ivBase64 = ivBase64;
        }

        public String getCiphertextBase64() { return ciphertextBase64; }
        public String getIvBase64() { return ivBase64; }
    }
}
