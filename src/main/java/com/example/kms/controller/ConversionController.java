package com.example.kms.controller;

import com.example.kms.dto.ConversionJobDTO;
import com.example.kms.service.FileConversionService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/convert")
public class ConversionController {

    private final FileConversionService fileConversionService;

    public ConversionController(FileConversionService fileConversionService) {
        this.fileConversionService = fileConversionService;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<ConversionJobDTO>> convertUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetFormat") String targetFormat,
            @AuthenticationPrincipal(errorOnInvalidType = false) UserDetails user) {
        try {
            return fileConversionService.convertGuestAsync(file, targetFormat)
                    .thenApply(dto -> ResponseEntity.status(HttpStatus.ACCEPTED).body(dto));
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", "5")
                            .build());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @PostMapping("/vault/{sourceFileId}")
    public CompletableFuture<ResponseEntity<ConversionJobDTO>> convertVaultFile(
            @PathVariable UUID sourceFileId,
            @RequestParam("targetFormat") String targetFormat,
            @AuthenticationPrincipal UserDetails user) {
        String emailHash = user.getUsername();
        try {
            return fileConversionService.convertAndStoreAsync(emailHash, sourceFileId, targetFormat)
                    .thenApply(dto -> ResponseEntity.status(HttpStatus.ACCEPTED).body(dto));
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", "5")
                            .build());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ConversionJobDTO> jobStatus(@PathVariable UUID jobId) {
        return ResponseEntity.ok(fileConversionService.getJobStatus(jobId));
    }

    @GetMapping("/download/{token}")
    public ResponseEntity<Resource> guestDownload(@PathVariable String token) {
        try {
            byte[] bytes = fileConversionService.downloadGuestResult(token);
            ByteArrayResource resource = new ByteArrayResource(bytes);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"converted-file\"")
                    .body(resource);
        } catch (RejectedExecutionException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "5")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
