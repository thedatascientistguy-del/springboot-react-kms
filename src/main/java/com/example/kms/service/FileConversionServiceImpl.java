package com.example.kms.service;

import com.example.kms.dto.ConversionJobDTO;
import com.example.kms.exception.ResourceNotFoundException;
import com.example.kms.exception.UnsupportedConversionException;
import com.example.kms.exception.UnsupportedFileTypeException;
import com.example.kms.model.*;
import com.example.kms.repository.ClientRepository;
import com.example.kms.repository.ConversionJobRepository;
import com.example.kms.repository.VaultFileRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static java.util.Map.entry;

@Service
public class FileConversionServiceImpl implements FileConversionService {

    private final ConversionJobRepository conversionJobRepository;
    private final VaultFileRepository vaultFileRepository;
    private final ClientRepository clientRepository;
    private final CryptoService cryptoService;
    private final SupabaseStorageService supabaseStorageService;
    private final GuestCacheService guestCacheService;
    private final Executor cryptoExecutor;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    public FileConversionServiceImpl(
            ConversionJobRepository conversionJobRepository,
            VaultFileRepository vaultFileRepository,
            ClientRepository clientRepository,
            CryptoService cryptoService,
            SupabaseStorageService supabaseStorageService,
            GuestCacheService guestCacheService,
            @Qualifier("cryptoExecutor") Executor cryptoExecutor) {
        this.conversionJobRepository = conversionJobRepository;
        this.vaultFileRepository = vaultFileRepository;
        this.clientRepository = clientRepository;
        this.cryptoService = cryptoService;
        this.supabaseStorageService = supabaseStorageService;
        this.guestCacheService = guestCacheService;
        this.cryptoExecutor = cryptoExecutor;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<ConversionJobDTO> convertGuestAsync(MultipartFile file, String targetFormat) throws Exception {
        // Create job immediately and return
        ConversionJob job = ConversionJob.builder()
                .sourceFormat(formatFromMimeType(file.getContentType()))
                .targetFormat(targetFormat.toLowerCase().trim())
                .status(JobStatus.PENDING)
                .build();
        ConversionJob saved = conversionJobRepository.save(job);

        CompletableFuture.runAsync(() -> {
            try {
                // Update to PROCESSING
                saved.setStatus(JobStatus.PROCESSING);
                conversionJobRepository.save(saved);

                String sourceFormat = formatFromMimeType(file.getContentType());
                String tgt = targetFormat.toLowerCase().trim();

                byte[] inputBytes = file.getBytes();
                byte[] converted = convertBytes(inputBytes, sourceFormat, tgt);

                // Encrypt output with temp DEK
                byte[] tempDek = cryptoService.randomBytes(32);
                byte[] encryptedOutput = cryptoService.aesGcmEncryptBytes(tempDek, converted);

                // Extract IV (first 12 bytes) and encode as Base64
                byte[] ivBytes = new byte[12];
                System.arraycopy(encryptedOutput, 0, ivBytes, 0, 12);
                String ivBase64 = Base64.getEncoder().encodeToString(ivBytes);

                // Store in guest cache
                String token = guestCacheService.store(encryptedOutput, tempDek, ivBase64, Duration.ofMinutes(30));

                // Update job to DONE
                saved.setStatus(JobStatus.DONE);
                saved.setDownloadToken(token);
                saved.setCompletedAt(LocalDateTime.now());
                conversionJobRepository.save(saved);
            } catch (Exception e) {
                saved.setStatus(JobStatus.FAILED);
                saved.setErrorMessage(e.getMessage());
                conversionJobRepository.save(saved);
            }
        }, cryptoExecutor);

        return CompletableFuture.completedFuture(toDTO(saved));
    }

    @Override
    public CompletableFuture<ConversionJobDTO> convertAndStoreAsync(String emailHash, UUID sourceFileId, String targetFormat) throws Exception {
        VaultFile sourceFile = vaultFileRepository.findById(sourceFileId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found: " + sourceFileId));

        ConversionJob job = ConversionJob.builder()
                .sourceFile(sourceFile)
                .sourceFormat(sourceFile.getContentType())
                .targetFormat(targetFormat.toLowerCase().trim())
                .status(JobStatus.PENDING)
                .build();
        ConversionJob saved = conversionJobRepository.save(job);

        CompletableFuture.runAsync(() -> {
            try {
                saved.setStatus(JobStatus.PROCESSING);
                conversionJobRepository.save(saved);

                // Fetch encrypted blob from Supabase
                byte[] encryptedBlob = supabaseStorageService.getObject(bucket, sourceFile.getStorageKey());

                // Unwrap server DEK
                byte[] serverKek = cryptoService.deriveServerKek(emailHash);
                byte[] wrappedDekBytes = Base64.getDecoder().decode(sourceFile.getDekWrappedServer());
                byte[] dek = cryptoService.aesGcmDecryptBytes(serverKek, wrappedDekBytes);

                // Decrypt source file
                byte[] plaintext = cryptoService.aesGcmDecryptBytes(dek, encryptedBlob);

                // Determine source format from content type
                String srcFormat = formatFromMimeType(sourceFile.getContentType());
                String tgt = targetFormat.toLowerCase().trim();

                // Convert
                byte[] converted = convertBytes(plaintext, srcFormat, tgt);

                // Re-encrypt with new DEK
                byte[] newDek = cryptoService.randomBytes(32);
                byte[] newEncryptedBlob = cryptoService.aesGcmEncryptBytes(newDek, converted);

                // Wrap new DEK for server
                byte[] newServerKek = cryptoService.deriveServerKek(emailHash);
                byte[] wrappedNewDekBytes = cryptoService.aesGcmEncryptBytes(newServerKek, newDek);
                String dekWrappedServer = Base64.getEncoder().encodeToString(wrappedNewDekBytes);

                // Wrap new DEK for client
                Client client = clientRepository.findByEmailHash(emailHash)
                        .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + emailHash));
                byte[] salt = cryptoService.randomBytes(16);
                String info = "VAULT-v1|dek-wrap|client|emailHash:" + emailHash;
                String dekWrappedClient = cryptoService.wrapDekForRecipient(
                        newDek,
                        cryptoService.getServerPrivateKey(),
                        client.getPublicKey(),
                        salt,
                        info.getBytes());

                // Build result filename
                String originalName = sourceFile.getFilename();
                String baseName = originalName.contains(".")
                        ? originalName.substring(0, originalName.lastIndexOf('.'))
                        : originalName;
                String resultFilename = baseName + "." + tgt;

                // Extract IV
                byte[] ivBytes = new byte[12];
                System.arraycopy(newEncryptedBlob, 0, ivBytes, 0, 12);
                String ivBase64 = Base64.getEncoder().encodeToString(ivBytes);
                String saltBase64 = Base64.getEncoder().encodeToString(salt);

                // Store in Supabase
                String storageKey = "vault/" + emailHash + "/" + UUID.randomUUID();
                String resultMimeType = mimeTypeFromFormat(tgt);
                supabaseStorageService.putObject(bucket, storageKey, newEncryptedBlob, resultMimeType);

                // Persist new VaultFile
                FileCategory category = FileCategory.fromMimeType(resultMimeType);
                VaultFile resultFile = VaultFile.builder()
                        .owner(client)
                        .filename(resultFilename)
                        .contentType(resultMimeType)
                        .category(category)
                        .storageKey(storageKey)
                        .originalSize(converted.length)
                        .encryptedSize(newEncryptedBlob.length)
                        .dekWrappedClient(dekWrappedClient)
                        .dekWrappedServer(dekWrappedServer)
                        .iv(ivBase64)
                        .salt(saltBase64)
                        .guest(false)
                        .build();
                VaultFile savedResult = vaultFileRepository.save(resultFile);

                // Update job to DONE
                saved.setStatus(JobStatus.DONE);
                saved.setResultFile(savedResult);
                saved.setCompletedAt(LocalDateTime.now());
                conversionJobRepository.save(saved);
            } catch (Exception e) {
                saved.setStatus(JobStatus.FAILED);
                saved.setErrorMessage(e.getMessage());
                conversionJobRepository.save(saved);
            }
        }, cryptoExecutor);

        return CompletableFuture.completedFuture(toDTO(saved));
    }

