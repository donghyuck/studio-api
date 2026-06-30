CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_evaluation_question_set (
    question_set_id VARCHAR(100) NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    questions_json LONGTEXT NOT NULL,
    INDEX idx_ai_rag_retrieval_eval_qset_updated (updated_at)
);
