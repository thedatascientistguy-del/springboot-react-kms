-- V2: Vault schema — adds phone_hash to clients, vault_files, and conversion_jobs tables

-- Add phone_hash and email_hash index to clients if not already present
ALTER TABLE clients ADD COLUMN IF NOT EXISTS phone_hash VARCHAR(64);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS email_hash VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS idx_clients_email_hash ON clients(email_hash);
CREATE UNIQUE INDEX IF NOT EXISTS idx_clients_phone_hash ON clients(phone_hash);

-- vault_files table
CREATE TABLE IF NOT EXISTS vault_files (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           BIGINT        REFERENCES clients(id) ON DELETE CASCADE,
    filename_enc        TEXT          NOT NULL,
    content_type        VARCHAR(128)  NOT NULL,
    file_category       VARCHAR(32)   NOT NULL,
    storage_key         TEXT          NOT NULL,
    original_size       BIGINT        NOT NULL,
    encrypted_size      BIGINT        NOT NULL,
    dek_wrapped_client  TEXT          NOT NULL,
    dek_wrapped_server  TEXT,
    iv                  VARCHAR(64)   NOT NULL,
    salt                VARCHAR(64)   NOT NULL,
    is_guest            BOOLEAN       NOT NULL DEFAULT false,
    guest_session_token VARCHAR(128),
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vault_files_client_id ON vault_files(client_id);
CREATE INDEX IF NOT EXISTS idx_vault_files_guest_session_token ON vault_files(guest_session_token);
CREATE UNIQUE INDEX IF NOT EXISTS idx_vault_files_guest_token_unique ON vault_files(guest_session_token) WHERE guest_session_token IS NOT NULL;

-- conversion_jobs table
CREATE TABLE IF NOT EXISTS conversion_jobs (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id        BIGINT        REFERENCES clients(id) ON DELETE SET NULL,
    source_file_id   UUID          REFERENCES vault_files(id) ON DELETE SET NULL,
    result_file_id   UUID          REFERENCES vault_files(id) ON DELETE SET NULL,
    source_format    VARCHAR(32),
    target_format    VARCHAR(32),
    status           VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    error_message    TEXT,
    download_token   VARCHAR(128)  UNIQUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_conversion_jobs_client_id ON conversion_jobs(client_id);
CREATE INDEX IF NOT EXISTS idx_conversion_jobs_download_token ON conversion_jobs(download_token);
CREATE INDEX IF NOT EXISTS idx_conversion_jobs_status ON conversion_jobs(status);
