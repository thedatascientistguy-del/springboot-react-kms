-- V1: Initial schema matching existing JPA entities

-- clients table
CREATE TABLE IF NOT EXISTS clients (
    id            BIGSERIAL PRIMARY KEY,
    name          TEXT          NOT NULL,
    email         TEXT          NOT NULL,
    email_hash    VARCHAR(64)   NOT NULL UNIQUE,
    phone         TEXT          NOT NULL UNIQUE,
    password      VARCHAR(255)  NOT NULL,
    public_key    TEXT          NOT NULL,
    server_public_key TEXT      NOT NULL,
    otp_code      VARCHAR(10),
    otp_expiry    TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL DEFAULT now()
);

-- encrypted_data table (existing KMS records)
CREATE TABLE IF NOT EXISTS encrypted_data (
    id                      BIGSERIAL PRIMARY KEY,
    client_id               BIGINT        NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    encrypted_payload       TEXT          NOT NULL,
    iv                      VARCHAR(255)  NOT NULL,
    salt                    VARCHAR(255)  NOT NULL,
    dek_wrapped             TEXT          NOT NULL,
    dek_wrapped_for_recovery TEXT,
    data_type               VARCHAR(32)   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_encrypted_data_client_id ON encrypted_data(client_id);
