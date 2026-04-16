package com.example.kms.service;

import com.example.kms.dto.VaultFileDTO;
import com.example.kms.exception.ResourceNotFoundException;
import com.example.kms.model.Client;
import com.example.kms.model.FileCategory;
import com.example.kms.model.VaultFile;
import com.example.kms.repository.ClientRepository;
import com.example.kms.repository.VaultFileRepository;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class VaultServiceImpl implements VaultService {

    private final VaultFileRepository vaultFileRepository;
    private final ClientRepository clientRepository;
    private final CryptoService cryptoService;
    private final SupabaseStorageService supabaseStorageService;
    private final Executor cryptoExecutor;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    public VaultServiceImpl(
            VaultFileRepository vaultFileRepository,
            ClientRepository clientRepository,
            CryptoService cryptoService,
            SupabaseStorageService supabaseStorageService,
            @Qualifier("cryptoExecutor") Executor cryptoExecutor) {
        this.vaultFileRepository = vaultFileRepository;
        this.clientRepository = clientRepository;
        this.cryptoService = cryptoService;
        this.supabaseStorageService = supabaseStorageService;
        this.cryptoExecutor = cryptoExecutor;
    }

    @Override
    public CompletableFuture<VaultFileDTO> uploadFileAsync(String emailHash, MultipartFile file) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Detect MIME type via Apache Tika
                Tika tika = new Tika();
                String detectedMimeType = tika.detect(file.getInputStream(), file.getOriginalFilename());

                // 2. Determine FileCategory from MIME type
                FileCategory category = FileCategory.fromMimeType(detectedMimeType);

                // 3. Generate random 32-byte DEK
                byte[] dek = cryptoService.randomBytes(32);

                // 4. Encrypt file bytes → iv||ciphertext
                byte[] encryptedBlob = cryptoService.aesGcmEncryptBytes(dek, file.getBytes());

                // 5. Generate random 16-byte salt
                byte[] salt = cryptoService.randomBytes(16);

                // 6. Wrap DEK for client using X25519
                Client client = clientRepository.findByEmailHash(emailHash)
                        .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + emailHash));
                String info = "VAULT-v1|dek-wrap|client|emailHash:" + emailHash;
                String dekWrappedClient = cryptoService.wrapDekForRecipient(
                        dek,
                        cryptoService.getServerPrivateKey(),
                        client.getPublicKey(),
                        salt,
                        info.getBytes());

                // 7. Wrap DEK for server using HKDF
                byte[] serverKek = cryptoService.deriveServerKek(emailHash);
                byte[] wrappedServerBytes = cryptoService.aesGcmEncryptBytes(serverKek, dek);
                String dekWrappedServer = Base64.getEncoder().encodeToString(wrappedServerBytes);

                // 8. Build storage key
                String storageKey = "vault/" + emailHash + "/" + UUID.randomUUID();

                // 9. Store encrypted blob in Supabase
                supabaseStorageService.putObject(bucket, storageKey, encryptedBlob, detectedMimeType);

                // 10. Extract IV from encrypted result (first 12 bytes) and encode as Base64
                byte[] ivBytes = new byte[12];
                System.arraycopy(encryptedBlob, 0, ivBytes, 0, 12);
                String ivBase64 = Base64.getEncoder().encodeToString(ivBytes);
                String saltBase64 = Base64.getEncoder().encodeToString(salt);

                // 11. Save VaultFile entity
                VaultFile vaultFile = VaultFile.builder()
                        .owner(client)
                        .filename(file.getOriginalFilename())
                        .contentType(detectedMimeType)
                        .category(category)
                        .storageKey(storageKey)
                        .originalSize(file.getSize())
                        .encryptedSize(encryptedBlob.length)
                        .dekWrappedClient(dekWrappedClient)
                        .dekWrappedServer(dekWrappedServer)
                        .iv(ivBase64)
                        .salt(saltBase64)
                        .guest(false)
                        .build();

                VaultFile saved = vaultFileRepository.save(vaultFile);

                // 12. Return VaultFileDTO
                return toDTO(saved);
            } catch (Exception e) {
                throw new RuntimeException("Upload failed", e);
            }
        }, cryptoExecutor);
    }

    @Override
    public List<VaultFileDTO> listFiles(String emailHash) {
        return vaultFileRepository.findAllByOwner_EmailHash(emailHash)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<byte[]> downloadFileAsync(String emailHash, UUID fileId) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Find VaultFile by id and owner emailHash
                VaultFile vaultFile = vaultFileRepository.findByIdAndOwner_EmailHash(fileId, emailHash)
                        .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

                // 2. Fetch encrypted blob from Supabase
                byte[] encryptedBlob = supabaseStorageService.getObject(bucket, vaultFile.getStorageKey());

                // 3. Unwrap DEK for client
                Client client = clientRepository.findByEmailHash(emailHash)
                        .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + emailHash));
                byte[] saltBytes = Base64.getDecoder().decode(vaultFile.getSalt());
                String info = "VAULT-v1|dek-wrap|client|emailHash:" + emailHash;
                byte[] dek = cryptoService.unwrapDek(
                        vaultFile.getDekWrappedClient(),
                        cryptoService.getServerPrivateKey(),
                        client.getPublicKey(),
                        saltBytes,
                        info.getBytes());

                // 4. Decrypt blob (blob already has iv prepended)
                return cryptoService.aesGcmDecryptBytes(dek, encryptedBlob);
            } catch (ResourceNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Download failed", e);
            }
        }, cryptoExecutor);
    }

    @Override
    public VaultFileDTO renameFile(String emailHash, UUID fileId, String newName) {
        VaultFile vaultFile = vaultFileRepository.findByIdAndOwner_EmailHash(fileId, emailHash)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        // Update filename — converter will re-encrypt
        vaultFile.setFilename(newName);
        VaultFile saved = vaultFileRepository.save(vaultFile);
        return toDTO(saved);
    }

    @Override
    public CompletableFuture<VaultFileDTO> replaceFileAsync(String emailHash, UUID fileId, MultipartFile newFile) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Find VaultFile, verify ownership
                VaultFile vaultFile = vaultFileRepository.findByIdAndOwner_EmailHash(fileId, emailHash)
                        .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));

                // Detect MIME type
                Tika tika = new Tika();
                String detectedMimeType = tika.detect(newFile.getInputStream(), newFile.getOriginalFilename());
                FileCategory category = FileCategory.fromMimeType(detectedMimeType);

                // Generate new DEK
                byte[] dek = cryptoService.randomBytes(32);

                // Encrypt new file bytes
                byte[] encryptedBlob = cryptoService.aesGcmEncryptBytes(dek, newFile.getBytes());

                // Generate new salt
                byte[] salt = cryptoService.randomBytes(16);

                // Wrap DEK for client
                Client client = clientRepository.findByEmailHash(emailHash)
                        .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + emailHash));
                String info = "VAULT-v1|dek-wrap|client|emailHash:" + emailHash;
                String dekWrappedClient = cryptoService.wrapDekForRecipient(
                        dek,
                        cryptoService.getServerPrivateKey(),
                        client.getPublicKey(),
                        salt,
                        info.getBytes());

                // Wrap DEK for server
                byte[] serverKek = cryptoService.deriveServerKek(emailHash);
                byte[] wrappedServerBytes = cryptoService.aesGcmEncryptBytes(serverKek, dek);
                String dekWrappedServer = Base64.getEncoder().encodeToString(wrappedServerBytes);

                // Delete old blob, put new blob
                supabaseStorageService.deleteObject(bucket, vaultFile.getStorageKey());
                String newStorageKey = "vault/" + emailHash + "/" + UUID.randomUUID();
                supabaseStorageService.putObject(bucket, newStorageKey, encryptedBlob, detectedMimeType);

                // Extract IV
                byte[] ivBytes = new byte[12];
                System.arraycopy(encryptedBlob, 0, ivBytes, 0, 12);
                String ivBase64 = Base64.getEncoder().encodeToString(ivBytes);
                String saltBase64 = Base64.getEncoder().encodeToString(salt);

                // Update all key material fields in DB
                vaultFile.setContentType(detectedMimeType);
                vaultFile.setCategory(category);
                vaultFile.setStorageKey(newStorageKey);
                vaultFile.setOriginalSize(newFile.getSize());
                vaultFile.setEncryptedSize(encryptedBlob.length);
                vaultFile.setDekWrappedClient(dekWrappedClient);
                vaultFile.setDekWrappedServer(dekWrappedServer);
                vaultFile.setIv(ivBase64);
                vaultFile.setSalt(saltBase64);

                VaultFile saved = vaultFileRepository.save(vaultFile);
                return toDTO(saved);
            } catch (ResourceNotFoundException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Replace failed", e);
            }
        }, cryptoExecutor);
    }

    @Override
    public void deleteFile(String emailHash, UUID fileId) {
        VaultFile vaultFile = vaultFileRepository.findByIdAndOwner_EmailHash(fileId, emailHash)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + fileId));
        supabaseStorageService.deleteObject(bucket, vaultFile.getStorageKey());
        vaultFileRepository.delete(vaultFile);
    }

    private VaultFileDTO toDTO(VaultFile vaultFile) {
        return new VaultFileDTO(
                vaultFile.getId(),
                vaultFile.getFilename(),
                vaultFile.getContentType(),
                vaultFile.getCategory(),
                vaultFile.getOriginalSize(),
                vaultFile.getCreatedAt(),
                vaultFile.getUpdatedAt()
        );
    }
}
