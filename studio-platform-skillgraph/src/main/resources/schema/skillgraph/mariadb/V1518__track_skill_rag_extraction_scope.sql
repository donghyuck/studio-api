ALTER TABLE tb_skill_rag_extraction_job
    ADD COLUMN query_text VARCHAR(500) NULL,
    ADD COLUMN extraction_mode VARCHAR(30) NOT NULL DEFAULT 'ALL_CHUNKS',
    ADD COLUMN selected_chunk_ids TEXT NULL;
