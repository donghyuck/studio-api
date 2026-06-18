ALTER TABLE tb_ai_vector_projection
    ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'DETAIL',
    ADD COLUMN total_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN projected_count INT NOT NULL DEFAULT 0,
    ADD COLUMN sampled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sample_size INT NULL,
    ADD COLUMN sampling_strategy VARCHAR(30) NOT NULL DEFAULT 'STRATIFIED',
    ADD COLUMN max_allowed INT NOT NULL DEFAULT 1000,
    ADD COLUMN error_code VARCHAR(100) NULL;

UPDATE tb_ai_vector_projection
SET total_count = item_count,
    projected_count = item_count,
    sampled = FALSE
WHERE total_count = 0
  AND projected_count = 0;
