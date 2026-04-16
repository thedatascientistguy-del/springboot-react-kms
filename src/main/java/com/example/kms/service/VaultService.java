package com.example.kms.service;

import com.example.kms.dto.VaultFileDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface VaultService {
    CompletableFuture<VaultFileDTO> uploadFileAsync(String emailHash, MultipartFile file) throws Exception;
    List<VaultFileDTO> listFiles(String emailHash);
    CompletableFuture<byte[]> downloadFileAsync(String emailHash, UUID fileId) throws Exception;
    VaultFileDTO renameFile(String emailHash, UUID fileId, String newName);
    CompletableFuture<VaultFileDTO> replaceFileAsync(String emailHash, UUID fileId, MultipartFile newFile) throws Exception;
    void deleteFile(String emailHash, UUID fileId);
}
