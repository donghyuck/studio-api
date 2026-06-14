CREATE TABLE IF NOT EXISTS tb_ai_markdown_pipeline_execution (
    revision_id VARCHAR(100) PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    current_stage VARCHAR(40) NOT NULL,
    last_completed_stage VARCHAR(40),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL
);
