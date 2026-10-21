-- Cobros mensuales sin Mercado Pago: cada mes se genera un cobro nuevo a partir de esta plantilla.
CREATE TABLE IF NOT EXISTS recurring_charges (
    id BIGSERIAL PRIMARY KEY,
    teacher_profile_id BIGINT NOT NULL,
    student_user_id BIGINT,
    payer_email VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(12) NOT NULL,
    day_of_month INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_operation_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    stopped_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_recurring_charges_due ON recurring_charges (active, next_run_at);

ALTER TABLE payment_operations
    ADD COLUMN IF NOT EXISTS recurring_charge_id BIGINT;
