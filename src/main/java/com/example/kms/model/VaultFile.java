package com.example.kms.model;

import com.example.kms.util.EncryptDecryptConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vault_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VaultFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client owner; // null for guests

    @Convert(converter = EncryptDecryptConverter.class)
    @Column(name = "filename_enc", nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_category", nullable = false)
    private FileCategory category;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_size", nullable = false)
    private long originalSize;

    @Column(name = "encrypted_size", nullable = false)
    private long encryptedSize;

    @Column(name = "dek_wrapped_client", nullable = false)
    private String dekWrappedClient;

    @Column(name = "dek_wrapped_server")
    private String dekWrappedServer;

    @Column(nullable = false)
    private String iv;

    @Column(nullable = false)
    private String salt;

    @Column(name = "is_guest", nullable = false)
    private boolean guest;

    @Column(name = "guest_session_token")
    private String guestSessionToken;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
