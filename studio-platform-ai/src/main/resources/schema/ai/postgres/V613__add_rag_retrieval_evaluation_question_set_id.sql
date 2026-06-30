ALTER TABLE tb_ai_rag_retrieval_evaluation
    ADD COLUMN IF NOT EXISTS question_set_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_eval_question_set
    ON tb_ai_rag_retrieval_evaluation (question_set_id, created_at DESC);
