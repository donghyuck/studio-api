INSERT IGNORE INTO web_knowledge_crawl_run (
    run_id, workspace_id, source_id, status, policy_json, policy_hash,
    discovered_count, fetched_count, indexed_count, created_at, updated_at, completed_at)
SELECT
    CONCAT('legacy-run-', MD5(revision.revision_id)),
    source.workspace_id, source.source_id, 'COMPLETED', '{}', 'legacy-single-page-v1',
    1, 1, 1, revision.created_at, revision.updated_at, revision.updated_at
FROM web_knowledge_source source
JOIN web_knowledge_revision revision ON revision.revision_id = source.current_revision_id
WHERE source.collection_mode = 'SINGLE_PAGE'
  AND revision.status = 'COMPLETED'
  AND revision.content_hash IS NOT NULL;

INSERT IGNORE INTO web_knowledge_page (
    page_id, workspace_id, source_id, normalized_url, normalized_url_hash,
    canonical_url, canonical_url_hash, current_page_revision_id, active,
    missing_run_count, first_seen_at, last_seen_at, created_at, updated_at)
SELECT
    CONCAT('legacy-page-', MD5(revision.revision_id)),
    source.workspace_id, source.source_id, source.normalized_url, source.normalized_url_hash,
    COALESCE(source.canonical_url, source.normalized_url), source.normalized_url_hash,
    revision.revision_id, TRUE, 0,
    revision.created_at, revision.updated_at, revision.created_at, revision.updated_at
FROM web_knowledge_source source
JOIN web_knowledge_revision revision ON revision.revision_id = source.current_revision_id
WHERE source.collection_mode = 'SINGLE_PAGE'
  AND revision.status = 'COMPLETED'
  AND revision.content_hash IS NOT NULL;

INSERT IGNORE INTO web_knowledge_page_revision (
    page_revision_id, workspace_id, source_id, page_id, run_id, status,
    title, publisher, language_code, published_at, source_modified_at,
    retrieved_at, etag, last_modified, content_type, content_length,
    content_hash, normalized_snapshot, content_preview, metadata_json,
    created_at, updated_at)
SELECT
    revision.revision_id, source.workspace_id, source.source_id,
    CONCAT('legacy-page-', MD5(revision.revision_id)),
    CONCAT('legacy-run-', MD5(revision.revision_id)),
    'COMPLETED', revision.title, revision.publisher, revision.language_code,
    revision.published_at, revision.source_modified_at, revision.retrieved_at,
    revision.etag, revision.last_modified, revision.content_type, revision.content_length,
    revision.content_hash, revision.normalized_snapshot, revision.content_preview,
    revision.metadata_json, revision.created_at, revision.updated_at
FROM web_knowledge_source source
JOIN web_knowledge_revision revision ON revision.revision_id = source.current_revision_id
WHERE source.collection_mode = 'SINGLE_PAGE'
  AND revision.status = 'COMPLETED'
  AND revision.content_hash IS NOT NULL;

INSERT IGNORE INTO web_knowledge_corpus_revision (
    corpus_revision_id, workspace_id, source_id, run_id, status,
    manifest_hash, policy_hash, embedding_space_id, page_count,
    created_at, completed_at)
SELECT
    CONCAT('legacy-corpus-', MD5(revision.revision_id)),
    source.workspace_id, source.source_id,
    CONCAT('legacy-run-', MD5(revision.revision_id)),
    'COMPLETED', revision.content_hash, 'legacy-single-page-v1',
    source.embedding_space_id, 1, revision.created_at, revision.updated_at
FROM web_knowledge_source source
JOIN web_knowledge_revision revision ON revision.revision_id = source.current_revision_id
WHERE source.collection_mode = 'SINGLE_PAGE'
  AND revision.status = 'COMPLETED'
  AND revision.content_hash IS NOT NULL
  AND source.embedding_space_id IS NOT NULL;

INSERT IGNORE INTO web_knowledge_corpus_page (
    corpus_page_id, workspace_id, source_id, corpus_revision_id,
    page_id, page_revision_id, page_order, created_at)
SELECT
    CONCAT('legacy-cp-', MD5(revision.revision_id)),
    source.workspace_id, source.source_id,
    CONCAT('legacy-corpus-', MD5(revision.revision_id)),
    CONCAT('legacy-page-', MD5(revision.revision_id)),
    revision.revision_id, 0, revision.created_at
FROM web_knowledge_source source
JOIN web_knowledge_revision revision ON revision.revision_id = source.current_revision_id
WHERE source.collection_mode = 'SINGLE_PAGE'
  AND revision.status = 'COMPLETED'
  AND revision.content_hash IS NOT NULL
  AND source.embedding_space_id IS NOT NULL;

UPDATE web_knowledge_source
SET current_corpus_revision_id = CONCAT('legacy-corpus-', MD5(current_revision_id))
WHERE collection_mode = 'SINGLE_PAGE'
  AND current_revision_id IS NOT NULL
  AND embedding_space_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM web_knowledge_corpus_revision corpus
      WHERE corpus.corpus_revision_id = CONCAT('legacy-corpus-', MD5(web_knowledge_source.current_revision_id))
        AND corpus.source_id = web_knowledge_source.source_id
        AND corpus.status = 'COMPLETED'
  );
