CREATE TABLE IF NOT EXISTS tb_ai_rag_chunk_stage (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  object_type VARCHAR(100) NOT NULL,
  object_id   VARCHAR(150) NOT NULL,
  document_id VARCHAR(200),
  document_key VARCHAR(200) GENERATED ALWAYS AS (COALESCE(document_id, '')) PERSISTENT,
  chunk_index INT NOT NULL,
  chunk_id    VARCHAR(200),
  text        LONGTEXT NOT NULL,
  metadata    LONGTEXT,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_ai_rag_chunk_stage_scope UNIQUE (object_type, object_id, document_key, chunk_index)
);

CREATE INDEX idx_ai_rag_chunk_stage_scope
    ON tb_ai_rag_chunk_stage(object_type, object_id, document_id);
