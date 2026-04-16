# Requirements Document

## Introduction

Secure Cloud Vault is an evolution of the existing Key Management System into a full-featured, encrypted cloud storage and file conversion platform. Authenticated users receive a personal encrypted vault with full CRUD over files of any type (images, videos, documents, audio). Guest users receive a zero-account file conversion service where no data is persisted beyond a 30-minute encrypted cache. The existing X25519 + AES-256-GCM dual-key cryptographic model is preserved and extended to all stored files. The backend is Spring Boot 3 connected to Supabase (PostgreSQL + Storage bucket); the frontend is a new React 18 application with a glassmorphism/neon theme.

## Glossary

- **Vault_System**: The complete Secure Cloud Vault platform (Spring Boot backend + React frontend).
- **Auth_Controller**: The Spring Boot controller handling user registration, login, and OTP endpoints.
- **Vault_Controller**: The Spring Boot controller handling authenticated file CRUD endpoints.
- **Conversion_Controller**: The Spring Boot controller handling file conversion endpoints for both guests and authenticated users.
- **Vault_Service**: The backend service responsible for encrypted file upload, download, rename, replace, and delete for authenticated users.
- **Conversion_Service**: The backend service responsible for file format conversion for both guest and authenticated users.
- **Crypto_Service**: The backend service responsible for all cryptographic operations (X25519, AES-256-GCM, HKDF, SHA-256).
- **Guest_Cache_Service**: The backend service that stores short-lived encrypted conversion results for guest users with a 30-minute TTL.
- **Storage_Service**: The backend service abstracting Supabase Storage bucket operations.
- **Column_Converter**: The JPA attribute converter that encrypts/decrypts PII columns using AES-256-GCM.
- **Client**: A registered, authenticated user of the vault.
- **Guest**: An anonymous user who uses the file conversion service without an account.
- **DEK**: Data Encryption Key — a random 32-byte key used to encrypt a single file with AES-256-GCM.
- **KEK**: Key Encryption Key — derived via HKDF from a shared secret or master key; used to wrap a DEK.
- **Wrapped_DEK**: A DEK encrypted under a KEK, stored in the database.
- **Download_Token**: A cryptographically random, single-use, 30-minute-TTL opaque string issued to a guest after a successful conversion.
- **Email_Hash**: SHA-256(lowercase(email)) — stored in the `email_hash` column; used for login lookup without storing plaintext email.
- **FileCategory**: Enum classifying files as DOCUMENT, IMAGE, VIDEO, or AUDIO.
- **Conversion_Job**: A database record tracking the lifecycle (PENDING → PROCESSING → DONE/FAILED) of an async conversion operation.
- **JWT**: JSON Web Token issued on successful login; required for all authenticated endpoints.
- **Master_Key**: A 32-byte AES key loaded from the `VAULT_COLUMN_MASTER_KEY` environment variable; used for column-level encryption.
- **Storage_Key**: The path/key of an encrypted blob in the Supabase Storage bucket, always in the form `vault/{emailHash}/{uuid}`.

---

## Requirements

### Requirement 1: User Registration and Authentication

**User Story:** As a new user, I want to register an account and log in securely, so that I can access my personal encrypted vault.

#### Acceptance Criteria

1. WHEN a registration request is received with name, email, phone, and password, THE Auth_Controller SHALL create a new Client record with all PII fields encrypted using AES-256-GCM and store only the SHA-256 hash of the email and phone for lookup.
2. WHEN a registration request is received, THE Crypto_Service SHALL generate a unique X25519 key pair for the client and store the public key in plaintext and the server ephemeral public key in the `clients` table.
3. WHEN a login request is received with an email and password, THE Auth_Controller SHALL look up the client by Email_Hash and verify the BCrypt password hash before issuing a JWT.
4. IF a login request is received with an email that does not match any Email_Hash, THEN THE Auth_Controller SHALL return HTTP 401 without revealing whether the email or password was incorrect.
5. IF a login request is received with a correct email but incorrect password, THEN THE Auth_Controller SHALL return HTTP 401 without revealing whether the email or password was incorrect.
6. WHEN a valid JWT is presented on a protected endpoint, THE Vault_System SHALL extract the Email_Hash from the token and use it to identify the requesting Client.
7. IF an expired or malformed JWT is presented, THEN THE Vault_System SHALL return HTTP 401.
8. WHEN an OTP send request is received for a registered email, THE Auth_Controller SHALL generate a time-limited OTP, store it in the `otp_code` and `otp_expiry` columns, and deliver it to the client.
9. WHEN an OTP verify request is received with a valid, unexpired OTP, THE Auth_Controller SHALL confirm the OTP and allow the associated action to proceed.
10. IF an OTP verify request is received with an expired or incorrect OTP, THEN THE Auth_Controller SHALL return HTTP 400.

