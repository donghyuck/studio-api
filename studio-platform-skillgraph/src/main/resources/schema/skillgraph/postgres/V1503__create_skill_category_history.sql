CREATE TABLE IF NOT EXISTS tb_skill_category_history (
    history_id VARCHAR(100) PRIMARY KEY,
    category_id VARCHAR(100),
    skill_id VARCHAR(100),
    action_type VARCHAR(50) NOT NULL,
    previous_category_id VARCHAR(100),
    new_category_id VARCHAR(100),
    detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_skill_category_history_category
    ON tb_skill_category_history(category_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_skill_category_history_skill
    ON tb_skill_category_history(skill_id, created_at DESC);
