ALTER TABLE tb_skill_rag_extraction_job
    ADD COLUMN generate_embeddings BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN embedding_provider VARCHAR(100),
    ADD COLUMN embedding_model VARCHAR(200),
    ADD COLUMN embedding_dimension INT,
    ADD COLUMN embedding_job_id VARCHAR(100),
    ADD COLUMN embedding_status VARCHAR(40);
