CREATE INDEX IF NOT EXISTS idx_ai_chunk_embedding_1024_hnsw
    ON tb_ai_document_chunk
    USING hnsw ((embedding::vector(1024)) vector_l2_ops)
    WHERE embedding IS NOT NULL
      AND embedding_dimension = 1024;

CREATE INDEX IF NOT EXISTS idx_ai_chunk_embedding_768_hnsw
    ON tb_ai_document_chunk
    USING hnsw ((embedding::vector(768)) vector_l2_ops)
    WHERE embedding IS NOT NULL
      AND embedding_dimension = 768;
