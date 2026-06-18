ALTER TABLE tb_ai_vector_projection_point
    ADD COLUMN document_chunk_id BIGINT NULL,
    ADD COLUMN target_type VARCHAR(100) NULL,
    ADD COLUMN source_id VARCHAR(200) NULL,
    ADD COLUMN label VARCHAR(500) NULL,
    ADD COLUMN metadata_preview_json LONGTEXT NULL;

UPDATE tb_ai_vector_projection_point p
  JOIN tb_ai_document_chunk c
    ON p.vector_item_id = COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.chunkId')), ''), CONCAT('row-', c.id))
   SET p.document_chunk_id = c.id,
       p.target_type = c.object_type,
       p.source_id = c.object_id,
       p.metadata_preview_json = JSON_OBJECT(
           'chunkId', JSON_EXTRACT(c.metadata, '$.chunkId'),
           'sourceName', JSON_EXTRACT(c.metadata, '$.sourceName'),
           'title', JSON_EXTRACT(c.metadata, '$.title'),
           'filename', JSON_EXTRACT(c.metadata, '$.filename'),
           'fileName', JSON_EXTRACT(c.metadata, '$.fileName'),
           'name', JSON_EXTRACT(c.metadata, '$.name'),
           'headingPath', JSON_EXTRACT(c.metadata, '$.headingPath'),
           'sourceRef', JSON_EXTRACT(c.metadata, '$.sourceRef'),
           'objectType', c.object_type,
           'objectId', c.object_id,
           'chunkIndex', c.chunk_index
       ),
       p.label = COALESCE(
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.sourceName')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.title')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.filename')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.fileName')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.name')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.headingPath')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(c.metadata, '$.sourceRef')), ''),
           c.object_id
       )
 WHERE p.document_chunk_id IS NULL;

CREATE INDEX idx_ai_vector_projection_point_target
    ON tb_ai_vector_projection_point(projection_id, target_type);

CREATE INDEX idx_ai_vector_projection_point_chunk
    ON tb_ai_vector_projection_point(document_chunk_id);
