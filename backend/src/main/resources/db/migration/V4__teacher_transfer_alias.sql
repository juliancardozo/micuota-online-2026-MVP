ALTER TABLE teacher_profiles
    ADD COLUMN IF NOT EXISTS transfer_alias VARCHAR(255);

ALTER TABLE teacher_profiles
    ADD COLUMN IF NOT EXISTS transfer_bank_name VARCHAR(255);
