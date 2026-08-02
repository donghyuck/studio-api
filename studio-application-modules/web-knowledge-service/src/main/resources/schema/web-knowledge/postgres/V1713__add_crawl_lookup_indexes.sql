CREATE INDEX IF NOT EXISTS idx_web_page_canonical
    ON web_knowledge_page(workspace_id, source_id, canonical_url_hash);
CREATE INDEX IF NOT EXISTS idx_web_page_revision_content
    ON web_knowledge_page_revision(workspace_id, page_id, content_hash);
CREATE INDEX IF NOT EXISTS idx_web_corpus_page_revision
    ON web_knowledge_corpus_page(workspace_id, corpus_revision_id, page_revision_id);
CREATE INDEX IF NOT EXISTS idx_web_crawl_run_owner_lease
    ON web_knowledge_crawl_run(workspace_id, lease_owner, lease_expires_at);
