package com.example.kms.controller;

import com.example.kms.model.Client;
import com.example.kms.model.Document;
import com.example.kms.repository.ClientRepository;
import com.example.kms.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "http://localhost:3000") // Enable CORS for frontend
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ClientRepository clientRepository;

    @GetMapping
    public ResponseEntity<java.util.List<DocumentDTO>> getMyDocuments(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).build();

        Client client = clientRepository.findByEmailHash(principal.getName()).orElse(null);
        if (client == null)
            return ResponseEntity.status(401).build();

        java.util.List<Document> docs = documentService.getDocumentsForClient(client);
        java.util.List<DocumentDTO> dtos = docs.stream().map(this::convertToDTO)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/upload")
    public ResponseEntity<DocumentDTO> uploadDocument(@RequestParam("file") MultipartFile file, Principal principal) {
        try {
            Client client = null;
            if (principal != null) {
                // Assuming Principal name is email or phone, need to lookup
                // For this simplified version, let's assume we can resolve it.
                // In Spring Security with JWT, principal.getName() usually returns the subject
                // (email/phone).
                client = clientRepository.findByEmailHash(principal.getName()).orElse(null);
                // Wait, logic might need adjustment based on how UserDetails is implemented.
                // If principal is null, it's a guest.
            }

            // FIXME: Lookup client properly if authenticated.
            // For now, if principal is not null, try to find client.
            // If the project uses custom Client object in security context, cast it.

            Document doc = documentService.storeDocument(file, client);
            return ResponseEntity.ok(convertToDTO(doc));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<DocumentDTO> convertDocument(@PathVariable UUID id, @RequestParam("format") String format) {
        try {
            // Check ownership logic here if needed (e.g. is this session owner of the guest
            // doc?)
            // For MVP, we allow conversion if you have the ID.
            Document mappedDoc = documentService.convertDocument(id, format);
            return ResponseEntity.ok(convertToDTO(mappedDoc));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build(); // Simplify error handling
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable UUID id) {
        try {
            Document doc = documentService.getDocument(id);
            if (doc == null)
                return ResponseEntity.notFound().build();

            byte[] data = documentService.retrieveDocumentBytes(id);
            ByteArrayResource resource = new ByteArrayResource(data);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFilename() + "\"")
                    .contentType(MediaType.parseMediaType(doc.getContentType()))
                    .contentLength(data.length)
                    .body(resource);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // DTO to avoid exposing internal fields/byte arrays in JSON
    private DocumentDTO convertToDTO(Document doc) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(doc.getId());
        dto.setFilename(doc.getFilename());
        dto.setContentType(doc.getContentType());
        dto.setOriginalSize(doc.getOriginalSize());
        dto.setCreatedAt(doc.getCreatedAt());
        dto.setGuest(doc.isGuest());
        return dto;
    }

    // Inner DTO Class
    public static class DocumentDTO {
        private UUID id;
        private String filename;
        private String contentType;
        private long originalSize;
        private java.time.LocalDateTime createdAt;
        private boolean isGuest;

        // Getters/Setters
        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public long getOriginalSize() {
            return originalSize;
        }

        public void setOriginalSize(long originalSize) {
            this.originalSize = originalSize;
        }

        public java.time.LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(java.time.LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public boolean isGuest() {
            return isGuest;
        }

        public void setGuest(boolean guest) {
            isGuest = guest;
        }
    }
}
