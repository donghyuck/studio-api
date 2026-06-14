ALTER TABLE tb_skill_candidate
    ADD COLUMN source_markdown_document_id VARCHAR(100),
    ADD COLUMN source_markdown_revision_id VARCHAR(100),
    ADD COLUMN source_metadata_json TEXT;

CREATE INDEX idx_skill_candidate_markdown_revision
    ON tb_skill_candidate(source_markdown_revision_id);
