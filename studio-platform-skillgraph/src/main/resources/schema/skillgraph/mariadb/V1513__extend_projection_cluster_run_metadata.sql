ALTER TABLE tb_skill_dictionary
    ADD COLUMN IF NOT EXISTS skill_type VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE tb_skill_projection
    ADD COLUMN IF NOT EXISTS skill_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS job_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS projection_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS projection_dimension INT,
    ADD COLUMN IF NOT EXISTS metadata TEXT;

ALTER TABLE tb_skill_cluster
    ADD COLUMN IF NOT EXISTS skill_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS job_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS cluster_label INT,
    ADD COLUMN IF NOT EXISTS representative_skill_ids TEXT,
    ADD COLUMN IF NOT EXISTS centroid_projection_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS confidence DECIMAL(5, 4),
    ADD COLUMN IF NOT EXISTS metadata TEXT;

CREATE TABLE IF NOT EXISTS tb_skill_cluster_member (
    cluster_id VARCHAR(100) NOT NULL,
    skill_id VARCHAR(100) NOT NULL,
    embedding_id VARCHAR(120),
    projection_id VARCHAR(100) NOT NULL,
    membership_score DOUBLE NOT NULL DEFAULT 1,
    distance_to_centroid DOUBLE NOT NULL DEFAULT 0,
    is_representative BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (projection_id, cluster_id, skill_id)
);

CREATE INDEX idx_skill_dictionary_skill_type
    ON tb_skill_dictionary(skill_type, status, name);

CREATE INDEX idx_skill_projection_run_lookup
    ON tb_skill_projection(skill_type, embedding_provider, embedding_model, embedding_dimension);

CREATE INDEX idx_skill_cluster_job
    ON tb_skill_cluster(job_id, skill_type);

CREATE INDEX idx_skill_cluster_member_cluster
    ON tb_skill_cluster_member(projection_id, cluster_id, is_representative);
