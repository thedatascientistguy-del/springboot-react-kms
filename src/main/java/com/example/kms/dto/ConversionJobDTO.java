package com.example.kms.dto;

import com.example.kms.model.JobStatus;
import java.util.UUID;

public record ConversionJobDTO(
    UUID jobId,
    JobStatus status,
    String sourceFormat,
    String targetFormat,
    String downloadToken,
    UUID resultFileId,
    String errorMessage
) {}
