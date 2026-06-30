CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_evaluation_question_set (
    question_set_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    questions_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_eval_qset_updated
    ON tb_ai_rag_retrieval_evaluation_question_set (updated_at DESC);
