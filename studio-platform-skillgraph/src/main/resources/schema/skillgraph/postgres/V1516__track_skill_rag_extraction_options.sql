ALTER TABLE tb_skill_rag_extraction_job
    ADD COLUMN IF NOT EXISTS exclude_extracted BOOLEAN NOT NULL DEFAULT false;
