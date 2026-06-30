CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_policy (
    object_type VARCHAR(100) NOT NULL,
    object_id VARCHAR(100) NOT NULL,
    retrieval_strategy VARCHAR(40) NOT NULL,
    retrieval_options_json TEXT,
    question_set_id VARCHAR(80),
    evaluation_run_id VARCHAR(80),
    score DOUBLE PRECISION,
    hit_rate DOUBLE PRECISION,
    mrr DOUBLE PRECISION,
    average_elapsed_ms DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (object_type, object_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_policy_question_set
    ON tb_ai_rag_retrieval_policy (question_set_id);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_policy_updated
    ON tb_ai_rag_retrieval_policy (updated_at DESC);
