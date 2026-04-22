ALTER TABLE teacher_profiles
    ADD COLUMN IF NOT EXISTS mp_public_key VARCHAR(255);
