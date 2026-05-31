CREATE TABLE IF NOT EXISTS tb_skill_recommendation_job (
    job_id VARCHAR(120) PRIMARY KEY,
    target_scope VARCHAR(50) NOT NULL,
    target_filter JSON,
    embedding_provider VARCHAR(100) NOT NULL,
    embedding_model VARCHAR(200) NOT NULL,
    embedding_dimension INT NOT NULL,
    target_types TEXT,
    top_k INT NOT NULL DEFAULT 5,
    min_score DECIMAL(6,5) NOT NULL DEFAULT 0.75000,
    new_skill_min_confidence DECIMAL(6,5) NOT NULL DEFAULT 0.80000,
    existing_skill_min_score DECIMAL(6,5) NOT NULL DEFAULT 0.92000,
    status VARCHAR(30) NOT NULL,
    total_count BIGINT NOT NULL DEFAULT 0,
    processed_count BIGINT NOT NULL DEFAULT 0,
    result_count BIGINT NOT NULL DEFAULT 0,
    failed_count BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_skill_recommendation_job_status
    ON tb_skill_recommendation_job(status, created_at);

CREATE TABLE IF NOT EXISTS tb_skill_recommendation_result (
    result_id VARCHAR(120) PRIMARY KEY,
    job_id VARCHAR(120) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(120) NOT NULL,
    source_text TEXT,
    target_source_type VARCHAR(50),
    target_source_id VARCHAR(120),
    target_text TEXT,
    recommendation_type VARCHAR(50) NOT NULL,
    similarity_score DECIMAL(6,5) NOT NULL DEFAULT 0,
    confidence DECIMAL(6,5) NOT NULL DEFAULT 0,
    score_detail JSON,
    reason JSON,
    status VARCHAR(30) NOT NULL DEFAULT 'CANDIDATE',
    apply_type VARCHAR(50),
    applied_at TIMESTAMP NULL,
    applied_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_skill_recommendation_result_job
    ON tb_skill_recommendation_result(job_id);

CREATE INDEX ix_skill_recommendation_result_source
    ON tb_skill_recommendation_result(source_type, source_id);

CREATE INDEX ix_skill_recommendation_result_type
    ON tb_skill_recommendation_result(recommendation_type);

CREATE INDEX ix_skill_recommendation_result_status
    ON tb_skill_recommendation_result(status);
