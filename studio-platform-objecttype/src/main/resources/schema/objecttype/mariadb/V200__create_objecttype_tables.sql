-- MariaDB Version
-- =================================================
-- PACKAGE: PLATFORM OBJECTTYPE
-- CREATE : 2026.01.22
-- UPDATE :
-- =================================================

-- object type registry
CREATE TABLE tb_application_object_type (
    object_type     INT           NOT NULL PRIMARY KEY,
    code            VARCHAR(80)   NOT NULL UNIQUE,
    name            VARCHAR(200)  NOT NULL,
    domain          VARCHAR(80)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'active',
    description     VARCHAR(1000) NULL,

    created_by      VARCHAR(120)  NOT NULL,
    created_by_id   BIGINT        NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(120)  NOT NULL,
    updated_by_id   BIGINT        NOT NULL,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT tb_app_obj_type_status_chk
        CHECK (status IN ('active', 'deprecated', 'disabled'))
);

CREATE INDEX tb_app_obj_type_idx1
    ON tb_application_object_type (domain, status);

CREATE TABLE tb_application_object_type_policy (
    object_type     INT          NOT NULL PRIMARY KEY,
    max_file_mb     INT          NULL,
    allowed_ext     TEXT         NULL,
    allowed_mime    TEXT         NULL,
    policy_json     JSON        NULL,

    created_by      VARCHAR(120) NOT NULL,
    created_by_id   BIGINT       NOT NULL,
    created_at      TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(120) NOT NULL,
    updated_by_id   BIGINT       NOT NULL,
    updated_at      TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT tb_app_obj_type_policy_fk1
        FOREIGN KEY (object_type)
        REFERENCES tb_application_object_type (object_type)
        ON DELETE CASCADE
);
