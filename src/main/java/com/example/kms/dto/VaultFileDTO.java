package com.example.kms.dto;

import com.example.kms.model.FileCategory;
import java.time.LocalDateTime;
import java.util.UUID;

public record VaultFileDTO(
    UUID id,
    String filename,
    String contentType,
    FileCategory category,
    long originalSize,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
