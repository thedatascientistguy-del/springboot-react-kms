# Implementation Plan: Secure Cloud Vault

## Overview

Incremental implementation of the Secure Cloud Vault feature on top of the existing Spring Boot KMS backend and the `kms-frontend` React app. Each task builds on the previous, ending with full integration. The backend is Java 17 / Spring Boot 3; the frontend is React 18 + TypeScript + Vite.

## Tasks

- [x] 1. Backend foundation — dependencies, config, migrations, async
  - [x] 1.1 Add new Maven dependencies to `pom.xml`
    - Add `postgresql`, `spring-boot-starter-webflux`, `poi-ooxml`, `pdfbox`, `openpdf`, `imageio-webp`, `jave-all-deps`, `flyway-core`, `tika-core`
    - _Requirements: 7.1, 7.5, 5.1–5.7_
  - [x] 1.2 Update `application.yml` with Supabase datasource, JPA validate mode, Supabase storage config, and file size limits
    - Replace H2 datasource with PostgreSQL; add `supabase.*` properties backed by env vars; set `ddl-auto: validate`; add `spring.servlet.multipart.max-file-size`
    - _Requirements: 7.1, 8.3, 8.4, 8.5_
  - [x] 1.3 Create `AsyncConfig.java` with `fileIoExecutor` and `cryptoExecutor` thread pools and `@EnableAsync`
    - `fileIoExecutor`: core=10, max=50, queue=200; `cryptoExecutor`: core=4, max=2×processors, queue=100
    - _Requirements: 9.1, 9.2_
  - [x] 1.4 Write Flyway migration `V2__vault_schema.sql` creating `vault_files` and `conversion_jobs` tables; add `phone_hash` column to `clients` if missing
    - Include all columns from the data model; add indexes on `client_id`, `email_hash`, `guest_session_token`, `download_token`
    - _Requirements: 7.5, 2.1, 3.2, 6.4_

- [x] 2. Column encryption upgrade — AES-ECB → AES-GCM
  - [x] 2.1 Rewrite `EncryptDecryptConverter` to use AES-256-GCM with a random 12-byte IV prepended to each ciphertext; load master key from `VAULT_COLUMN_MASTER_KEY` env var
    - Output format: `Base64(iv[12] || ciphertext)`; decrypt by splitting first 12 bytes as IV
    - _Requirements: 6.1, 6.2, 6.3_
  - [ ]* 2.2 Write property test for column encryption round-trip (Property 4)
    - **Property 4: Column Encryption Round-Trip** — for any string, `decrypt(encrypt(s)) == s`
    - **Validates: Requirements 6.1, 6.2**
  - [ ]* 2.3 Write property test for column encryption non-determinism (Property 3)
    - **Property 3: Column Encryption Non-Determinism** — `encrypt(s) != encrypt(s)` for two independent calls, yet both decrypt to `s`
    - **Validates: Requirements 6.1, 6.2**

- [x] 3. New entities, enums, and repositories
  - [x] 3.1 Create `FileCategory.java` enum with `DOCUMENT`, `IMAGE`, `VIDEO`, `AUDIO` values and `fromMimeType(String)` static factory using the full MIME map from the design
    - Throw `UnsupportedFileTypeException` for unknown MIME types
    - _Requirements: 5.9, 5.10_
  - [ ]* 3.2 Write property test for `FileCategory.fromMimeType` (Property 10)
    - **Property 10: File Category Classification** — every MIME in the supported set maps to the correct enum; every unsupported MIME throws `UnsupportedFileTypeException`
    - **Validates: Requirements 5.9, 5.10**
  - [x] 3.3 Create `VaultFile.java` entity with all columns from the data model; use `@Convert(converter = EncryptDecryptConverter.class)` on `filename`
    - _Requirements: 2.1, 2.4, 6.7_
  - [x] 3.4 Create `JobStatus.java` enum (`PENDING`, `PROCESSING`, `DONE`, `FAILED`) and `ConversionJob.java` entity
    - _Requirements: 4.3, 4.4_
  - [x] 3.5 Create `VaultFileRepository` and `ConversionJobRepository` Spring Data JPA interfaces
    - Add `findByIdAndOwnerEmailHash`, `findAllByOwnerEmailHash`, `findByDownloadToken`, `findByGuestSessionToken`, `findByExpiresAtBefore` query methods
    - _Requirements: 2.5, 2.6, 3.4, 3.7_

