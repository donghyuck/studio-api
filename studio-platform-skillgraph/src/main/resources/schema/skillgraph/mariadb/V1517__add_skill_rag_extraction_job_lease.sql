ALTER TABLE tb_skill_rag_extraction_job
    ADD COLUMN lease_owner VARCHAR(200),
    ADD COLUMN lease_expires_at TIMESTAMP NULL,
    ADD COLUMN heartbeat_at TIMESTAMP NULL,
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_skill_rag_extraction_job_recovery
    ON tb_skill_rag_extraction_job(status, lease_expires_at, updated_at);
