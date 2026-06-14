CREATE TABLE IF NOT EXISTS tb_ai_markdown_document (
    document_id VARCHAR(100) PRIMARY KEY,
    source_attachment_id BIGINT NOT NULL UNIQUE,
    current_revision_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_ai_markdown_revision (
    revision_id VARCHAR(100) PRIMARY KEY,
    document_id VARCHAR(100) NOT NULL,
    source_attachment_id BIGINT NOT NULL,
    result_attachment_id BIGINT,
    document_convert_job_id VARCHAR(100),
    extractor_type VARCHAR(40) NOT NULL,
    extractor_version VARCHAR(100) NOT NULL,
    options_json TEXT NOT NULL,
    options_hash VARCHAR(64) NOT NULL,
    source_content_hash VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64),
    markdown_text TEXT,
    source_file_name VARCHAR(500),
    source_format VARCHAR(40),
    source_object_type VARCHAR(100),
    source_object_id VARCHAR(200),
    status VARCHAR(30) NOT NULL,
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_markdown_revision_document
    ON tb_ai_markdown_revision(document_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_markdown_revision_convert_job
    ON tb_ai_markdown_revision(document_convert_job_id);
CREATE INDEX IF NOT EXISTS idx_ai_markdown_revision_dedupe
    ON tb_ai_markdown_revision(source_attachment_id, source_content_hash, extractor_type, extractor_version, options_hash, status);

CREATE TABLE IF NOT EXISTS tb_ai_markdown_locator (
    locator_id VARCHAR(100) PRIMARY KEY,
    revision_id VARCHAR(100) NOT NULL,
    locator_type VARCHAR(40) NOT NULL,
    locator_no INTEGER,
    title VARCHAR(500),
    start_offset INTEGER NOT NULL,
    end_offset INTEGER NOT NULL,
    source_ref VARCHAR(500),
    metadata_json TEXT
);
CREATE INDEX IF NOT EXISTS idx_ai_markdown_locator_revision
    ON tb_ai_markdown_locator(revision_id, start_offset);

CREATE TABLE IF NOT EXISTS tb_ai_markdown_resource (
    resource_id VARCHAR(100) PRIMARY KEY,
    revision_id VARCHAR(100) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    name VARCHAR(500),
    attachment_id BIGINT,
    metadata_json TEXT
);
CREATE INDEX IF NOT EXISTS idx_ai_markdown_resource_revision
    ON tb_ai_markdown_resource(revision_id);
