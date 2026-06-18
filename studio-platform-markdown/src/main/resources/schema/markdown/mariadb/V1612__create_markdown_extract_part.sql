CREATE TABLE IF NOT EXISTS tb_ai_markdown_extract_part (
    part_id VARCHAR(80) PRIMARY KEY,
    revision_id VARCHAR(80) NOT NULL,
    page_from INT NOT NULL,
    page_to INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    engine VARCHAR(64) NOT NULL,
    text_length INT NOT NULL DEFAULT 0,
    markdown_text LONGTEXT NULL,
    error_code VARCHAR(128) NULL,
    error_message VARCHAR(1000) NULL,
    elapsed_ms BIGINT NULL,
    metadata_json LONGTEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_markdown_extract_part_revision
        FOREIGN KEY (revision_id) REFERENCES tb_ai_markdown_revision(revision_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_markdown_extract_part_revision_page
    ON tb_ai_markdown_extract_part (revision_id, page_from, page_to);

CREATE INDEX idx_markdown_extract_part_revision_status
    ON tb_ai_markdown_extract_part (revision_id, status);
