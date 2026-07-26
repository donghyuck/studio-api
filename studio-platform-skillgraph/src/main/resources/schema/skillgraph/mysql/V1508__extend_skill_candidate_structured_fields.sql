ALTER TABLE tb_skill_candidate
    ADD COLUMN search_text LONGTEXT,
    ADD COLUMN skill_type VARCHAR(40),
    ADD COLUMN action VARCHAR(200),
    ADD COLUMN technology LONGTEXT,
    ADD COLUMN target TEXT,
    ADD COLUMN evidence_text TEXT,
    ADD COLUMN context TEXT,
    ADD COLUMN difficulty VARCHAR(40),
    ADD COLUMN extraction_method VARCHAR(80),
    ADD COLUMN confidence_detail LONGTEXT,
    ADD COLUMN source_position LONGTEXT,
    ADD COLUMN normalization_info LONGTEXT,
    ADD COLUMN mapping_candidates LONGTEXT,
    ADD COLUMN review_status VARCHAR(40),
    ADD COLUMN feedback TEXT;

CREATE TABLE IF NOT EXISTS tb_skill_embedding (
    embedding_id VARCHAR(100) PRIMARY KEY,
    source_type VARCHAR(100) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    embedding_provider VARCHAR(100) NOT NULL,
    embedding_model VARCHAR(200) NOT NULL,
    embedding_dimension INT NOT NULL,
    embedding_text LONGTEXT NOT NULL,
    embedding_json JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_skill_embedding_source_model UNIQUE (source_type, source_id, embedding_provider, embedding_model)
);

CREATE INDEX idx_skill_embedding_source
    ON tb_skill_embedding(source_type, source_id);
