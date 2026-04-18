CREATE SCHEMA IF NOT EXISTS crm;
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS crm.leads (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(64),
    full_name VARCHAR(255) NOT NULL,
    source VARCHAR(120) NOT NULL,
    status VARCHAR(50) NOT NULL,
    segment VARCHAR(50),
    score INTEGER,
    owner VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'leads'
    ) THEN
        INSERT INTO crm.leads (id, email, phone, full_name, source, status, segment, score, owner, created_at, updated_at, assigned_at)
        SELECT id, email, phone, full_name, source, status, segment, score, owner, created_at, updated_at, assigned_at
        FROM public.leads
        ON CONFLICT (id) DO NOTHING;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_crm_leads_created_at ON crm.leads (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crm_leads_status ON crm.leads (status);
CREATE INDEX IF NOT EXISTS idx_crm_leads_source ON crm.leads (source);

CREATE TABLE IF NOT EXISTS analytics.session_activity (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    duration_seconds BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_session_activity_started_at ON analytics.session_activity (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_session_activity_last_seen_at ON analytics.session_activity (last_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_session_activity_tenant_id ON analytics.session_activity (tenant_id);
