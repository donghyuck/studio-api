CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_policy_usage (
    usage_id VARCHAR(80) PRIMARY KEY,
    object_type VARCHAR(100) NOT NULL,
    object_id VARCHAR(100) NOT NULL,
    retrieval_strategy VARCHAR(40) NOT NULL,
    question_set_id VARCHAR(80),
    evaluation_run_id VARCHAR(80),
    top_k INTEGER,
    min_score DOUBLE PRECISION,
    result_count INTEGER NOT NULL,
    skipped_chat BOOLEAN NOT NULL DEFAULT FALSE,
    elapsed_ms BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_policy_usage_object
    ON tb_ai_rag_retrieval_policy_usage (object_type, object_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_policy_usage_strategy
    ON tb_ai_rag_retrieval_policy_usage (retrieval_strategy, created_at DESC);
