ALTER TABLE tb_ai_document_chunk
    ADD COLUMN partition_id VARCHAR(80) NOT NULL DEFAULT '' AFTER object_id,
    DROP INDEX uq_ai_chunk,
    ADD CONSTRAINT uq_ai_chunk
        UNIQUE (object_type, object_id, partition_id, chunk_index),
    ADD INDEX idx_ai_chunk_object_partition (object_type, object_id, partition_id);
