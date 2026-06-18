CREATE INDEX IF NOT EXISTS idx_ai_chunk_vector_item_id
    ON tb_ai_document_chunk (
        (COALESCE(NULLIF(metadata ->> 'chunkId', ''), 'row-' || id))
    );