---

### Requirement 2: Encrypted Vault File Storage (CRUD)

**User Story:** As an authenticated user, I want to upload, view, download, rename, replace, and delete files in my encrypted vault, so that my files are stored securely and I retain full control over them.

#### Acceptance Criteria

1. WHEN an authenticated upload request is received with a multipart file, THE Vault_Service SHALL generate a random 32-byte DEK, encrypt the file bytes with AES-256-GCM using that DEK, and store the encrypted blob in Supabase Storage under the path `vault/{emailHash}/{uuid}`.
2. WHEN a file is uploaded, THE Vault_Service SHALL wrap the DEK for the client using HKDF(X25519(serverPriv, clientPub), salt, info) and store the Wrapped_DEK in the `dek_wrapped_client` column.
3. WHEN a file is uploaded, THE Vault_Service SHALL wrap the DEK for the server using HKDF(masterServerKey, salt, info) and store the Wrapped_DEK in the `dek_wrapped_server` column.
4. WHEN a file is uploaded, THE Vault_Service SHALL encrypt the original filename using the Column_Converter and store it in `filename_enc`; the `content_type` and `file_category` SHALL be stored in plaintext.
5. WHEN a list files request is received, THE Vault_Controller SHALL return metadata (id, decrypted filename, content type, category, original size, timestamps) for all vault files owned by the requesting Client, without returning any key material or encrypted blobs.
6. WHEN a download request is received for a file owned by the requesting Client, THE Vault_Service SHALL fetch the encrypted blob from Supabase Storage, unwrap the DEK using the client's X25519 key, decrypt the blob with AES-256-GCM, and return the original plaintext bytes.
7. IF a download request is received for a file not owned by the requesting Client, THEN THE Vault_Controller SHALL return HTTP 404.
8. WHEN a rename request is received, THE Vault_Service SHALL re-encrypt the new filename using the Column_Converter and update the `filename_enc` column; all other file metadata SHALL remain unchanged.
9. WHEN a replace request is received with a new multipart file, THE Vault_Service SHALL generate a new random DEK, re-encrypt the new file bytes, update the Supabase Storage blob, and update all key material columns (`dek_wrapped_client`, `dek_wrapped_server`, `iv`, `salt`) in the database.
10. WHEN a delete request is received for a file owned by the requesting Client, THE Vault_Service SHALL delete the encrypted blob from Supabase Storage and remove the corresponding `vault_files` row from the database.
11. IF a delete request is received for a file not owned by the requesting Client, THEN THE Vault_Controller SHALL return HTTP 404.
12. THE Vault_Service SHALL perform upload, download, and replace operations asynchronously using CompletableFuture on dedicated thread pools, so that the HTTP request thread is not blocked.

---

### Requirement 3: Guest File Conversion (No Account Required)

**User Story:** As a guest user, I want to convert files without creating an account, so that I can use the conversion service freely without any registration barrier.

#### Acceptance Criteria

1. WHEN a conversion request is received without a JWT, THE Conversion_Controller SHALL accept the request and process it as a guest conversion without requiring authentication.
2. WHEN a guest conversion request is received, THE Conversion_Service SHALL encrypt the input file bytes in memory with a temporary DEK before any processing, perform the conversion, encrypt the output bytes with the same temporary DEK, and store only the encrypted output in the Guest_Cache_Service.
3. WHEN a guest conversion completes successfully, THE Conversion_Service SHALL store the encrypted result in the Guest_Cache_Service with a TTL of 30 minutes and return a cryptographically random Download_Token to the client.
4. WHEN a guest download request is received with a valid, unexpired Download_Token, THE Conversion_Controller SHALL decrypt the cached result and stream the plaintext file bytes to the client with a `Content-Disposition: attachment` header.
5. IF a guest download request is received with a Download_Token that has already been consumed, THEN THE Conversion_Controller SHALL return HTTP 410.
6. IF a guest download request is received with a Download_Token that has expired (TTL exceeded), THEN THE Conversion_Controller SHALL return HTTP 410.
7. THE Guest_Cache_Service SHALL purge all expired cache entries on a scheduled basis every 5 minutes.
8. WHEN a guest conversion completes, THE Vault_System SHALL not persist any plaintext guest file data to the database or Supabase Storage beyond the encrypted TTL cache entry.
9. WHEN a guest conversion request is received, THE Conversion_Service SHALL create a Conversion_Job record with status PENDING and return the job ID immediately, then process the conversion asynchronously.

