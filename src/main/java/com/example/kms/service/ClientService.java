package com.example.kms.service;

import com.example.kms.dto.ClientLoginRequest;
import com.example.kms.dto.ClientRegisterRequest;
import com.example.kms.dto.ServerStoreRequest;
import com.example.kms.dto.ServerStoreResponse;
import com.example.kms.model.Client;
import com.example.kms.model.EncryptedData;
import com.example.kms.model.DataType;
import com.example.kms.repository.ClientRepository;
import com.example.kms.repository.EncryptedDataRepository;
import com.example.kms.util.HashUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

// inside ClientService class


import java.security.KeyPair;
import java.util.Base64;
import java.util.List;

@Service
public class ClientService {
    private final ClientRepository clientRepository;
    private final EncryptedDataRepository encryptedDataRepository;
    private final CryptoService cryptoService;
    private final PasswordEncoder passwordEncoder;

    public ClientService(ClientRepository clientRepository,
                         EncryptedDataRepository encryptedDataRepository,
                         CryptoService cryptoService,
                         PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.encryptedDataRepository = encryptedDataRepository;
        this.cryptoService = cryptoService;
        this.passwordEncoder = passwordEncoder;
    }

    // -------------------------------
    // Register client
    // -------------------------------
    @Transactional
    public Client registerClient(ClientRegisterRequest req) throws Exception {
        if (clientRepository.findByPhone(req.getPhone()).isPresent()) {
            throw new IllegalArgumentException("Phone already registered");
        }

        // Hash the email for login lookups
        String emailHash = HashUtil.sha256(req.getEmail());

        if (clientRepository.findByEmailHash(emailHash).isPresent()) {
            throw new IllegalArgumentException("email already registered");
        }

        Client c = new Client();
        c.setName(req.getName());
        c.setPhone(req.getPhone());
        c.setEmail(req.getEmail()); // will be encrypted by JPA converter
        c.setEmailHash(emailHash);

        // Hash password with BCrypt
        c.setPassword(passwordEncoder.encode(req.getPassword()));

        // Generate or accept provided client public key
        if (req.getPublicKey() == null || req.getPublicKey().isBlank()) {
            KeyPair kp = cryptoService.generateX25519KeyPair();
            c.setPublicKey(cryptoService.encodeKey(kp.getPublic()));
        } else {
            c.setPublicKey(req.getPublicKey());
        }

        // Server public key
        if (req.getServerPublicKey() == null || req.getServerPublicKey().isBlank()) {
            KeyPair serverKp = cryptoService.generateX25519KeyPair();
            c.setServerPublicKey(cryptoService.encodeKey(serverKp.getPublic()));
        } else {
            c.setServerPublicKey(req.getServerPublicKey());
        }

        return clientRepository.save(c);
    }

    // -------------------------------
    // Login client
    // -------------------------------

@Transactional(readOnly = true)
public Client loginClient(ClientLoginRequest req) {
    String emailHash = HashUtil.sha256(req.getEmail());

    Client client = clientRepository.findByEmailHash(emailHash)
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

    if (!passwordEncoder.matches(req.getPassword(), client.getPassword())) {
        throw new IllegalArgumentException("Invalid email or password");
    }

    return client;
}



    // -------------------------------
    // Store record (client-provided crypto values)
    // -------------------------------
    @Transactional
    public EncryptedData storeEncryptedRecord(String email,
                                              String encryptedDataBase64,
                                              String ivBase64,
                                              String saltBase64,
                                              String dekWrappedForClientBase64,
                                              String dekWrappedForRecoveryBase64,
                                              DataType storageType) {
        Client client = clientRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("client not found"));

        EncryptedData ed = new EncryptedData();
        ed.setClient(client);
        ed.setEncryptedPayload(encryptedDataBase64);
        ed.setIv(ivBase64);
        ed.setSalt(saltBase64);
        ed.setDekWrapped(dekWrappedForClientBase64);
        ed.setDekWrappedForRecovery(dekWrappedForRecoveryBase64);
        ed.setDataType(storageType);

        return encryptedDataRepository.save(ed);
    }

    // -------------------------------
    // Store record (server generates crypto)
    // -------------------------------
    @Transactional
    public ServerStoreResponse storePlaintextServerSide(String email, ServerStoreRequest req) throws Exception {
        Client client = clientRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("client not found"));

        // decode plaintext
        byte[] plaintext = Base64.getDecoder().decode(req.getPlaintextBase64());

        // 1) generate random DEK (32 bytes)
        byte[] dek = cryptoService.randomBytes(32);

        // 2) encrypt plaintext with DEK -> AES-256-GCM
        CryptoService.EncryptResult enc = cryptoService.encryptAndSplit(dek, plaintext);
        String ciphertextBase64 = enc.getCiphertextBase64();
        String ivBase64 = enc.getIvBase64();

        // 3) generate random salt
        byte[] salt = cryptoService.randomBytes(16);
        String saltBase64 = Base64.getEncoder().encodeToString(salt);

        // 4) HKDF info
        String infoStr = "KMS-v1|dek-wrap|phone:" + client.getPhone();
        byte[] info = infoStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // 5) wrap DEK for client
        String dekWrappedForClient = cryptoService.wrapDekForRecipient(
                dek,
                cryptoService.getServerPrivateKey(),
                client.getPublicKey(),
                salt,
                info
        );

        // 6) optional recovery key
        String dekWrappedForRecovery = null;
        if (req.getRecoveryPublicKeyBase64() != null && !req.getRecoveryPublicKeyBase64().isBlank()) {
            dekWrappedForRecovery = cryptoService.wrapDekForRecipient(
                    dek,
                    cryptoService.getServerPrivateKey(),
                    req.getRecoveryPublicKeyBase64(),
                    salt,
                    ("KMS-v1|dek-wrap|recovery|phone:" + client.getPhone()).getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }

        // 7) persist encrypted entity
        EncryptedData ed = new EncryptedData();
        ed.setClient(client);
        ed.setEncryptedPayload(ciphertextBase64);
        ed.setIv(ivBase64);
        ed.setSalt(saltBase64);
        ed.setDekWrapped(dekWrappedForClient);
        ed.setDekWrappedForRecovery(dekWrappedForRecovery);
        ed.setDataType(DataType.valueOf(req.getDataType().toUpperCase()));

        EncryptedData saved = encryptedDataRepository.save(ed);

        // 8) build response
        ServerStoreResponse resp = new ServerStoreResponse();
        resp.setEncryptedDataId(saved.getId());
        resp.setDekWrappedForClient(dekWrappedForClient);
        resp.setDekWrappedForRecovery(dekWrappedForRecovery);
        resp.setIv(ivBase64);
        resp.setSalt(saltBase64);
        resp.setServerPublicKey(cryptoService.getServerPublicKeyBase64());
        return resp;
    }

    // -------------------------------
    // Fetch records
    // -------------------------------
    public List<EncryptedData> fetchEncryptedRecords(String email) {
        Client client = clientRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("client not found"));
        return encryptedDataRepository.findByClient(client);
    }

    public List<EncryptedData> fetchEncryptedRecordsByType(String email, DataType dataType) {
        Client client = clientRepository.findByPhone(email)
                .orElseThrow(() -> new IllegalArgumentException("client not found"));
        return encryptedDataRepository.findByClientAndDataType(client, dataType);
    }
}
