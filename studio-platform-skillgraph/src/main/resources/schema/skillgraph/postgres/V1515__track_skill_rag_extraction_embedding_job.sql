ALTER TABLE tb_skill_rag_extraction_job
    ADD COLUMN IF NOT EXISTS generate_embeddings BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(100),
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(200),
    ADD COLUMN IF NOT EXISTS embedding_dimension INT,
    ADD COLUMN IF NOT EXISTS embedding_job_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(40);
