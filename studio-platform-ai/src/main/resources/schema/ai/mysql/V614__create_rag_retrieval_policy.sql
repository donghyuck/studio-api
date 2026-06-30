CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_policy (
    object_type VARCHAR(100) NOT NULL,
    object_id VARCHAR(100) NOT NULL,
    retrieval_strategy VARCHAR(40) NOT NULL,
    retrieval_options_json LONGTEXT,
    question_set_id VARCHAR(80),
    evaluation_run_id VARCHAR(80),
    score DOUBLE,
    hit_rate DOUBLE,
    mrr DOUBLE,
    average_elapsed_ms DOUBLE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (object_type, object_id),
    INDEX idx_ai_rag_retrieval_policy_question_set (question_set_id),
    INDEX idx_ai_rag_retrieval_policy_updated (updated_at)
);