---

### Requirement 4: Async File Conversion for Authenticated Users

**User Story:** As an authenticated user, I want to convert files already in my vault or upload new files for conversion, so that I can transform file formats without leaving the platform.

#### Acceptance Criteria

1. WHEN an authenticated conversion request is received referencing a vault file by ID, THE Conversion_Service SHALL unwrap the DEK using the server KEK, decrypt the source file, perform the conversion, re-encrypt the result with a new DEK, store it in the user's vault, and update the Conversion_Job with the result file ID.
2. WHEN an authenticated conversion request is received with a direct file upload, THE Conversion_Controller SHALL accept the file, perform the conversion, and store the result in the user's vault as a new vault file.
3. WHEN a conversion job is submitted, THE Conversion_Service SHALL create a Conversion_Job record with status PENDING and return the job ID immediately without waiting for conversion to complete.
4. WHEN a conversion job transitions to PROCESSING, DONE, or FAILED, THE Conversion_Service SHALL update the `status` column in the `conversion_jobs` table accordingly.
5. WHEN a job status poll request is received, THE Conversion_Controller SHALL return the current Conversion_Job status, including `resultFileId` for DONE authenticated jobs and `downloadToken` for DONE guest jobs.
6. IF a conversion fails due to an unsupported format pair or processing error, THEN THE Conversion_Service SHALL set the job status to FAILED and store a descriptive error message in the `error_message` column.

---

### Requirement 5: File Format Conversion Support

**User Story:** As a user, I want to convert between common document, image, audio, and video formats, so that I can work with files in the format I need.

#### Acceptance Criteria

1. WHEN a document conversion request is received with source format `docx` and target format `pdf`, THE Conversion_Service SHALL produce a valid PDF output using docx4j.
2. WHEN a document conversion request is received with source format `pdf` and target format `docx`, THE Conversion_Service SHALL produce a valid DOCX output using Apache PDFBox and Apache POI.
3. WHEN a document conversion request is received for CSV ↔ XLSX, THE Conversion_Service SHALL produce valid output in the target format using Apache POI.
4. WHEN a document conversion request is received with source format `txt` and target format `pdf`, THE Conversion_Service SHALL produce a valid PDF output using OpenPDF.
5. WHEN an image conversion request is received for any supported pair among JPG, PNG, WEBP, and BMP, THE Conversion_Service SHALL produce a valid image in the target format using Java ImageIO and TwelveMonkeys plugins.
6. WHEN an audio conversion request is received for any supported pair among MP3, WAV, FLAC, AAC, and OGG, THE Conversion_Service SHALL produce valid audio output in the target format using JAVE2.
7. WHEN a video conversion request is received for any supported pair among MP4, AVI, MOV, and MKV, THE Conversion_Service SHALL produce valid video output in the target format using JAVE2.
8. IF a conversion request is received for an unsupported source/target format pair, THEN THE Conversion_Service SHALL throw an UnsupportedConversionException and the Conversion_Controller SHALL return HTTP 400.
9. THE Conversion_Service SHALL detect the FileCategory of a file from its MIME type using the `FileCategory.fromMimeType` mapping and route the conversion to the appropriate conversion sub-service.
10. IF a MIME type is not present in the supported MIME type mapping, THEN THE Conversion_Service SHALL throw an UnsupportedFileTypeException and the Conversion_Controller SHALL return HTTP 400.

---

### Requirement 6: Database Opacity and Column-Level Encryption

**User Story:** As a system administrator, I want all personally identifiable information to be encrypted at the column level so that direct database access reveals no plaintext user data.

#### Acceptance Criteria