- [x] 4. CryptoService extensions
  - [x] 4.1 Add `encryptColumnValue(String)` and `decryptColumnValue(String)` methods to `CryptoService` using AES-256-GCM and the master key from env
    - These are the programmatic equivalents of the converter for use in service code
    - _Requirements: 6.1, 6.2, 6.3_
  - [x] 4.2 Add `deriveServerKek(String emailHash)` to `CryptoService` using `HKDF(masterServerKey, salt, "VAULT-v1|dek-wrap|server|emailHash:{hash}", 32)`
    - _Requirements: 2.3_
  - [ ]* 4.3 Write property test for crypto round-trip (Property 1 proxy at unit level)
    - **Property 1 (unit): File Encryption Round-Trip** — for any `byte[]` P and random 32-byte key K, `aesGcmDecryptBytes(K, aesGcmEncryptBytes(K, P))` equals P
    - **Validates: Requirements 2.1, 2.6**

- [x] 5. SupabaseStorageService implementation
  - [x] 5.1 Create `SupabaseStorageService` interface and `SupabaseStorageServiceImpl` using `WebClient` with the service-role key
    - Implement `putObject`, `getObject`, `deleteObject` against the Supabase Storage REST API; throw `StorageUnavailableException` on connection errors
    - _Requirements: 7.2, 7.3, 7.4, 7.6_
  - [ ]* 5.2 Write unit tests for `SupabaseStorageServiceImpl` with a mocked `WebClient`
    - Test successful put/get/delete and `StorageUnavailableException` on 5xx responses
    - _Requirements: 7.2, 7.3, 7.4, 7.6_

- [x] 6. GuestCacheService implementation
  - [x] 6.1 Create `GuestCacheService` interface and `GuestCacheServiceImpl`
    - `store`: generate 32-byte hex download token, persist encrypted bytes + tempDek + iv + `expires_at = now + TTL` to `vault_files` with `is_guest=true`; return token
    - `retrieveAndConsume`: look up by token, check expiry, decrypt, null out token (invalidate), return plaintext bytes; throw `GuestTokenExpiredException` if expired or already consumed
    - `purgeExpired`: `@Scheduled(fixedRate = 300_000)` deletes rows where `is_guest=true AND expires_at < now()`
    - _Requirements: 3.3, 3.4, 3.5, 3.6, 3.7, 8.6, 8.7_
  - [ ]* 6.2 Write property test for guest token single-use (Property 6)
    - **Property 6: Guest Token Single-Use** — first `retrieveAndConsume` succeeds; every subsequent call with the same token throws `GuestTokenExpiredException`
    - **Validates: Requirements 3.5, 8.6, 8.7**
  - [ ]* 6.3 Write property test for guest token uniqueness (Property 8)
    - **Property 8: Guest Token Uniqueness** — two independent `store` calls always produce different tokens
    - **Validates: Requirements 8.6**

