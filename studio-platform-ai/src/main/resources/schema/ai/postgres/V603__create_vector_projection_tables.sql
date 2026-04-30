CREATE TABLE IF NOT EXISTS tb_ai_vector_projection (
    projection_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    algorithm VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    target_types VARCHAR(500),
    filter_json JSONB DEFAULT '{}'::jsonb,
    item_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_vector_projection_status
    ON tb_ai_vector_projection(status, created_at DESC);

CREATE TABLE IF NOT EXISTS tb_ai_vector_projection_point (
    projection_id VARCHAR(100) NOT NULL,
    vector_item_id VARCHAR(100) NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    cluster_id VARCHAR(100),
    display_order INT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (projection_id, vector_item_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_vector_projection_point_order
    ON tb_ai_vector_projection_point(projection_id, display_order);

CREATE INDEX IF NOT EXISTS idx_ai_vector_projection_point_cluster
    ON tb_ai_vector_projection_point(projection_id, cluster_id);