1. THE Column_Converter SHALL encrypt all PII column values (name, email, phone, filename) using AES-256-GCM with a randomly generated 12-byte IV prepended to each ciphertext, so that encrypting the same value twice produces different ciphertext.
2. THE Column_Converter SHALL decrypt a column value by extracting the first 12 bytes as the IV and decrypting the remainder with the Master_Key.
3. THE Vault_System SHALL load the Master_Key exclusively from the `VAULT_COLUMN_MASTER_KEY` environment variable and SHALL NOT hardcode any encryption key in source code or configuration files.
4. WHEN a Client is registered, THE Auth_Controller SHALL store the SHA-256 hash of the lowercase email in `email_hash` and the SHA-256 hash of the phone in `phone_hash`; no plaintext email or phone SHALL be stored in any column.
5. WHEN a login lookup is performed, THE Auth_Controller SHALL compute SHA-256(lowercase(email)) and query the `email_hash` column; the plaintext email SHALL NOT be used in any database query.
6. THE Vault_System SHALL store passwords exclusively as BCrypt hashes with a cost factor of at least 12; plaintext passwords SHALL never be stored or logged.
7. WHEN a file is uploaded, THE Vault_Service SHALL store the original filename exclusively in the `filename_enc` column using the Column_Converter; the plaintext filename SHALL NOT appear in any other column.

---

### Requirement 7: Supabase Integration

**User Story:** As a developer, I want the system to use Supabase as the database and storage backend, so that I can leverage managed PostgreSQL and object storage without operating my own infrastructure.

#### Acceptance Criteria

1. THE Vault_System SHALL connect to Supabase PostgreSQL using the JDBC URL constructed from the `SUPABASE_DB_HOST` and `SUPABASE_DB_PASSWORD` environment variables.
2. THE Storage_Service SHALL upload encrypted file blobs to the Supabase Storage bucket named `vault-files` using the Supabase Storage REST API authenticated with the `SUPABASE_SERVICE_ROLE_KEY`.
3. THE Storage_Service SHALL download encrypted file blobs from the `vault-files` bucket by storage key using the Supabase Storage REST API.
4. THE Storage_Service SHALL delete objects from the `vault-files` bucket by storage key using the Supabase Storage REST API.
5. THE Vault_System SHALL use Flyway to manage all database schema migrations; the JPA `ddl-auto` setting SHALL be set to `validate` in production.
6. IF the Supabase Storage service is unreachable, THEN THE Storage_Service SHALL throw a StorageUnavailableException and THE Vault_Controller SHALL return HTTP 503.

---

### Requirement 8: Security Controls

**User Story:** As a security-conscious operator, I want the system to enforce strict security controls, so that user data and credentials are protected against common attack vectors.

#### Acceptance Criteria

1. THE Vault_System SHALL re-detect the MIME type of every uploaded file from its magic bytes using Apache Tika, regardless of the `Content-Type` header supplied by the client.
2. THE Vault_Service SHALL construct all Supabase Storage keys in the format `vault/{emailHash}/{uuid}`, where the UUID is server-generated; user-supplied filenames SHALL NOT be used in storage key construction.
3. THE Vault_System SHALL enforce a configurable maximum file size for uploads; IF a file exceeds this limit, THEN THE Vault_Controller SHALL return HTTP 413.
4. THE Vault_System SHALL load the JWT signing secret exclusively from an environment variable; the secret SHALL be at least 256 bits in length and SHALL NOT be hardcoded in any configuration file.
5. THE Vault_System SHALL load the Supabase service-role key exclusively from the `SUPABASE_SERVICE_ROLE_KEY` environment variable; it SHALL NOT be committed to source control.
6. WHEN a Download_Token is issued to a guest, THE Guest_Cache_Service SHALL generate it as a cryptographically random 32-byte hex string; the same token SHALL NOT be issued to two different conversion results.
7. WHEN a Download_Token is consumed by a successful guest download, THE Guest_Cache_Service SHALL invalidate the token so that subsequent requests with the same token return HTTP 410.
8. THE Vault_System SHALL restrict CORS to the known frontend origin in production; wildcard (`*`) CORS SHALL NOT be used in production configuration.

---

### Requirement 9: Async Processing and Thread Pool Management

**User Story:** As a system operator, I want all heavy operations to run on dedicated thread pools, so that the HTTP layer remains responsive under load.

