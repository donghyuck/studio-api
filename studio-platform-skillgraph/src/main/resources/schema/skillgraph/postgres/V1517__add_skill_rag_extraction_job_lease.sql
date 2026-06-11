ALTER TABLE tb_skill_rag_extraction_job
    ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(200),
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_skill_rag_extraction_job_recovery
    ON tb_skill_rag_extraction_job(status, lease_expires_at, updated_at);
