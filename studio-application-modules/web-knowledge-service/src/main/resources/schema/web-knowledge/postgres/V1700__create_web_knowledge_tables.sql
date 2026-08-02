CREATE TABLE IF NOT EXISTS web_knowledge_source (
    source_id VARCHAR(80) PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    input_url VARCHAR(2048) NOT NULL,
    normalized_url VARCHAR(2048) NOT NULL,
    normalized_url_hash VARCHAR(64) NOT NULL,
    active_dedupe_key VARCHAR(256),
    canonical_url VARCHAR(2048),
    source_host VARCHAR(255) NOT NULL,
    display_name VARCHAR(300),
    embedding_deployment_id VARCHAR(160) NOT NULL,
    embedding_space_id VARCHAR(200),
    current_revision_id VARCHAR(80),
    status VARCHAR(32) NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(160),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS web_knowledge_revision (
    revision_id VARCHAR(80) PRIMARY KEY,
    source_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_source(source_id),
    rag_job_id VARCHAR(80),
    status VARCHAR(32) NOT NULL,
    title VARCHAR(500),
    publisher VARCHAR(300),
    language_code VARCHAR(32),
    published_at TIMESTAMP WITH TIME ZONE,
    source_modified_at TIMESTAMP WITH TIME ZONE,
    retrieved_at TIMESTAMP WITH TIME ZONE,
    etag VARCHAR(500),
    last_modified VARCHAR(500),
    content_type VARCHAR(160),
    content_length BIGINT,
    content_hash VARCHAR(64),
    normalized_snapshot TEXT,
    content_preview VARCHAR(500),
    metadata_json TEXT,
    error_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_web_knowledge_source_workspace
    ON web_knowledge_source(workspace_id, archived, updated_at);
CREATE INDEX IF NOT EXISTS idx_web_knowledge_source_lookup
    ON web_knowledge_source(workspace_id, normalized_url_hash, embedding_deployment_id, archived);
CREATE UNIQUE INDEX IF NOT EXISTS uk_web_knowledge_source_active
    ON web_knowledge_source(workspace_id, active_dedupe_key);
CREATE INDEX IF NOT EXISTS idx_web_knowledge_revision_source
    ON web_knowledge_revision(source_id, created_at);
CREATE INDEX IF NOT EXISTS idx_web_knowledge_revision_status
    ON web_knowledge_revision(status, updated_at);
