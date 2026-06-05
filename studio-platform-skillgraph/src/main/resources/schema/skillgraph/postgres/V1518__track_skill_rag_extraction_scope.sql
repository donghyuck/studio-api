ALTER TABLE tb_skill_rag_extraction_job
    ADD COLUMN IF NOT EXISTS query_text VARCHAR(500),
    ADD COLUMN IF NOT EXISTS extraction_mode VARCHAR(30) NOT NULL DEFAULT 'ALL_CHUNKS',
    ADD COLUMN IF NOT EXISTS selected_chunk_ids TEXT;
