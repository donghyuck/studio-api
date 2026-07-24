-- Repair only rows whose canonical profile already proves the model that
-- generated the stored vector. This migration never relabels embedding values
-- from one model as another.
UPDATE tb_ai_document_chunk
SET metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object(
    'embeddingProvider', 'google-ai',
    'embeddingModel', 'gemini-embedding-001',
    'embeddingModelId', 'google-ai/gemini-embedding-001@768',
    'embeddingDeploymentId', 'humanities-text-v1',
    'embeddingCatalogId', 'google/gemini-embedding-001',
    'embeddingSpaceId', 'google-ai/gemini-embedding-001@768',
    'embeddingDimension', 768
)
WHERE embedding_dimension = 768
  AND metadata ->> 'embeddingProfileId' = 'google-ai/gemini-embedding-001@768'
  AND (
      NULLIF(metadata ->> 'embeddingProvider', '') IS NULL
      OR LOWER(COALESCE(metadata ->> 'embeddingModel', 'unknown')) = 'unknown'
      OR NULLIF(metadata ->> 'embeddingDeploymentId', '') IS NULL
  );

UPDATE tb_ai_document_chunk
SET metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object(
    'embeddingProvider', 'google-ai',
    'embeddingModel', 'gemini-embedding-2',
    'embeddingModelId', 'google-ai/gemini-embedding-2@768',
    'embeddingDeploymentId', 'document-multimodal-v1',
    'embeddingCatalogId', 'google/gemini-embedding-2',
    'embeddingSpaceId', 'google-ai/gemini-embedding-2@768',
    'embeddingDimension', 768
)
WHERE embedding_dimension = 768
  AND metadata ->> 'embeddingProfileId' = 'google-ai/gemini-embedding-2@768'
  AND (
      NULLIF(metadata ->> 'embeddingProvider', '') IS NULL
      OR LOWER(COALESCE(metadata ->> 'embeddingModel', 'unknown')) = 'unknown'
      OR NULLIF(metadata ->> 'embeddingDeploymentId', '') IS NULL
  );