- [x] 7. VaultService implementation
  - [x] 7.1 Create `VaultService` interface and `VaultServiceImpl` — implement `uploadFileAsync`
    - Validate MIME via Apache Tika; generate DEK; encrypt file bytes; wrap DEK for client and server; store blob via `SupabaseStorageService`; persist `VaultFile`; return `VaultFileDTO`
    - Storage key format: `vault/{emailHash}/{randomUUID}` — never use original filename in key
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 8.1, 8.2, 9.3_
  - [x] 7.2 Implement `listFiles` in `VaultServiceImpl`
    - Query `VaultFileRepository.findAllByOwnerEmailHash`; decrypt filename via converter; map to `VaultFileDTO` (no key material in DTO)
    - _Requirements: 2.5_
  - [x] 7.3 Implement `downloadFileAsync` in `VaultServiceImpl`
    - Fetch blob from Supabase; unwrap DEK using client X25519 key; decrypt blob; return plaintext bytes; throw 404 if file not owned by caller
    - _Requirements: 2.6, 2.7_
  - [x] 7.4 Implement `renameFile` in `VaultServiceImpl`
    - Re-encrypt new filename with `EncryptDecryptConverter`; update only `filename_enc`; leave all key material columns unchanged
    - _Requirements: 2.8_
  - [ ]* 7.5 Write property test for rename preserves key material (Property 12)
    - **Property 12: Rename Preserves Key Material** — after rename, `dek_wrapped_client`, `dek_wrapped_server`, `iv`, `salt`, `storage_key` are byte-for-byte identical to pre-rename values
    - **Validates: Requirements 2.8**
  - [x] 7.6 Implement `replaceFileAsync` in `VaultServiceImpl`
    - Generate new DEK; re-encrypt new file bytes; update Supabase blob; update `dek_wrapped_client`, `dek_wrapped_server`, `iv`, `salt` in DB
    - _Requirements: 2.9_
  - [ ]* 7.7 Write property test for replace generates new DEK (Property 13)
    - **Property 13: Replace Generates New DEK** — `dek_wrapped_client` and `dek_wrapped_server` after replace differ from values before replace
    - **Validates: Requirements 2.9**
  - [x] 7.8 Implement `deleteFile` in `VaultServiceImpl`
    - Delete blob from Supabase Storage; delete `vault_files` row; throw 404 if not owned by caller
    - _Requirements: 2.10, 2.11_

- [x] 8. Checkpoint — backend core complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. FileConversionService implementation
  - [x] 9.1 Create `FileConversionService` interface and `FileConversionServiceImpl` skeleton; add `UnsupportedConversionException` and `UnsupportedFileTypeException` custom exceptions
    - _Requirements: 5.8, 5.10_
  - [x] 9.2 Implement document converters: DOCX→PDF (docx4j), PDF→DOCX (PDFBox + POI), CSV↔XLSX (POI), TXT→PDF (OpenPDF)
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  - [x] 9.3 Implement image converter: JPG↔PNG↔WEBP↔BMP using Java ImageIO + TwelveMonkeys
    - _Requirements: 5.5_
  - [x] 9.4 Implement audio and video converters using JAVE2 (FFmpeg wrapper): MP3↔WAV↔FLAC↔AAC↔OGG and MP4↔AVI↔MOV↔MKV
    - _Requirements: 5.6, 5.7_
  - [ ]* 9.5 Write property test for conversion output non-empty (Property 11)
    - **Property 11: Conversion Output Non-Empty** — for any valid (sourceFormat, targetFormat) pair and non-empty input, `convert(bytes, src, tgt).length > 0`
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7**
  - [x] 9.6 Implement `convertGuestAsync` in `FileConversionServiceImpl`
    - Create `ConversionJob(PENDING)`; submit async to `cryptoExecutor`; encrypt input in memory with tempDEK; convert; encrypt output; store via `GuestCacheService`; update job to DONE/FAILED
    - _Requirements: 3.1, 3.2, 3.3, 3.8, 3.9, 4.3, 4.4_
  - [x] 9.7 Implement `convertAndStoreAsync` for authenticated users
    - Unwrap DEK via server KEK; decrypt source file; convert; re-encrypt result with new DEK; store in vault; update job with `resultFileId`
    - _Requirements: 4.1, 4.2, 4.3, 4.4_
  - [x] 9.8 Implement `getJobStatus` and `downloadGuestResult` in `FileConversionServiceImpl`
    - _Requirements: 4.5, 3.4, 3.5_

