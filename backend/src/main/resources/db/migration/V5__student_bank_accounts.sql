CREATE TABLE IF NOT EXISTS student_bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    account_type VARCHAR(100) NOT NULL,
    bank_code VARCHAR(100),
    branch_code VARCHAR(100),
    account_holder_name VARCHAR(255),
    account_last4 VARCHAR(32) NOT NULL,
    account_number_masked VARCHAR(255) NOT NULL,
    verification_status VARCHAR(50) NOT NULL,
    provider_code INTEGER,
    provider_message VARCHAR(255),
    preferred BOOLEAN NOT NULL DEFAULT FALSE,
    raw_response TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_student_bank_account_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_student_bank_account_student FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT fk_student_bank_account_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_student_bank_accounts_tenant_id ON student_bank_accounts (tenant_id);
CREATE INDEX IF NOT EXISTS idx_student_bank_accounts_student_user_id ON student_bank_accounts (student_user_id);
CREATE INDEX IF NOT EXISTS idx_student_bank_accounts_preferred ON student_bank_accounts (student_user_id, preferred, updated_at DESC);
