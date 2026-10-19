ALTER TABLE payment_operations
    ADD COLUMN IF NOT EXISTS mp_payment_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS mp_fee_amount NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS mp_net_received_amount NUMERIC(19, 2);

CREATE INDEX IF NOT EXISTS idx_payment_operations_mp_payment_id ON payment_operations (mp_payment_id);
