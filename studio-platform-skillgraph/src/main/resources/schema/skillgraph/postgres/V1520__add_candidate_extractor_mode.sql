ALTER TABLE tb_skill_rag_extraction_job
    ADD COLUMN IF NOT EXISTS candidate_extractor_mode VARCHAR(20);