    @Override
    public ConversionJobDTO getJobStatus(UUID jobId) {
        ConversionJob job = conversionJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        return toDTO(job);
    }

    @Override
    public byte[] downloadGuestResult(String downloadToken) throws Exception {
        return guestCacheService.retrieveAndConsume(downloadToken);
    }

    // -------------------------------------------------------------------------
    // Conversion routing
    // -------------------------------------------------------------------------

    private byte[] convertBytes(byte[] input, String sourceFormat, String targetFormat) throws Exception {
        String src = sourceFormat.toLowerCase().trim();
        String tgt = targetFormat.toLowerCase().trim();

        // Document conversions
        if (src.equals("docx") && tgt.equals("pdf")) return docxToPdf(input);
        if (src.equals("pdf") && tgt.equals("docx")) return pdfToDocx(input);
        if (src.equals("csv") && tgt.equals("xlsx")) return csvToXlsx(input);
        if (src.equals("xlsx") && tgt.equals("csv")) return xlsxToCsv(input);
        if (src.equals("txt") && tgt.equals("pdf")) return txtToPdf(input);

        // Image conversions
        Set<String> imageFormats = Set.of("jpg", "jpeg", "png", "webp", "bmp", "gif");
        if (imageFormats.contains(src) && imageFormats.contains(tgt)) return convertImage(input, src, tgt);

        // Audio conversions
        Set<String> audioFormats = Set.of("mp3", "wav", "flac", "aac", "ogg");
        if (audioFormats.contains(src) && audioFormats.contains(tgt)) return convertAudioVideo(input, src, tgt);

        // Video conversions
        Set<String> videoFormats = Set.of("mp4", "avi", "mov", "mkv");
        if (videoFormats.contains(src) && videoFormats.contains(tgt)) return convertAudioVideo(input, src, tgt);

        throw new UnsupportedConversionException(src + " → " + tgt);
    }

