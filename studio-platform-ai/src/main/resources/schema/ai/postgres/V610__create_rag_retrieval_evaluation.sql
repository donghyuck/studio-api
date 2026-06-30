CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_evaluation (
    run_id VARCHAR(80) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    object_type VARCHAR(100),
    object_id VARCHAR(100),
    embedding_profile_id VARCHAR(200),
    embedding_provider VARCHAR(200),
    embedding_model VARCHAR(300),
    top_k INTEGER,
    min_score DOUBLE PRECISION,
    result_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_eval_created
    ON tb_ai_rag_retrieval_evaluation (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_eval_object
    ON tb_ai_rag_retrieval_evaluation (object_type, object_id, created_at DESC);
