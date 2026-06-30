CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_evaluation (
    run_id VARCHAR(80) NOT NULL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    object_type VARCHAR(100),
    object_id VARCHAR(100),
    embedding_profile_id VARCHAR(200),
    embedding_provider VARCHAR(200),
    embedding_model VARCHAR(300),
    top_k INT,
    min_score DOUBLE,
    result_json LONGTEXT NOT NULL,
    INDEX idx_ai_rag_retrieval_eval_created (created_at),
    INDEX idx_ai_rag_retrieval_eval_object (object_type, object_id, created_at)
);
