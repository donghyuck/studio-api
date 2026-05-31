CREATE TABLE IF NOT EXISTS tb_skill_recommendation_job (
    job_id VARCHAR(120) PRIMARY KEY,
    target_scope VARCHAR(50) NOT NULL,
    target_filter JSONB,
    embedding_provider VARCHAR(100) NOT NULL,
    embedding_model VARCHAR(200) NOT NULL,
    embedding_dimension INT NOT NULL,
    target_types TEXT,
    top_k INT NOT NULL DEFAULT 5,
    min_score NUMERIC(6,5) NOT NULL DEFAULT 0.75000,
    new_skill_min_confidence NUMERIC(6,5) NOT NULL DEFAULT 0.80000,
    existing_skill_min_score NUMERIC(6,5) NOT NULL DEFAULT 0.92000,
    status VARCHAR(30) NOT NULL,
    total_count BIGINT NOT NULL DEFAULT 0,
    processed_count BIGINT NOT NULL DEFAULT 0,
    result_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_skill_recommendation_job_status
    ON tb_skill_recommendation_job(status, created_at DESC);

CREATE TABLE IF NOT EXISTS tb_skill_recommendation_result (
    result_id VARCHAR(120) PRIMARY KEY,
    job_id VARCHAR(120) NOT NULL REFERENCES tb_skill_recommendation_job(job_id),
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(120) NOT NULL,
    source_text TEXT,
    target_source_type VARCHAR(50),
    target_source_id VARCHAR(120),
    target_text TEXT,
    recommendation_type VARCHAR(50) NOT NULL,
    similarity_score NUMERIC(6,5) NOT NULL DEFAULT 0,
    confidence NUMERIC(6,5) NOT NULL DEFAULT 0,
    score_detail JSONB,
    reason JSONB,
    status VARCHAR(30) NOT NULL DEFAULT 'CANDIDATE',
    apply_type VARCHAR(50),
    applied_at TIMESTAMP,
    applied_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_skill_recommendation_result_job
    ON tb_skill_recommendation_result(job_id);

CREATE INDEX IF NOT EXISTS ix_skill_recommendation_result_source
    ON tb_skill_recommendation_result(source_type, source_id);

CREATE INDEX IF NOT EXISTS ix_skill_recommendation_result_type
    ON tb_skill_recommendation_result(recommendation_type);

CREATE INDEX IF NOT EXISTS ix_skill_recommendation_result_status
    ON tb_skill_recommendation_result(status);
