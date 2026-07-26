CREATE TABLE IF NOT EXISTS tb_ai_markdown_metadata_backfill_job (
    job_id VARCHAR(100) PRIMARY KEY,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_by VARCHAR(200) NOT NULL,
    settings_json TEXT NOT NULL,
    settings_fingerprint VARCHAR(64) NOT NULL,
    dry_run_job_id VARCHAR(100),
    lease_owner VARCHAR(100),
    lease_expires_at TIMESTAMP NULL,
    heartbeat_at TIMESTAMP NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    total_count INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_ai_markdown_metadata_backfill_job_status (status, created_at)
);

CREATE TABLE IF NOT EXISTS tb_ai_markdown_metadata_backfill_item (
    item_id VARCHAR(100) PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    document_id VARCHAR(100) NOT NULL,
    revision_id VARCHAR(100) NOT NULL,
    content_hash VARCHAR(64),
    status VARCHAR(50) NOT NULL,
    artifact_fingerprint VARCHAR(64),
    error_code VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_ai_markdown_metadata_backfill_item_job (job_id, status, item_id)
);
