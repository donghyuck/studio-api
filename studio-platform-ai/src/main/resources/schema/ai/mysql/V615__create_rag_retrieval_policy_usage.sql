CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_policy_usage (
    usage_id VARCHAR(80) NOT NULL PRIMARY KEY,
    object_type VARCHAR(100) NOT NULL,
    object_id VARCHAR(100) NOT NULL,
    retrieval_strategy VARCHAR(40) NOT NULL,
    question_set_id VARCHAR(80),
    evaluation_run_id VARCHAR(80),
    top_k INT,
    min_score DOUBLE,
    result_count INT NOT NULL,
    skipped_chat BOOLEAN NOT NULL DEFAULT FALSE,
    elapsed_ms BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_ai_rag_retrieval_policy_usage_object (object_type, object_id, created_at),
    INDEX idx_ai_rag_retrieval_policy_usage_strategy (retrieval_strategy, created_at)
);
