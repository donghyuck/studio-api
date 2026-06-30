CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_evaluation_job (
    job_id VARCHAR(100) PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    total_questions INTEGER NOT NULL DEFAULT 0,
    completed_questions INTEGER NOT NULL DEFAULT 0,
    total_strategies INTEGER NOT NULL DEFAULT 0,
    completed_strategies INTEGER NOT NULL DEFAULT 0,
    current_strategy VARCHAR(80),
    current_question TEXT,
    run_id VARCHAR(80),
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_eval_job_created
    ON tb_ai_rag_retrieval_evaluation_job (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_eval_job_status
    ON tb_ai_rag_retrieval_evaluation_job (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_rag_retrieval_eval_job_run
    ON tb_ai_rag_retrieval_evaluation_job (run_id);
