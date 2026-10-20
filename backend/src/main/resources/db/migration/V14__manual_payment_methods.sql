-- Datos para cobrar por transferencia, giro o efectivo, y registro de pagos marcados a mano.
ALTER TABLE teacher_profiles
    ADD COLUMN IF NOT EXISTS transfer_account_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS transfer_account_holder VARCHAR(120),
    ADD COLUMN IF NOT EXISTS other_payment_instructions VARCHAR(500);

ALTER TABLE payment_operations
    ADD COLUMN IF NOT EXISTS paid_method VARCHAR(20),
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS paid_note VARCHAR(300);
