DROP INDEX IF EXISTS idx_ai_chunk_vector;

ALTER TABLE tb_ai_document_chunk
  ALTER COLUMN embedding TYPE vector;

ALTER TABLE tb_ai_document_chunk
  ADD COLUMN IF NOT EXISTS embedding_dimension INT;

UPDATE tb_ai_document_chunk
   SET embedding_dimension = vector_dims(embedding)
 WHERE embedding IS NOT NULL
   AND embedding_dimension IS NULL;

UPDATE tb_ai_document_chunk
   SET embedding_dimension = 768
 WHERE embedding_dimension IS NULL;

ALTER TABLE tb_ai_document_chunk
  ALTER COLUMN embedding_dimension SET NOT NULL;

ALTER TABLE tb_ai_document_chunk
  ALTER COLUMN embedding_dimension SET DEFAULT 768;
