CREATE TABLE IF NOT EXISTS tb_ai_rag_retrieval_evaluation_job (
    job_id VARCHAR(100) NOT NULL PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    total_questions INT NOT NULL DEFAULT 0,
    completed_questions INT NOT NULL DEFAULT 0,
    total_strategies INT NOT NULL DEFAULT 0,
    completed_strategies INT NOT NULL DEFAULT 0,
    current_strategy VARCHAR(80),
    current_question TEXT,
    run_id VARCHAR(80),
    error_message TEXT,
    INDEX idx_ai_rag_retrieval_eval_job_created (created_at),
    INDEX idx_ai_rag_retrieval_eval_job_status (status, created_at),
    INDEX idx_ai_rag_retrieval_eval_job_run (run_id)
);
