CREATE TABLE IF NOT EXISTS tb_ai_chunk_set (
  chunk_set_id       VARCHAR(200) PRIMARY KEY,
  object_type        VARCHAR(100) NOT NULL,
  object_id          VARCHAR(150) NOT NULL,
  document_id        VARCHAR(200) NOT NULL,
  source_revision_id VARCHAR(200) NOT NULL,
  source_content_hash VARCHAR(128) NOT NULL,
  strategy           VARCHAR(100) NOT NULL,
  strategy_hash      VARCHAR(128) NOT NULL,
  chunk_unit         VARCHAR(30),
  max_size           INT,
  overlap_size       INT,
  status             VARCHAR(30) NOT NULL,
  quality_status     VARCHAR(30) NOT NULL,
  quality_issues     TEXT,
  metadata           TEXT,
  created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_ai_chunk_set_revision_strategy
    UNIQUE (object_type, object_id, source_revision_id, strategy_hash)
);

CREATE INDEX idx_ai_chunk_set_scope
  ON tb_ai_chunk_set(object_type, object_id, document_id, source_revision_id);

CREATE INDEX idx_ai_chunk_set_status
  ON tb_ai_chunk_set(object_type, object_id, status);

CREATE TABLE IF NOT EXISTS tb_ai_chunk_item (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  chunk_set_id VARCHAR(200) NOT NULL,
  chunk_index  INT NOT NULL,
  chunk_id     VARCHAR(200) NOT NULL,
  text         LONGTEXT NOT NULL,
  content_hash VARCHAR(128) NOT NULL,
  metadata     LONGTEXT,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ai_chunk_item_set
    FOREIGN KEY (chunk_set_id) REFERENCES tb_ai_chunk_set(chunk_set_id) ON DELETE CASCADE,
  CONSTRAINT uq_ai_chunk_item_set_index UNIQUE (chunk_set_id, chunk_index)
);

CREATE INDEX idx_ai_chunk_item_set
  ON tb_ai_chunk_item(chunk_set_id, chunk_index);
