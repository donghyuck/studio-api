ALTER TABLE tb_skill_candidate
    ADD COLUMN IF NOT EXISTS search_text LONGTEXT,
    ADD COLUMN IF NOT EXISTS skill_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS action VARCHAR(200),
    ADD COLUMN IF NOT EXISTS technology LONGTEXT,
    ADD COLUMN IF NOT EXISTS target TEXT,
    ADD COLUMN IF NOT EXISTS evidence_text TEXT,
    ADD COLUMN IF NOT EXISTS context TEXT,
    ADD COLUMN IF NOT EXISTS difficulty VARCHAR(40),
    ADD COLUMN IF NOT EXISTS extraction_method VARCHAR(80),
    ADD COLUMN IF NOT EXISTS confidence_detail LONGTEXT,
    ADD COLUMN IF NOT EXISTS source_position LONGTEXT,
    ADD COLUMN IF NOT EXISTS normalization_info LONGTEXT,
    ADD COLUMN IF NOT EXISTS mapping_candidates LONGTEXT,
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS feedback TEXT;

CREATE TABLE IF NOT EXISTS tb_skill_embedding (
    embedding_id VARCHAR(100) PRIMARY KEY,
    source_type VARCHAR(100) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    embedding_provider VARCHAR(100) NOT NULL,
    embedding_model VARCHAR(200) NOT NULL,
    embedding_dimension INT NOT NULL,
    embedding_text LONGTEXT NOT NULL,
    embedding_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_skill_embedding_source_model UNIQUE (source_type, source_id, embedding_provider, embedding_model)
);

CREATE INDEX idx_skill_embedding_source
    ON tb_skill_embedding(source_type, source_id);