#### Acceptance Criteria

1. THE Vault_System SHALL configure a `fileIoExecutor` thread pool with a core size of 10, a maximum size of 50, and a queue capacity of 200 for I/O-bound file upload and download operations.
2. THE Vault_System SHALL configure a `cryptoExecutor` thread pool with a core size of 4, a maximum size of 2× the available processor count, and a queue capacity of 100 for CPU-bound encryption and conversion operations.
3. WHEN a file upload, download, or replace request is received, THE Vault_Service SHALL execute the operation asynchronously using CompletableFuture on the `fileIoExecutor` and `cryptoExecutor` pools and return a response without blocking the HTTP request thread.
4. WHEN a conversion job is submitted, THE Conversion_Service SHALL execute the conversion asynchronously on the `cryptoExecutor` pool and return the job ID immediately with status PENDING.
5. IF the conversion job queue is full (thread pool queue capacity exceeded), THEN THE Conversion_Controller SHALL return HTTP 429 with a `Retry-After` header.

---

### Requirement 10: React Frontend

**User Story:** As a user, I want a vibrant, interactive web interface, so that I can manage my vault and convert files through an intuitive browser experience.

#### Acceptance Criteria

1. THE Vault_System SHALL provide a React 18 + TypeScript single-page application built with Vite that implements the glassmorphism/neon visual theme defined by the CSS design tokens.
2. WHEN a user visits the landing page, THE Vault_System SHALL display a hero section with a "Store My Files" CTA that navigates to the signup page and a "Convert a File — No Login Needed" CTA that navigates to the conversion page.
3. WHEN a user logs in successfully, THE Vault_System SHALL store the JWT and Email_Hash in Zustand auth state and redirect the user to the dashboard.
4. WHEN a user logs out, THE Vault_System SHALL clear the Zustand auth state and redirect the user to the landing page.
5. WHEN an authenticated user visits the dashboard, THE Vault_System SHALL display a file grid showing all vault files with their decrypted filenames, categories, and sizes, fetched via React Query.
6. WHEN a user uploads a file via the drag-and-drop dropzone, THE Vault_System SHALL show upload progress and add the new file to the file grid upon completion.
7. WHEN a user initiates a conversion on the conversion page, THE Vault_System SHALL present a multi-step wizard: (1) file selection, (2) target format selection showing only compatible formats, (3) animated progress polling the job status every 2 seconds, (4) download button upon completion.
8. WHEN a conversion job reaches DONE status and the user is authenticated, THE Vault_System SHALL display a "Save to Vault" button in addition to the download button.
9. WHEN an API request fails due to an expired JWT, THE Vault_System SHALL automatically redirect the user to the login page.
10. THE Vault_System SHALL use React Query to poll `GET /api/convert/jobs/{jobId}` every 2 seconds until the job status is DONE or FAILED, then stop polling.

---

### Requirement 11: Error Handling

**User Story:** As a user, I want clear error messages when something goes wrong, so that I understand what happened and can take corrective action.

#### Acceptance Criteria

1. IF a request is received with an unsupported file type, THEN THE Vault_System SHALL return HTTP 400 with a JSON error body containing a descriptive message identifying the unsupported MIME type.
2. IF a file upload exceeds the configured maximum size, THEN THE Vault_System SHALL return HTTP 413 with a JSON error body.
3. IF a requested file is not found or does not belong to the requesting Client, THEN THE Vault_System SHALL return HTTP 404 with a JSON error body.
4. IF a decryption operation fails due to invalid key material, THEN THE Vault_System SHALL return HTTP 422 with a JSON error body; key material details SHALL NOT be included in the error response.
5. IF a conversion operation fails, THEN THE Conversion_Service SHALL set the Conversion_Job status to FAILED and store a descriptive error message; THE Conversion_Controller SHALL return the error message in the job status response.
6. IF a guest download token is expired or already consumed, THEN THE Vault_System SHALL return HTTP 410 with a JSON error body.
7. IF the Supabase Storage service is unreachable, THEN THE Vault_System SHALL return HTTP 503 with a JSON error body.
8. THE Vault_System SHALL return all error responses in the format `{ "timestamp": "<ISO-8601>", "status": <code>, "error": "<reason>", "message": "<detail>", "path": "<request-path>" }`.