    // -------------------------------------------------------------------------
    // Document converters
    // -------------------------------------------------------------------------

    private byte[] docxToPdf(byte[] docxBytes) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new ByteArrayInputStream(docxBytes));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Docx4J.toPDF(pkg, out);
        return out.toByteArray();
    }

    private byte[] pdfToDocx(byte[] pdfBytes) throws Exception {
        PDDocument doc = Loader.loadPDF(pdfBytes);
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(doc);
        doc.close();

        XWPFDocument docx = new XWPFDocument();
        XWPFParagraph para = docx.createParagraph();
        XWPFRun run = para.createRun();
        run.setText(text);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        docx.write(out);
        docx.close();
        return out.toByteArray();
    }

    private byte[] csvToXlsx(byte[] csvBytes) throws Exception {
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");
        for (int i = 0; i < lines.length; i++) {
            Row row = sheet.createRow(i);
            String[] cells = lines[i].split(",");
            for (int j = 0; j < cells.length; j++) {
                row.createCell(j).setCellValue(cells[j].trim());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }

    private byte[] xlsxToCsv(byte[] xlsxBytes) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes));
        Sheet sheet = workbook.getSheetAt(0);
        StringBuilder sb = new StringBuilder();
        for (Row row : sheet) {
            for (int i = 0; i < row.getLastCellNum(); i++) {
                if (i > 0) sb.append(",");
                Cell cell = row.getCell(i);
                sb.append(cell != null ? cell.toString() : "");
            }
            sb.append("\n");
        }
        workbook.close();
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] txtToPdf(byte[] txtBytes) throws Exception {
        String text = new String(txtBytes, java.nio.charset.StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph(text));
        document.close();
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Image converter
    // -------------------------------------------------------------------------

    private byte[] convertImage(byte[] inputBytes, String sourceFormat, String targetFormat) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(inputBytes));
        if (image == null) throw new UnsupportedConversionException("Cannot read image: " + sourceFormat);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String writerFormat = targetFormat.equals("jpg") ? "jpeg" : targetFormat;
        ImageIO.write(image, writerFormat, out);
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Audio/Video converter (JAVE2)
    // -------------------------------------------------------------------------

    private byte[] convertAudioVideo(byte[] inputBytes, String sourceFormat, String targetFormat) throws Exception {
        File inputFile = File.createTempFile("vault-conv-in-", "." + sourceFormat);
        File outputFile = File.createTempFile("vault-conv-out-", "." + targetFormat);
        try {
            Files.write(inputFile.toPath(), inputBytes);
            AudioAttributes audio = new AudioAttributes();
            Encoder encoder = new Encoder();
            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat(targetFormat);
            attrs.setAudioAttributes(audio);
            encoder.encode(new MultimediaObject(inputFile), outputFile, attrs);
            return Files.readAllBytes(outputFile.toPath());
        } finally {
            inputFile.delete();
            outputFile.delete();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String formatFromMimeType(String mimeType) {
        if (mimeType == null) throw new UnsupportedFileTypeException("null");
        Map<String, String> map = Map.ofEntries(
            entry("application/pdf", "pdf"),
            entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            entry("text/plain", "txt"),
            entry("text/csv", "csv"),
            entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            entry("image/jpeg", "jpg"),
            entry("image/png", "png"),
            entry("image/webp", "webp"),
            entry("image/bmp", "bmp"),
            entry("image/gif", "gif"),
            entry("audio/mpeg", "mp3"),
            entry("audio/wav", "wav"),
            entry("audio/flac", "flac"),
            entry("audio/aac", "aac"),
            entry("audio/ogg", "ogg"),
            entry("video/mp4", "mp4"),
            entry("video/x-msvideo", "avi"),
            entry("video/quicktime", "mov"),
            entry("video/x-matroska", "mkv")
        );
        return Optional.ofNullable(map.get(mimeType.toLowerCase()))
                .orElseThrow(() -> new UnsupportedFileTypeException(mimeType));
    }

    private String mimeTypeFromFormat(String format) {
        Map<String, String> map = Map.ofEntries(
            entry("pdf", "application/pdf"),
            entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            entry("txt", "text/plain"),
            entry("csv", "text/csv"),
            entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            entry("jpg", "image/jpeg"),
            entry("jpeg", "image/jpeg"),
            entry("png", "image/png"),
            entry("webp", "image/webp"),
            entry("bmp", "image/bmp"),
            entry("gif", "image/gif"),
            entry("mp3", "audio/mpeg"),
            entry("wav", "audio/wav"),
            entry("flac", "audio/flac"),
            entry("aac", "audio/aac"),
            entry("ogg", "audio/ogg"),
            entry("mp4", "video/mp4"),
            entry("avi", "video/x-msvideo"),
            entry("mov", "video/quicktime"),
            entry("mkv", "video/x-matroska")
        );
        return map.getOrDefault(format.toLowerCase(), "application/octet-stream");
    }

    private ConversionJobDTO toDTO(ConversionJob job) {
        UUID resultFileId = job.getResultFile() != null ? job.getResultFile().getId() : null;
        return new ConversionJobDTO(
                job.getId(),
                job.getStatus(),
                job.getSourceFormat(),
                job.getTargetFormat(),
                job.getDownloadToken(),
                resultFileId,
                job.getErrorMessage()
        );
    }
}
