CREATE TABLE tb_ai_document_chunk (
  id            BIGSERIAL PRIMARY KEY,
  object_type   VARCHAR(50) NOT NULL,  -- BOARD_POST, STT, COURSE, PDF 등
  object_id     VARCHAR(100) NOT NULL, -- 원본 PK
  chunk_index   INT NOT NULL,          -- 문서 내 chunk 순서
  text          TEXT NOT NULL,         -- chunk 내용
  embedding     VECTOR(768),           -- 임베딩 벡터(Gemini-004 기준)
  metadata      JSONB DEFAULT '{}'::jsonb,
  created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- 검색 최적화를 위한 인덱스
CREATE INDEX idx_ai_chunk_object ON tb_ai_document_chunk(object_type, object_id);

CREATE INDEX idx_ai_chunk_vector
ON tb_ai_document_chunk
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

ALTER TABLE tb_ai_document_chunk
ADD CONSTRAINT uq_ai_chunk UNIQUE (object_type, object_id, chunk_index);
