CREATE TABLE IF NOT EXISTS tb_ai_rag_chunk_stage (
  id          BIGSERIAL PRIMARY KEY,
  object_type VARCHAR(100) NOT NULL,
  object_id   VARCHAR(150) NOT NULL,
  document_id VARCHAR(200),
  chunk_index INT NOT NULL,
  chunk_id    VARCHAR(200),
  text        TEXT NOT NULL,
  metadata    TEXT,
  created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_rag_chunk_stage_scope
    ON tb_ai_rag_chunk_stage(object_type, object_id, COALESCE(document_id, ''), chunk_index);

CREATE INDEX IF NOT EXISTS idx_ai_rag_chunk_stage_scope
    ON tb_ai_rag_chunk_stage(object_type, object_id, document_id);
