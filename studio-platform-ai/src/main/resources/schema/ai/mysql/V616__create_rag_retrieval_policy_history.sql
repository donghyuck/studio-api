CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_policy_history (
    history_id VARCHAR(80) NOT NULL PRIMARY KEY,
    object_type VARCHAR(100) NOT NULL,
    object_id VARCHAR(100) NOT NULL,
    retrieval_strategy VARCHAR(40) NOT NULL,
    reason VARCHAR(80) NOT NULL,
    question_set_id VARCHAR(80),
    evaluation_run_id VARCHAR(80),
    score DOUBLE,
    hit_rate DOUBLE,
    mrr DOUBLE,
    average_elapsed_ms DOUBLE,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_ai_rag_retrieval_policy_hist_object (object_type, object_id, created_at)
);
