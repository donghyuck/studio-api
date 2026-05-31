CREATE TABLE IF NOT EXISTS tb_skillgraph_batch_job (
    job_id VARCHAR(120) PRIMARY KEY,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_count BIGINT NOT NULL DEFAULT 0,
    requested_count BIGINT NOT NULL DEFAULT 0,
    processed_count BIGINT NOT NULL DEFAULT 0,
    result_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    skipped_count BIGINT NOT NULL DEFAULT 0,
    embedding_provider VARCHAR(100),
    embedding_model VARCHAR(200),
    embedding_dimension INT NOT NULL DEFAULT 0,
    request_snapshot TEXT,
    error_message TEXT,
    created_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    started_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_skillgraph_batch_job_type_created
    ON tb_skillgraph_batch_job(job_type, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_skillgraph_batch_job_status_updated
    ON tb_skillgraph_batch_job(status, updated_at DESC);

CREATE INDEX IF NOT EXISTS ix_skillgraph_batch_job_created_by_created
    ON tb_skillgraph_batch_job(created_by, created_at DESC);
