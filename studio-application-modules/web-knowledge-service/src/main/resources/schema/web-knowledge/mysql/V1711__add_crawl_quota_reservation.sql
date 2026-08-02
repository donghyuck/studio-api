ALTER TABLE web_knowledge_crawl_run
    ADD COLUMN IF NOT EXISTS reserved_page_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reserved_snapshot_bytes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS quota_released_at TIMESTAMP(6) NULL;
