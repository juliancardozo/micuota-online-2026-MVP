ALTER TABLE payment_operations
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(120),
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS due_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS last_reconciled_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_payment_operations_next_retry_at ON payment_operations (next_retry_at);
CREATE INDEX IF NOT EXISTS idx_payment_operations_due_at ON payment_operations (due_at);
CREATE INDEX IF NOT EXISTS idx_payment_operations_reconciliation_status ON payment_operations (reconciliation_status);
