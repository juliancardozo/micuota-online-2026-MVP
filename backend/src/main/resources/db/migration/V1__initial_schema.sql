CREATE TABLE IF NOT EXISTS tenants (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    tenant_id BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email)
);

CREATE TABLE IF NOT EXISTS teacher_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    mp_access_token VARCHAR(255),
    wc_api_key VARCHAR(255),
    prometeo_api_key VARCHAR(255),
    transfer_alias VARCHAR(255),
    transfer_bank_name VARCHAR(255),
    CONSTRAINT fk_teacher_profile_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS courses (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    teacher_user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_course_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS course_enrollments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_enrollment_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_user_id) REFERENCES users (id),
    CONSTRAINT uk_enrollment_course_student UNIQUE (course_id, student_user_id)
);

CREATE TABLE IF NOT EXISTS payment_operations (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    student_user_id BIGINT,
    course_id BIGINT,
    provider VARCHAR(50) NOT NULL,
    flow_type VARCHAR(50) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(12) NOT NULL,
    description VARCHAR(255) NOT NULL,
    checkout_url VARCHAR(255) NOT NULL,
    provider_reference VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    raw_response TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users (tenant_id);
CREATE INDEX IF NOT EXISTS idx_courses_tenant_id ON courses (tenant_id);
CREATE INDEX IF NOT EXISTS idx_courses_teacher_user_id ON courses (teacher_user_id);
CREATE INDEX IF NOT EXISTS idx_course_enrollments_course_id ON course_enrollments (course_id);
CREATE INDEX IF NOT EXISTS idx_course_enrollments_student_user_id ON course_enrollments (student_user_id);
CREATE INDEX IF NOT EXISTS idx_payment_operations_teacher_id ON payment_operations (teacher_id);
CREATE INDEX IF NOT EXISTS idx_payment_operations_course_id ON payment_operations (course_id);
