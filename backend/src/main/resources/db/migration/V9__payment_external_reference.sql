ALTER TABLE payment_operations
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_payment_operations_external_reference ON payment_operations (external_reference);