- [x] 10. VaultController and ConversionController
  - [x] 10.1 Create `VaultController` with all six endpoints (`upload`, `listFiles`, `download`, `rename`, `replace`, `delete`)
    - Extract `emailHash` from `@AuthenticationPrincipal`; delegate to `VaultService`; return appropriate HTTP status codes; handle 404/413/503
    - _Requirements: 2.5, 2.7, 2.11, 8.3, 9.5_
  - [x] 10.2 Create `ConversionController` with `convertUpload`, `convertVaultFile`, `jobStatus`, `guestDownload` endpoints
    - Guest path: no auth required; authenticated path: require JWT; return 429 with `Retry-After` on `RejectedExecutionException`
    - _Requirements: 3.1, 4.1, 4.5, 9.5_
  - [x] 10.3 Create DTOs: `VaultFileDTO`, `ConversionJobDTO`, `RenameRequest`
    - _Requirements: 2.5, 4.5_
  - [ ]* 10.4 Write unit tests for `VaultController` and `ConversionController` with mocked services
    - Test auth enforcement, 404 on wrong owner, 413 on oversized file, 429 on queue full
    - _Requirements: 2.7, 2.11, 8.3, 9.5_

- [x] 11. Auth updates — JWT filter and ClientService column encryption
  - [x] 11.1 Update `ClientService` to use the new AES-GCM `EncryptDecryptConverter` for name, email, phone; store `phone_hash` (SHA-256) alongside `phone_enc` on registration
    - _Requirements: 1.1, 6.1, 6.4_
  - [x] 11.2 Update the JWT filter to extract `emailHash` as the principal username (instead of plaintext email) so all downstream services receive the hash
    - _Requirements: 1.6, 1.7_
  - [ ]* 11.3 Write property test for email hash consistency (Property 5)
    - **Property 5: Email Hash Consistency** — `SHA-256(lowercase(email))` at registration equals `SHA-256(lowercase(email))` at login for any email string
    - **Validates: Requirements 1.1, 6.4, 6.5**

- [x] 12. GlobalExceptionHandler updates
  - [x] 12.1 Add handlers for `UnsupportedFileTypeException` (400), `UnsupportedConversionException` (400), `GuestTokenExpiredException` (410), `DecryptionException` (422), `StorageUnavailableException` (503), `MaxUploadSizeExceededException` (413), `RejectedExecutionException` (429)
    - All responses must use the standard error format: `{ timestamp, status, error, message, path }`
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.6, 11.7, 11.8_

- [x] 13. Checkpoint — full backend wired
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 14. Frontend — Vite + React 18 + TypeScript setup, design tokens, routing
  - [x] 14.1 Scaffold the `kms-frontend` project with Vite + React 18 + TypeScript; install `react-router-dom`, `zustand`, `@tanstack/react-query`, `axios`, `framer-motion`, `react-dropzone`
    - _Requirements: 10.1_
  - [x] 14.2 Create `styles/tokens.css` with all CSS design tokens (colors, glassmorphism variables, gradients, spacing, radii) and `styles/global.css` with base resets
    - _Requirements: 10.1_
  - [x] 14.3 Set up React Router v6 routes for `/`, `/login`, `/signup`, `/dashboard`, `/convert`; create `services/api.ts` Axios instance with JWT interceptor that redirects to `/login` on 401
    - _Requirements: 10.3, 10.4, 10.9_
  - [x] 14.4 Implement Zustand `useAuth` store with `token`, `emailHash`, `isAuthenticated`, `login`, `logout` actions; persist token to `localStorage`
    - _Requirements: 10.3, 10.4_

- [ ] 15. Frontend — Auth pages (Login, Signup)
  - [x] 15.1 Implement `LoginPage.tsx` and `SignupPage.tsx` with glassmorphism card layout, form validation, and calls to existing auth API endpoints
    - On successful login: call `useAuth.login(token, emailHash)` and navigate to `/dashboard`
    - _Requirements: 10.3_
  - [ ]* 15.2 Write unit tests for login/signup form validation and auth store transitions
    - _Requirements: 10.3, 10.4_

