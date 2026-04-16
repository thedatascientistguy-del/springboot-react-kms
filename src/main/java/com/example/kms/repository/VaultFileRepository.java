package com.example.kms.repository;

import com.example.kms.model.VaultFile;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaultFileRepository extends JpaRepository<VaultFile, UUID> {

    List<VaultFile> findAllByOwner_EmailHash(String emailHash);

    Optional<VaultFile> findByIdAndOwner_EmailHash(UUID id, String emailHash);

    Optional<VaultFile> findByGuestSessionToken(String guestSessionToken);

    List<VaultFile> findByExpiresAtBefore(LocalDateTime dateTime);

    @Modifying
    @Transactional
    void deleteByIdAndOwner_EmailHash(UUID id, String emailHash);
}
