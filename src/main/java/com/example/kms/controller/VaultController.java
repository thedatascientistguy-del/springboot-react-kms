package com.example.kms.controller;

import com.example.kms.dto.RenameRequest;
import com.example.kms.dto.VaultFileDTO;
import com.example.kms.service.VaultService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @PostMapping("/upload")
    public CompletableFuture<ResponseEntity<VaultFileDTO>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails user) {
        String emailHash = user.getUsername();
        try {
            return vaultService.uploadFileAsync(emailHash, file)
                    .thenApply(ResponseEntity::ok);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @GetMapping("/files")
    public ResponseEntity<List<VaultFileDTO>> listFiles(@AuthenticationPrincipal UserDetails user) {
        String emailHash = user.getUsername();
        return ResponseEntity.ok(vaultService.listFiles(emailHash));
    }

    @GetMapping("/files/{id}/download")
    public CompletableFuture<ResponseEntity<Resource>> download(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        String emailHash = user.getUsername();
        try {
            return vaultService.downloadFileAsync(emailHash, id)
                    .thenApply(bytes -> {
                        ByteArrayResource resource = new ByteArrayResource(bytes);
                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                                .body((Resource) resource);
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @PatchMapping("/files/{id}/rename")
    public ResponseEntity<VaultFileDTO> rename(
            @PathVariable UUID id,
            @RequestBody RenameRequest req,
            @AuthenticationPrincipal UserDetails user) {
        String emailHash = user.getUsername();
        VaultFileDTO updated = vaultService.renameFile(emailHash, id, req.newName());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/files/{id}/replace")
    public CompletableFuture<ResponseEntity<VaultFileDTO>> replace(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails user) {
        String emailHash = user.getUsername();
        try {
            return vaultService.replaceFileAsync(emailHash, id, file)
                    .thenApply(ResponseEntity::ok);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        String emailHash = user.getUsername();
        vaultService.deleteFile(emailHash, id);
        return ResponseEntity.noContent().build();
    }
}
