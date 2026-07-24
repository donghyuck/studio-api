ALTER TABLE tb_skill_embedding
    ADD COLUMN model_deployment_id VARCHAR(200),
    ADD COLUMN catalog_id VARCHAR(300),
    ADD COLUMN embedding_space_id VARCHAR(80);
ALTER TABLE tb_skill_recommendation_job ADD COLUMN embedding_space_id VARCHAR(80);
ALTER TABLE tb_skillgraph_batch_job ADD COLUMN embedding_space_id VARCHAR(80);
ALTER TABLE tb_skill_projection ADD COLUMN embedding_space_id VARCHAR(80);
ALTER TABLE tb_skill_rag_extraction_job ADD COLUMN embedding_space_id VARCHAR(80);
CREATE INDEX idx_skill_embedding_space_lookup
    ON tb_skill_embedding(source_type, embedding_space_id, source_id);
