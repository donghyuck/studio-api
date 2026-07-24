ALTER TABLE tb_ai_rag_retrieval_evaluation
    ADD COLUMN IF NOT EXISTS model_deployment_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS catalog_id VARCHAR(300),
    ADD COLUMN IF NOT EXISTS embedding_space_id VARCHAR(80);

ALTER TABLE tb_ai_rag_index_job
    ADD COLUMN IF NOT EXISTS embedding_deployment_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS catalog_id VARCHAR(300),
    ADD COLUMN IF NOT EXISTS embedding_space_id VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_ai_document_chunk_embedding_space_v2
    ON tb_ai_document_chunk ((metadata ->> 'embeddingSpaceIdV2'), embedding_dimension);

CREATE TABLE IF NOT EXISTS tb_ai_model_data_migration (
    migration_id VARCHAR(120) PRIMARY KEY,
    migration_version VARCHAR(80) NOT NULL,
    target_name VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    last_key VARCHAR(300),
    scanned_count BIGINT NOT NULL DEFAULT 0,
    updated_count BIGINT NOT NULL DEFAULT 0,
    unresolved_count BIGINT NOT NULL DEFAULT 0,
    checksum VARCHAR(128),
    report_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
