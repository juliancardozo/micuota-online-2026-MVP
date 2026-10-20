ALTER TABLE teacher_profiles
    ADD COLUMN IF NOT EXISTS mp_oauth_connected BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS mp_refresh_token VARCHAR(255),
    ADD COLUMN IF NOT EXISTS mp_user_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS mp_token_expires_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE payment_operations
    ADD COLUMN IF NOT EXISTS marketplace_fee_amount NUMERIC(19, 2);
