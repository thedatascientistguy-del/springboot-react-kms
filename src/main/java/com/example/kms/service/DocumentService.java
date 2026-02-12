package com.example.kms.service;

import com.example.kms.model.Client;
import com.example.kms.model.Document;
import com.example.kms.repository.DocumentRepository;
import com.example.kms.util.CryptoUtils;
import org.docx4j.Docx4J;
import org.docx4j.convert.out.FOSettings;
import org.docx4j.fonts.IdentityPlusMapper;
import org.docx4j.fonts.Mapper;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private CryptoService cryptoService;

    @Transactional
    public Document storeDocument(MultipartFile file, Client owner) throws Exception {
        byte[] originalBytes = file.getBytes();
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();

        // 1. Generate a random DEK (32 bytes)
        byte[] dek = cryptoService.randomBytes(32);

        // 2. Encrypt the file content with DEK
        CryptoService.EncryptResult encryptedContent = cryptoService.encryptAndSplit(dek, originalBytes);

        // 3. Wrap the DEK with Server's Public Key (so server can decrypt later for
        // conversion)
        // For simplicity, we just use the Server Key Pair to lock it.
        // We act as both sender (Server) and receiver (Server) for the DEK wrapping in
        // this simplified model
        // so that the server can always open it.
        // In a real E2EE system, we would wrap for the Client's Public Key too, but
        // then Server couldn't convert it without user sending the key back.
        // Given the requirement is "Server converts document", Server must have access.

        // Let's use a simpler approach: Encrypt DEK with a master secret or just use
        // the Server Key.
        // We'll use the existing `wrapDekForRecipient` method treating Server as
        // recipient.
        String serverPubKeyBase64 = cryptoService.getServerPublicKeyBase64();

        // We need a salt and info for HKDF.
        byte[] salt = cryptoService.randomBytes(16);
        byte[] info = "kms-document-storage".getBytes();

        String wrappedDek = cryptoService.wrapDekForRecipient(
                dek,
                cryptoService.getServerPrivateKey(), // We are wrapping it ourselves
                serverPubKeyBase64, // For ourselves
                salt,
                info);

        // 4. Create Document Entity
        Document doc = new Document();
        doc.setFilename(filename);
        doc.setContentType(contentType);
        doc.setOwner(owner);
        doc.setOriginalSize(file.getSize());
        doc.setGuest(owner == null);

        // Store encrypted data
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedContent.getCiphertextBase64());
        doc.setData(encryptedBytes);
        doc.setIv(encryptedContent.getIvBase64());
        doc.setEncryptedDek(wrappedDek);

        // We should store salt/info if we want to unwrap.
        // For simplicity in this demo, we'll append salt to the IV or store it.
        // Let's pack the salt into the encryptedDek string structure or just use a
        // fixed salt/info for this MVP?
        // Better: store it. But I didn't add a field for salt.
        // QUICK FIX: Prepend salt to encryptedDek string: "saltBase64:wrappedDekBase64"
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        doc.setEncryptedDek(saltBase64 + ":" + wrappedDek);

        return documentRepository.save(doc);
    }

    public byte[] retrieveDocumentBytes(UUID documentId) throws Exception {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        return decryptDocument(doc);
    }

    private byte[] decryptDocument(Document doc) throws Exception {
        String[] parts = doc.getEncryptedDek().split(":");
        if (parts.length != 2)
            throw new RuntimeException("Invalid DEK format");

        byte[] salt = Base64.getDecoder().decode(parts[0]);
        String wrappedDek = parts[1];
        byte[] info = "kms-document-storage".getBytes();

        // Unwrap DEK using Server Private Key (since we wrapped it for Server)
        // Sender was also Server.
        String serverPubKeyBase64 = cryptoService.getServerPublicKeyBase64();

        byte[] dek = cryptoService.unwrapDek(
                wrappedDek,
                cryptoService.getServerPrivateKey(),
                serverPubKeyBase64,
                salt,
                info);

        // Decrypt content
        byte[] iv = Base64.getDecoder().decode(doc.getIv());
        byte[] content = doc.getData();

        // The CryptoService expects iv||ciphertext for `aesGcmDecryptBytes`?
        // Let's check CryptoService.aesGcmDecryptBytes:
        // "public byte[] aesGcmDecryptBytes(byte[] key, byte[] ivAndCiphertext)"
        // It expects IV prepended.

        // Need to combine IV + Content
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(iv);
        baos.write(content);
        byte[] ivAndCiphertext = baos.toByteArray();

        return cryptoService.aesGcmDecryptBytes(dek, ivAndCiphertext);
    }

    public Document convertDocument(UUID documentId, String targetFormat) throws Exception {
        // 1. Get original document
        Document originalDoc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (!"application/pdf".equalsIgnoreCase(targetFormat)) {
            // For now only supporting to PDF
            // If the request is for PDF, proceed.
        }

        // 2. Decrypt
        byte[] docxBytes = decryptDocument(originalDoc);

        // 3. Convert (Docx -> PDF)
        ByteArrayInputStream bis = new ByteArrayInputStream(docxBytes);
        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(bis);

        // Map Fonts
        Mapper fontMapper = new IdentityPlusMapper();
        wordMLPackage.setFontMapper(fontMapper);
        // PhysicalFonts.discoverPhysicalFonts(); // Slow, maybe skip if standard fonts
        // used

        FOSettings foSettings = Docx4J.createFOSettings();
        foSettings.setWmlPackage(wordMLPackage);

        ByteArrayOutputStream pdfOs = new ByteArrayOutputStream();
        Docx4J.toFO(foSettings, pdfOs, Docx4J.FLAG_EXPORT_PREFER_XSL);

        byte[] pdfBytes = pdfOs.toByteArray();

        // 4. Store new Document (or just return bytes? Requirement says "website where
        // user can convert... and if signed up save")
        // We will store it as a new document owned by the same user.

        // Reuse store logic but we have bytes, not MultipartFile.
        // Extract logic to helper.
        return storeBytes(pdfBytes, originalDoc.getFilename() + ".pdf", "application/pdf", originalDoc.getOwner());
    }

    // Helper to store raw bytes
    private Document storeBytes(byte[] data, String filename, String contentType, Client owner) throws Exception {
        byte[] dek = cryptoService.randomBytes(32);
        CryptoService.EncryptResult encryptedContent = cryptoService.encryptAndSplit(dek, data);

        String serverPubKeyBase64 = cryptoService.getServerPublicKeyBase64();
        byte[] salt = cryptoService.randomBytes(16);
        byte[] info = "kms-document-storage".getBytes();

        String wrappedDek = cryptoService.wrapDekForRecipient(
                dek,
                cryptoService.getServerPrivateKey(),
                serverPubKeyBase64,
                salt,
                info);

        Document doc = new Document();
        doc.setFilename(filename);
        doc.setContentType(contentType);
        doc.setOwner(owner);
        doc.setOriginalSize(data.length);
        doc.setGuest(owner == null);

        doc.setData(Base64.getDecoder().decode(encryptedContent.getCiphertextBase64()));
        doc.setIv(encryptedContent.getIvBase64());
        doc.setEncryptedDek(Base64.getEncoder().encodeToString(salt) + ":" + wrappedDek);

        return documentRepository.save(doc);
    }

    public List<Document> getDocumentsForClient(Client client) {
        return documentRepository.findByOwnerId(client.getId());
    }

    public Document getDocument(UUID id) {
        return documentRepository.findById(id).orElse(null);
    }

    // Cleanup Task: Runs every minute
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void deleteExpiredGuestDocuments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        documentRepository.deleteByCreatedAtBeforeAndIsGuestTrue(cutoff);
    }
}
