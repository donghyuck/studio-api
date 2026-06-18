ALTER TABLE tb_ai_vector_projection
    ADD COLUMN IF NOT EXISTS mode VARCHAR(20) NOT NULL DEFAULT 'DETAIL',
    ADD COLUMN IF NOT EXISTS total_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS projected_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sampled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS sample_size INT,
    ADD COLUMN IF NOT EXISTS sampling_strategy VARCHAR(30) NOT NULL DEFAULT 'STRATIFIED',
    ADD COLUMN IF NOT EXISTS max_allowed INT NOT NULL DEFAULT 1000,
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(100);

UPDATE tb_ai_vector_projection
SET total_count = item_count,
    projected_count = item_count,
    sampled = FALSE
WHERE total_count = 0
  AND projected_count = 0;
