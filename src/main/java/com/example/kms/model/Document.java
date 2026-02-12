package com.example.kms.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String contentType;

    @Lob
    @Column(columnDefinition = "BLOB")
    private byte[] data; // Encrypted data

    @Column(name = "original_size")
    private long originalSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client owner; // Nullable for guest users

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // For Guest users, we can track session ID (optional) or just rely on null
    // owner + cleanup task
    @Column(name = "is_guest")
    private boolean isGuest;

    // Encryption metadata (IV, Salt for DEK derivation if needed)
    // Simplified: We will encrypt the whole blob using a DEK, and store the DEK
    // encrypted with Server Key (or Client Key if logged in)
    // reusing the EncryptedData pattern might be complex for large blobs if we want
    // to stream.
    // For now, let's store simpler metadata.

    @Lob
    @Column(columnDefinition = "CLOB")
    private String encryptedDek; // DEK wrapped with Server Key (for guests) or Client Public Key (for users)

    private String iv; // IV for the file content encryption
}
