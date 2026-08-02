ALTER TABLE tb_ai_document_chunk
    ADD COLUMN IF NOT EXISTS partition_id VARCHAR(80) NOT NULL DEFAULT '';

ALTER TABLE tb_ai_document_chunk
    DROP CONSTRAINT IF EXISTS uq_ai_chunk;

ALTER TABLE tb_ai_document_chunk
    ADD CONSTRAINT uq_ai_chunk
        UNIQUE (object_type, object_id, partition_id, chunk_index);

CREATE INDEX IF NOT EXISTS idx_ai_chunk_object_partition
    ON tb_ai_document_chunk(object_type, object_id, partition_id);
