-- The production vector store uses PostgreSQL JSONB. Keep relational
-- evaluation history consistent for MySQL deployments.
UPDATE tb_ai_rag_retrieval_evaluation
SET embedding_profile_id = 'google-ai/gemini-embedding-001@768',
    embedding_provider = 'google-ai'
WHERE embedding_model = 'gemini-embedding-001'
  AND embedding_profile_id = 'gemini-768';

UPDATE tb_ai_rag_retrieval_evaluation
SET embedding_profile_id = 'google-ai/gemini-embedding-2@768',
    embedding_provider = 'google-ai'
WHERE embedding_model = 'gemini-embedding-2'
  AND embedding_profile_id = 'gemini-embedding-2-768';
