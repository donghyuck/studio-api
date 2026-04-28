ALTER TABLE tb_ai_rag_index_job
  ADD COLUMN source_name VARCHAR(300);

UPDATE tb_ai_rag_index_job
   SET source_name = document_id
 WHERE source_name IS NULL
   AND document_id IS NOT NULL;
