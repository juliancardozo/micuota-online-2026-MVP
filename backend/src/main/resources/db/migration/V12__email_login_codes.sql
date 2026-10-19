CREATE TABLE IF NOT EXISTS email_login_codes (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    request_ip VARCHAR(64),
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_email_login_codes_email_created_at ON email_login_codes (email, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_login_codes_request_ip_created_at ON email_login_codes (request_ip, created_at DESC);
