ALTER TABLE tb_ai_vector_projection_point
    ADD COLUMN IF NOT EXISTS document_chunk_id BIGINT,
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS label VARCHAR(500),
    ADD COLUMN IF NOT EXISTS metadata_preview_json JSONB DEFAULT '{}'::jsonb;

UPDATE tb_ai_vector_projection_point p
   SET document_chunk_id = c.id,
       target_type = c.object_type,
       source_id = c.object_id,
       metadata_preview_json = jsonb_strip_nulls(jsonb_build_object(
           'chunkId', c.metadata -> 'chunkId',
           'sourceName', c.metadata -> 'sourceName',
           'title', c.metadata -> 'title',
           'filename', c.metadata -> 'filename',
           'fileName', c.metadata -> 'fileName',
           'name', c.metadata -> 'name',
           'headingPath', c.metadata -> 'headingPath',
           'sourceRef', c.metadata -> 'sourceRef',
           'objectType', c.object_type,
           'objectId', c.object_id,
           'chunkIndex', c.chunk_index
       )),
       label = COALESCE(
           NULLIF(c.metadata ->> 'sourceName', ''),
           NULLIF(c.metadata ->> 'title', ''),
           NULLIF(c.metadata ->> 'filename', ''),
           NULLIF(c.metadata ->> 'fileName', ''),
           NULLIF(c.metadata ->> 'name', ''),
           NULLIF(c.metadata ->> 'headingPath', ''),
           NULLIF(c.metadata ->> 'sourceRef', ''),
           c.object_id
       )
  FROM tb_ai_document_chunk c
 WHERE p.document_chunk_id IS NULL
   AND p.vector_item_id = COALESCE(NULLIF(c.metadata ->> 'chunkId',''), 'row-' || c.id);

CREATE INDEX IF NOT EXISTS idx_ai_vector_projection_point_target
    ON tb_ai_vector_projection_point(projection_id, target_type);

CREATE INDEX IF NOT EXISTS idx_ai_vector_projection_point_chunk
    ON tb_ai_vector_projection_point(document_chunk_id);
