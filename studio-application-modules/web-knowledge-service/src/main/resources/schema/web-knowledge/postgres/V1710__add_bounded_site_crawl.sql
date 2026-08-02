ALTER TABLE web_knowledge_source
    ADD COLUMN IF NOT EXISTS collection_mode VARCHAR(32) NOT NULL DEFAULT 'SINGLE_PAGE',
    ADD COLUMN IF NOT EXISTS crawl_policy_json TEXT,
    ADD COLUMN IF NOT EXISTS crawl_policy_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS current_corpus_revision_id VARCHAR(80);

CREATE TABLE IF NOT EXISTS web_knowledge_crawl_run (
    run_id VARCHAR(80) PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    source_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_source(source_id),
    retry_of_run_id VARCHAR(80),
    status VARCHAR(32) NOT NULL,
    policy_json TEXT NOT NULL,
    policy_hash VARCHAR(64) NOT NULL,
    requested_by VARCHAR(160),
    discovered_count INTEGER NOT NULL DEFAULT 0,
    fetched_count INTEGER NOT NULL DEFAULT 0,
    indexed_count INTEGER NOT NULL DEFAULT 0,
    unchanged_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    removed_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    response_bytes BIGINT NOT NULL DEFAULT 0,
    normalized_chars BIGINT NOT NULL DEFAULT 0,
    truncated BOOLEAN NOT NULL DEFAULT FALSE,
    truncation_reason VARCHAR(80),
    error_code VARCHAR(80),
    cancel_requested_at TIMESTAMP WITH TIME ZONE,
    lease_owner VARCHAR(160),
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    heartbeat_at TIMESTAMP WITH TIME ZONE,
    attempt_no INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS web_knowledge_crawl_item (
    item_id VARCHAR(80) PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    run_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_crawl_run(run_id),
    source_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_source(source_id),
    normalized_url VARCHAR(2048) NOT NULL,
    normalized_url_hash VARCHAR(64) NOT NULL,
    parent_url_hash VARCHAR(64),
    depth INTEGER NOT NULL,
    discovery_order INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    page_id VARCHAR(80),
    page_revision_id VARCHAR(80),
    error_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_web_crawl_item_url UNIQUE (run_id, normalized_url_hash)
);

CREATE TABLE IF NOT EXISTS web_knowledge_page (
    page_id VARCHAR(80) PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    source_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_source(source_id),
    normalized_url VARCHAR(2048) NOT NULL,
    normalized_url_hash VARCHAR(64) NOT NULL,
    canonical_url VARCHAR(2048),
    canonical_url_hash VARCHAR(64),
    current_page_revision_id VARCHAR(80),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    missing_run_count INTEGER NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_web_page_url UNIQUE (workspace_id, source_id, normalized_url_hash)
);

CREATE TABLE IF NOT EXISTS web_knowledge_page_revision (
    page_revision_id VARCHAR(80) PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    source_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_source(source_id),
    page_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_page(page_id),
    run_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_crawl_run(run_id),
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

CREATE TABLE IF NOT EXISTS web_knowledge_corpus_revision (
    corpus_revision_id VARCHAR(80) PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    source_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_source(source_id),
    run_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_crawl_run(run_id),
    status VARCHAR(32) NOT NULL,
    manifest_hash VARCHAR(64) NOT NULL,
    policy_hash VARCHAR(64) NOT NULL,
    embedding_space_id VARCHAR(200) NOT NULL,
    page_count INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS web_knowledge_corpus_page (
    corpus_page_id VARCHAR(80) PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    source_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_source(source_id),
    corpus_revision_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_corpus_revision(corpus_revision_id),
    page_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_page(page_id),
    page_revision_id VARCHAR(80) NOT NULL REFERENCES web_knowledge_page_revision(page_revision_id),
    page_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_web_corpus_page UNIQUE (corpus_revision_id, page_id)
);

CREATE TABLE IF NOT EXISTS web_knowledge_quota_usage (
    workspace_id BIGINT PRIMARY KEY,
    source_count BIGINT NOT NULL DEFAULT 0,
    active_page_count BIGINT NOT NULL DEFAULT 0,
    normalized_snapshot_bytes BIGINT NOT NULL DEFAULT 0,
    reserved_page_count BIGINT NOT NULL DEFAULT 0,
    reserved_snapshot_bytes BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_web_crawl_run_source_status
    ON web_knowledge_crawl_run(workspace_id, source_id, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_web_crawl_run_lease
    ON web_knowledge_crawl_run(status, lease_expires_at);
CREATE INDEX IF NOT EXISTS idx_web_crawl_item_frontier
    ON web_knowledge_crawl_item(run_id, status, depth, discovery_order);
CREATE INDEX IF NOT EXISTS idx_web_page_source_active
    ON web_knowledge_page(workspace_id, source_id, active, normalized_url_hash);
CREATE INDEX IF NOT EXISTS idx_web_page_revision_run
    ON web_knowledge_page_revision(workspace_id, source_id, run_id, status);
CREATE INDEX IF NOT EXISTS idx_web_corpus_source
    ON web_knowledge_corpus_revision(workspace_id, source_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_web_corpus_page_manifest
    ON web_knowledge_corpus_page(workspace_id, corpus_revision_id, page_order);
