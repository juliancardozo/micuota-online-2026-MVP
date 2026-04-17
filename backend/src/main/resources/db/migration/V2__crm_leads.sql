CREATE TABLE IF NOT EXISTS leads (
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

CREATE INDEX IF NOT EXISTS idx_leads_created_at ON leads (created_at DESC);
