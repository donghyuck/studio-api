CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_policy_history (
    history_id VARCHAR(80) PRIMARY KEY,
    object_type VARCHAR(100) NOT NULL,
    object_id VARCHAR(100) NOT NULL,
    retrieval_strategy VARCHAR(40) NOT NULL,
    reason VARCHAR(80) NOT NULL,
    question_set_id VARCHAR(80),
    evaluation_run_id VARCHAR(80),
    score DOUBLE PRECISION,
    hit_rate DOUBLE PRECISION,
    mrr DOUBLE PRECISION,
    average_elapsed_ms DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_policy_hist_object
    ON tb_ai_rag_retrieval_policy_history (object_type, object_id, created_at DESC);
