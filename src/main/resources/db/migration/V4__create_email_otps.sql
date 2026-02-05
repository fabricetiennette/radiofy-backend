-- V4__create_email_otps.sql

CREATE TABLE IF NOT EXISTS email_otps (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    request_ip VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL
    );

-- Matches @Index in the entity (plus DESC for the "latest OTP" query)
CREATE INDEX IF NOT EXISTS idx_email_otps_email_purpose_created
    ON email_otps (email, purpose, created_at DESC);

-- Very useful for "active codes" checks (countBy...consumedAt is null and expiresAt > now)
CREATE INDEX IF NOT EXISTS idx_email_otps_active
    ON email_otps (email, purpose, expires_at)
    WHERE consumed_at IS NULL;