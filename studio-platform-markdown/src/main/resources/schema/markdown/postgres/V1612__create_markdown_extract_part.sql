CREATE TABLE IF NOT EXISTS tb_ai_markdown_extract_part (
    part_id VARCHAR(80) PRIMARY KEY,
    revision_id VARCHAR(80) NOT NULL,
    page_from INTEGER NOT NULL,
    page_to INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    engine VARCHAR(64) NOT NULL,
    text_length INTEGER NOT NULL DEFAULT 0,
    markdown_text TEXT NULL,
    error_code VARCHAR(128) NULL,
    error_message VARCHAR(1000) NULL,
    elapsed_ms BIGINT NULL,
    metadata_json TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_markdown_extract_part_revision
        FOREIGN KEY (revision_id) REFERENCES tb_ai_markdown_revision(revision_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_markdown_extract_part_revision_page
    ON tb_ai_markdown_extract_part (revision_id, page_from, page_to);

CREATE INDEX IF NOT EXISTS idx_markdown_extract_part_revision_status
    ON tb_ai_markdown_extract_part (revision_id, status);
