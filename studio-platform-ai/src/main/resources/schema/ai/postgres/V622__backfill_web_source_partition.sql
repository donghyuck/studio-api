UPDATE tb_ai_document_chunk
SET partition_id = COALESCE(metadata ->> 'sourceRevisionId', '')
WHERE object_type = 'web_source'
  AND partition_id = ''
  AND COALESCE(metadata ->> 'sourceRevisionId', '') <> '';
