package com.example.kms.service;

import com.example.kms.exception.GuestTokenExpiredException;
import com.example.kms.model.FileCategory;
import com.example.kms.model.VaultFile;
import com.example.kms.repository.VaultFileRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class GuestCacheServiceImpl implements GuestCacheService {

    private final VaultFileRepository vaultFileRepository;
    private final CryptoService cryptoService;
    private final SecureRandom secureRandom = new SecureRandom();

    public GuestCacheServiceImpl(VaultFileRepository vaultFileRepository, CryptoService cryptoService) {
        this.vaultFileRepository = vaultFileRepository;
        this.cryptoService = cryptoService;
    }

    @Override
    public String store(byte[] encryptedBytes, byte[] tempDek, String iv, Duration ttl) {
        // Generate 32-byte hex download token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : tokenBytes) {
            sb.append(String.format("%02x", b));
        }
        String downloadToken = sb.toString();

        VaultFile guestFile = VaultFile.builder()
                .guest(true)
                .guestSessionToken(downloadToken)
                .expiresAt(LocalDateTime.now().plus(ttl))
                .storageKey("guest-cache/" + downloadToken)
                .dekWrappedServer(Base64.getEncoder().encodeToString(encryptedBytes))
                .dekWrappedClient(Base64.getEncoder().encodeToString(tempDek))
                .iv(iv)
                .filename("guest-conversion")
                .contentType("application/octet-stream")
                .category(FileCategory.DOCUMENT)
                .originalSize(encryptedBytes.length)
                .encryptedSize(encryptedBytes.length)
                .salt("")
                .build();

        vaultFileRepository.save(guestFile);
        return downloadToken;
    }

    @Override
    public byte[] retrieveAndConsume(String downloadToken) throws Exception {
        VaultFile guestFile = vaultFileRepository.findByGuestSessionToken(downloadToken)
                .orElseThrow(() -> new GuestTokenExpiredException("Guest download token not found or already consumed"));

        if (guestFile.getGuestSessionToken() == null) {
            throw new GuestTokenExpiredException("Guest download token already consumed");
        }

        if (guestFile.getExpiresAt() != null && guestFile.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new GuestTokenExpiredException("Guest download token has expired");
        }

        byte[] tempDek = Base64.getDecoder().decode(guestFile.getDekWrappedClient());
        byte[] encryptedBytes = Base64.getDecoder().decode(guestFile.getDekWrappedServer());
        String ivStr = guestFile.getIv();

        // Reconstruct iv||ciphertext for aesGcmDecryptBytes
        byte[] ivBytes = Base64.getDecoder().decode(ivStr);
        byte[] ivAndCiphertext = new byte[ivBytes.length + encryptedBytes.length];
        System.arraycopy(ivBytes, 0, ivAndCiphertext, 0, ivBytes.length);
        System.arraycopy(encryptedBytes, 0, ivAndCiphertext, ivBytes.length, encryptedBytes.length);

        byte[] plaintext = cryptoService.aesGcmDecryptBytes(tempDek, ivAndCiphertext);

        // Invalidate token (one-time use)
        guestFile.setGuestSessionToken(null);
        vaultFileRepository.save(guestFile);

        return plaintext;
    }

    @Override
    @Scheduled(fixedRate = 300_000)
    public void purgeExpired() {
        List<VaultFile> expired = vaultFileRepository.findByExpiresAtBefore(LocalDateTime.now());
        List<VaultFile> expiredGuests = expired.stream()
                .filter(VaultFile::isGuest)
                .toList();
        vaultFileRepository.deleteAll(expiredGuests);
    }
}
