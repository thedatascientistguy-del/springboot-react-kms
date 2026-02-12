package com.example.kms.repository;

import com.example.kms.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByOwnerId(Long clientId);

    void deleteByCreatedAtBeforeAndIsGuestTrue(LocalDateTime expiryTime);
}
