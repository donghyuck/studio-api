CREATE INDEX IF NOT EXISTS idx_skill_embedding_model_lookup
    ON tb_skill_embedding(source_type, embedding_provider, embedding_model, embedding_dimension, source_id);

CREATE INDEX IF NOT EXISTS idx_skill_embedding_source_created
    ON tb_skill_embedding(source_type, source_id, created_at DESC);
