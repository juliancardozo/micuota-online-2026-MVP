CREATE TABLE IF NOT EXISTS payment_events (
    id BIGSERIAL PRIMARY KEY,
    operation_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    student_user_id BIGINT,
    course_id BIGINT,
    provider VARCHAR(50) NOT NULL,
    flow_type VARCHAR(50) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    status_from VARCHAR(50),
    status_to VARCHAR(50),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(12) NOT NULL,
    provider_reference VARCHAR(255) NOT NULL,
    raw_payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_payment_event_operation FOREIGN KEY (operation_id) REFERENCES payment_operations (id)
);

CREATE INDEX IF NOT EXISTS idx_payment_events_operation_id_created_at ON payment_events (operation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_events_teacher_id_created_at ON payment_events (teacher_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_events_event_type ON payment_events (event_type);
CREATE INDEX IF NOT EXISTS idx_payment_events_provider_reference ON payment_events (provider_reference);
