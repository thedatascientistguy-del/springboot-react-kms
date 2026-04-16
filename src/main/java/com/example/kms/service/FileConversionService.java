package com.example.kms.service;

import com.example.kms.dto.ConversionJobDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FileConversionService {
    CompletableFuture<ConversionJobDTO> convertGuestAsync(MultipartFile file, String targetFormat) throws Exception;
    CompletableFuture<ConversionJobDTO> convertAndStoreAsync(String emailHash, UUID sourceFileId, String targetFormat) throws Exception;
    ConversionJobDTO getJobStatus(UUID jobId);
    byte[] downloadGuestResult(String downloadToken) throws Exception;
}
