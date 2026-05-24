CREATE TABLE IF NOT EXISTS tb_skill_category_relation (
    relation_id VARCHAR(100) PRIMARY KEY,
    source_category_id VARCHAR(100) NOT NULL,
    target_category_id VARCHAR(100) NOT NULL,
    relation_type VARCHAR(50) NOT NULL,
    score DOUBLE NOT NULL DEFAULT 0,
    confidence DOUBLE NOT NULL DEFAULT 0,
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_skill_category_relation_source
    ON tb_skill_category_relation(source_category_id);

CREATE INDEX idx_skill_category_relation_target
    ON tb_skill_category_relation(target_category_id);