- [ ] 16. Frontend — Landing page
  - [x] 16.1 Implement `LandingPage.tsx` with hero section, "Store My Files" CTA → `/signup`, "Convert a File — No Login Needed" CTA → `/convert`, and feature highlights section
    - _Requirements: 10.2_

- [ ] 17. Frontend — Dashboard (file grid, upload, file card CRUD)
  - [x] 17.1 Create `services/vaultApi.ts` with typed functions for all vault endpoints; create `useVault.ts` React Query hooks for list, upload, rename, replace, delete
    - _Requirements: 10.5, 10.6_
  - [x] 17.2 Implement `UploadDropzone.tsx` using `react-dropzone`; show upload progress; invalidate vault query on success
    - _Requirements: 10.6_
  - [x] 17.3 Implement `FileCard.tsx` with filename, category icon, size, and action buttons (download, rename, replace, delete); wire to vault API hooks
    - _Requirements: 10.5_
  - [x] 17.4 Implement `FileGrid.tsx` and `DashboardPage.tsx` composing the dropzone and file grid; show empty state when vault is empty
    - _Requirements: 10.5, 10.6_

- [ ] 18. Frontend — Conversion wizard (4-step flow, polling, guest + auth modes)
  - [x] 18.1 Create `services/conversionApi.ts` with typed functions for `POST /api/convert`, `GET /api/convert/jobs/{jobId}`, `GET /api/convert/download/{token}`; create `useConversion.ts` hook with React Query polling every 2 seconds until DONE/FAILED
    - _Requirements: 10.7, 10.10_
  - [x] 18.2 Implement `ConversionWizard.tsx` with 4 steps: (1) file drop, (2) format selector showing only compatible targets, (3) animated progress polling job status, (4) download button + conditional "Save to Vault" for authenticated users
    - _Requirements: 10.7, 10.8_
  - [x] 18.3 Implement `ConvertPage.tsx` composing the wizard; handle guest and authenticated modes transparently
    - _Requirements: 10.7, 10.8_

- [ ] 19. Integration wiring and final validation
  - [x] 19.1 Wire `SecurityConfig` to permit `/api/convert`, `/api/convert/download/**`, and `/api/convert/jobs/**` without auth; require JWT for `/api/vault/**` and `/api/convert/vault/**`
    - _Requirements: 3.1, 1.6, 1.7_
  - [x] 19.2 Update CORS config to allow the Vite dev origin (`http://localhost:5173`) in dev profile and the production frontend origin in prod profile; remove wildcard CORS
    - _Requirements: 8.8_
  - [ ]* 19.3 Write integration test for full upload → download round-trip using `@SpringBootTest` with a mocked `SupabaseStorageService`
    - Verify plaintext bytes returned from download equal bytes submitted on upload (Property 1)
    - **Property 1: File Encryption Round-Trip**
    - **Validates: Requirements 2.1, 2.6**
  - [ ]* 19.4 Write integration test for guest conversion → download flow end-to-end
    - Verify token is single-use and second download returns 410 (Property 6)
    - **Property 6: Guest Token Single-Use**
    - **Validates: Requirements 3.3, 3.4, 3.5**
  - [ ]* 19.5 Write integration test for DEK uniqueness across two uploads of identical content (Property 2)
    - **Property 2: DEK Uniqueness Per File** — `dek_wrapped_client` of upload A ≠ `dek_wrapped_client` of upload B
    - **Validates: Requirements 2.1, 2.2, 2.3**

- [x] 20. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Checkpoints at tasks 8, 13, and 20 ensure incremental validation
- Property tests validate universal correctness properties defined in the design document
- The existing `EncryptDecryptConverter` (AES-ECB) is fully replaced in task 2.1 — run a data migration if any existing H2 data needs to be preserved
