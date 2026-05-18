CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS tb_skill_source_chunk (
    source_chunk_id VARCHAR(100) PRIMARY KEY,
    source_type VARCHAR(100),
    source_id VARCHAR(200),
    chunk_id VARCHAR(200),
    text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_skill_source_chunk_source
    ON tb_skill_source_chunk(source_type, source_id);

CREATE TABLE IF NOT EXISTS tb_skill_dictionary (
    skill_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(300) NOT NULL,
    normalized_name VARCHAR(300) NOT NULL,
    category_id VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    embedding VECTOR,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_skill_dictionary_normalized_name UNIQUE (normalized_name)
);

CREATE INDEX IF NOT EXISTS idx_skill_dictionary_status
    ON tb_skill_dictionary(status, name);

CREATE TABLE IF NOT EXISTS tb_skill_candidate (
    candidate_id VARCHAR(100) PRIMARY KEY,
    source_chunk_id VARCHAR(100),
    source_type VARCHAR(100),
    source_id VARCHAR(200),
    term VARCHAR(300) NOT NULL,
    normalized_term VARCHAR(300) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0,
    occurrence_count INT NOT NULL DEFAULT 1,
    matched_skill_id VARCHAR(100),
    reviewer_note TEXT,
    embedding VECTOR,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_skill_candidate_status
    ON tb_skill_candidate(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_skill_candidate_normalized
    ON tb_skill_candidate(normalized_term);

CREATE TABLE IF NOT EXISTS tb_skill_alias (
    alias_id VARCHAR(100) PRIMARY KEY,
    skill_id VARCHAR(100) NOT NULL,
    alias VARCHAR(300) NOT NULL,
    normalized_alias VARCHAR(300) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_skill_alias_normalized UNIQUE (normalized_alias)
);

CREATE TABLE IF NOT EXISTS tb_skill_category (
    category_id VARCHAR(100) PRIMARY KEY,
    parent_category_id VARCHAR(100),
    name VARCHAR(200) NOT NULL,
    display_order INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tb_skill_cluster (
    cluster_id VARCHAR(100) PRIMARY KEY,
    label VARCHAR(200),
    algorithm VARCHAR(50) NOT NULL,
    item_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tb_skill_projection (
    projection_id VARCHAR(100) NOT NULL,
    skill_id VARCHAR(100) NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    cluster_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (projection_id, skill_id)
);

CREATE TABLE IF NOT EXISTS tb_skill_relation (
    relation_id VARCHAR(100) PRIMARY KEY,
    source_skill_id VARCHAR(100) NOT NULL,
    target_skill_id VARCHAR(100) NOT NULL,
    relation_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS tb_skill_ncs_mapping (
    mapping_id VARCHAR(100) PRIMARY KEY,
    ncs_unit_id VARCHAR(100) NOT NULL,
    skill_id VARCHAR(100) NOT NULL,
    weight DOUBLE PRECISION NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_skill_ncs_mapping_unit
    ON tb_skill_ncs_mapping(ncs_unit_id);

CREATE TABLE IF NOT EXISTS tb_skill_course_mapping (
    mapping_id VARCHAR(100) PRIMARY KEY,
    course_id VARCHAR(100) NOT NULL,
    skill_id VARCHAR(100) NOT NULL,
    weight DOUBLE PRECISION NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_skill_course_mapping_course
    ON tb_skill_course_mapping(course_id);

CREATE INDEX IF NOT EXISTS idx_skill_course_mapping_skill
    ON tb_skill_course_mapping(skill_id);
