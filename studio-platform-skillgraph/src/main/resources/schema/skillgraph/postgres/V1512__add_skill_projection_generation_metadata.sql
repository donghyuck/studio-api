ALTER TABLE tb_skill_projection
    ADD COLUMN IF NOT EXISTS reduction_algorithm VARCHAR(30),
    ADD COLUMN IF NOT EXISTS clustering_algorithm VARCHAR(30),
    ADD COLUMN IF NOT EXISTS embedding_provider VARCHAR(100),
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(200),
    ADD COLUMN IF NOT EXISTS embedding_dimension INT;

UPDATE tb_skill_projection
SET reduction_algorithm = COALESCE(reduction_algorithm, 'UMAP'),
    clustering_algorithm = COALESCE(clustering_algorithm, 'DISTANCE_THRESHOLD')
WHERE reduction_algorithm IS NULL
   OR clustering_algorithm IS NULL;

CREATE INDEX IF NOT EXISTS idx_skill_projection_generation_lookup
    ON tb_skill_projection(projection_id, embedding_provider, embedding_model, embedding_dimension);
