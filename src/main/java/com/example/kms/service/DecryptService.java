package com.example.kms.service;

import com.example.kms.model.Client;
import com.example.kms.model.EncryptedData;
import com.example.kms.repository.ClientRepository;
import com.example.kms.repository.EncryptedDataRepository;
import com.example.kms.util.HashUtil;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

@Service
public class DecryptService {

    private final ClientRepository clientRepository;
    private final EncryptedDataRepository encryptedDataRepository;
    private final CryptoService cryptoService;

    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_BYTES = 32; // 256-bit

    public DecryptService(ClientRepository clientRepository,
                          EncryptedDataRepository encryptedDataRepository,
                          CryptoService cryptoService) {
        this.clientRepository = clientRepository;
        this.encryptedDataRepository = encryptedDataRepository;
        this.cryptoService = cryptoService;
    }

public DecryptResult decryptRecord(String rawEmail,
                                   Long recordId,
                                   String clientPublicKeyBase64,
                                   String serverPublicKeyBase64) throws Exception {

    if (clientPublicKeyBase64 == null || clientPublicKeyBase64.isBlank()) {
        throw new IllegalArgumentException("Client public key must be provided by the user");
    }
    if (serverPublicKeyBase64 == null || serverPublicKeyBase64.isBlank()) {
        throw new IllegalArgumentException("Server public key must be provided by the user");
    }

    // hash incoming raw email the same way registration did
    String emailHash = HashUtil.sha256(rawEmail.trim().toLowerCase());

    Client client = clientRepository.findByEmailHash(emailHash)
            .orElseThrow(() -> new IllegalArgumentException("client not found"));

    // ✅ Verify that the user-supplied server key matches DB
    if (!serverPublicKeyBase64.equals(client.getServerPublicKey())) {
        throw new IllegalArgumentException("Server public key mismatch. Please enter the correct server key");
    }

    EncryptedData ed = encryptedDataRepository.findById(recordId)
            .orElseThrow(() -> new IllegalArgumentException("record not found"));

    // 1) parse provided client public key
    PublicKey clientPub = cryptoService.publicKeyFromBase64(clientPublicKeyBase64);

    // 2) compute shared secret using server private key
    PrivateKey serverPrivate = cryptoService.getServerPrivateKey();
    byte[] shared = cryptoService.computeSharedSecret(serverPrivate, clientPub); // 32 bytes

    // 3) derive KEK via HKDF using stored salt and same info used at wrap time
    byte[] salt = Base64.getDecoder().decode(ed.getSalt());
    String infoStr = "KMS-v1|unwrap-dek|phone:" + client.getPhone() + "|record:" + ed.getId();
    byte[] info = infoStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

    byte[] kek = cryptoService.deriveKey(salt, shared, info, AES_KEY_BYTES);

    // 4) unwrap DEK
    String wrappedDekBase64 = ed.getDekWrapped();
    byte[] dek = aesGcmDecryptToBytes(kek, wrappedDekBase64);

    // 5) decrypt payload using DEK and stored IV
    byte[] plaintextBytes = decryptWithAesGcm(dek, ed.getIv(), ed.getEncryptedPayload());

    DecryptResult result = new DecryptResult();
    result.setDataType(ed.getDataType().name());
    result.setPlaintextBytes(plaintextBytes);
    return result;
}


    private byte[] decryptWithAesGcm(byte[] dek, String ivBase64, String ciphertextBase64) throws Exception {
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] ct = Base64.getDecoder().decode(ciphertextBase64);

        SecretKeySpec key = new SecretKeySpec(dek, 0, Math.min(dek.length, 32), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ct);
    }

    private byte[] aesGcmDecryptToBytes(byte[] kek, String base64IvCt) throws Exception {
        byte[] all = Base64.getDecoder().decode(base64IvCt);

        if (all.length > 12 + 16) {
            byte[] iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, 12);
            byte[] ct = new byte[all.length - 12];
            System.arraycopy(all, 12, ct, 0, ct.length);

            SecretKeySpec key = new SecretKeySpec(kek, 0, Math.min(kek.length, 32), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ct);
        } else {
            throw new IllegalArgumentException("Wrapped DEK format not supported (missing IV)");
        }
    }

    public static class DecryptResult {
        private byte[] plaintextBytes;
        private String dataType;

        public byte[] getPlaintextBytes() { return plaintextBytes; }
        public void setPlaintextBytes(byte[] plaintextBytes) { this.plaintextBytes = plaintextBytes; }

        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
    }
}
