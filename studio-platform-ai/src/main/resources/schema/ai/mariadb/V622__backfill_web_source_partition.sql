UPDATE tb_ai_document_chunk
SET partition_id = COALESCE(JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.sourceRevisionId')), '')
WHERE object_type = 'web_source'
  AND partition_id = ''
  AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.sourceRevisionId')), '') <> '';
