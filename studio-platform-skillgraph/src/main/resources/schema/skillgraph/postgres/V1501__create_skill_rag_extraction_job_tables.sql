CREATE TABLE IF NOT EXISTS tb_skill_rag_extraction_job (
    job_id VARCHAR(100) PRIMARY KEY,
    object_type VARCHAR(100) NOT NULL,
    object_id VARCHAR(200) NOT NULL,
    document_id VARCHAR(200),
    status VARCHAR(40) NOT NULL,
    requested_chunks INT NOT NULL DEFAULT 0,
    total_chunks INT NOT NULL DEFAULT 0,
    processed_chunks INT NOT NULL DEFAULT 0,
    succeeded_chunks INT NOT NULL DEFAULT 0,
    failed_chunks INT NOT NULL DEFAULT 0,
    extracted_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_skill_rag_extraction_job_status
    ON tb_skill_rag_extraction_job(status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_skill_rag_extraction_job_object
    ON tb_skill_rag_extraction_job(object_type, object_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS tb_skill_rag_extraction_job_item (
    job_id VARCHAR(100) NOT NULL,
    chunk_id VARCHAR(200) NOT NULL,
    document_id VARCHAR(200),
    source_id VARCHAR(200),
    source_chunk_id VARCHAR(100),
    extracted_count INT NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (job_id, chunk_id)
);

CREATE INDEX IF NOT EXISTS idx_skill_rag_extraction_job_item_status
    ON tb_skill_rag_extraction_job_item(job_id, status, created_at);
