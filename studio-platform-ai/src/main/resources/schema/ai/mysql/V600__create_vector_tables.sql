CREATE TABLE tb_ai_document_chunk (
  id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  object_type   VARCHAR(50) NOT NULL,  -- BOARD_POST, STT, COURSE, PDF 등
  object_id     VARCHAR(100) NOT NULL, -- 원본 PK
  chunk_index   INT NOT NULL,          -- 문서 내 chunk 순서
  text          TEXT NOT NULL,         -- chunk 내용
  embedding     JSON,                  -- 비 PostgreSQL dialect의 직렬화된 임베딩
  metadata      JSON DEFAULT (JSON_OBJECT()),
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 검색 최적화를 위한 인덱스
CREATE INDEX idx_ai_chunk_object ON tb_ai_document_chunk(object_type, object_id);

ALTER TABLE tb_ai_document_chunk
ADD CONSTRAINT uq_ai_chunk UNIQUE (object_type, object_id, chunk_index);
