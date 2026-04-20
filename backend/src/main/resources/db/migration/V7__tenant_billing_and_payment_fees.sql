ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS plan_code VARCHAR(20) NOT NULL DEFAULT 'BASE',
    ADD COLUMN IF NOT EXISTS take_rate_bps INTEGER NOT NULL DEFAULT 350,
    ADD COLUMN IF NOT EXISTS advanced_dunning_fee_bps INTEGER NOT NULL DEFAULT 120,
    ADD COLUMN IF NOT EXISTS recovery_automation_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS advanced_analytics_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS integrations_enabled BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE payment_operations
    ADD COLUMN IF NOT EXISTS processing_fee_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS advanced_feature_fee_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS net_amount_for_teacher NUMERIC(19, 2) NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_tenants_plan_code ON tenants (plan_code);
