-- PostgreSQL Version
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
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(120)  NOT NULL,
    updated_by_id   BIGINT        NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT tb_app_obj_type_status_chk
        CHECK (status IN ('active', 'deprecated', 'disabled'))
);

CREATE INDEX tb_app_obj_type_idx1
    ON tb_application_object_type (domain, status);

COMMENT ON TABLE tb_application_object_type IS '애플리케이션 오브젝트 타입 레지스트리';
COMMENT ON COLUMN tb_application_object_type.object_type IS '숫자 타입 (고정 ID)';
COMMENT ON COLUMN tb_application_object_type.code IS '코드 (예: post_draft, post, document)';
COMMENT ON COLUMN tb_application_object_type.name IS '표시명';
COMMENT ON COLUMN tb_application_object_type.domain IS '도메인 (예: forum, document, template)';
COMMENT ON COLUMN tb_application_object_type.status IS '상태 (active/deprecated/disabled)';
COMMENT ON COLUMN tb_application_object_type.description IS '설명';
COMMENT ON COLUMN tb_application_object_type.created_by IS '생성자';
COMMENT ON COLUMN tb_application_object_type.created_by_id IS '생성자 ID';
COMMENT ON COLUMN tb_application_object_type.created_at IS '생성 시각';
COMMENT ON COLUMN tb_application_object_type.updated_by IS '수정자';
COMMENT ON COLUMN tb_application_object_type.updated_by_id IS '수정자 ID';
COMMENT ON COLUMN tb_application_object_type.updated_at IS '수정 시각';

CREATE TABLE tb_application_object_type_policy (
    object_type     INT          NOT NULL PRIMARY KEY,
    max_file_mb     INT          NULL,
    allowed_ext     TEXT         NULL,
    allowed_mime    TEXT         NULL,
    policy_json     JSONB        NULL,

    created_by      VARCHAR(120) NOT NULL,
    created_by_id   BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(120) NOT NULL,
    updated_by_id   BIGINT       NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT tb_app_obj_type_policy_fk1
        FOREIGN KEY (object_type)
        REFERENCES tb_application_object_type (object_type)
        ON DELETE CASCADE
);

COMMENT ON TABLE tb_application_object_type_policy IS '오브젝트 타입 정책(1:1)';
COMMENT ON COLUMN tb_application_object_type_policy.object_type IS '오브젝트 타입 (1:1)';
COMMENT ON COLUMN tb_application_object_type_policy.max_file_mb IS '최대 파일 크기(MB)';
COMMENT ON COLUMN tb_application_object_type_policy.allowed_ext IS '허용 확장자 목록 (comma-separated, lowercase 권장)';
COMMENT ON COLUMN tb_application_object_type_policy.allowed_mime IS '허용 MIME 목록 (comma-separated, lowercase 권장)';
COMMENT ON COLUMN tb_application_object_type_policy.policy_json IS '확장 정책(JSON)';
COMMENT ON COLUMN tb_application_object_type_policy.created_by IS '생성자';
COMMENT ON COLUMN tb_application_object_type_policy.created_by_id IS '생성자 ID';
COMMENT ON COLUMN tb_application_object_type_policy.created_at IS '생성 시각';
COMMENT ON COLUMN tb_application_object_type_policy.updated_by IS '수정자';
COMMENT ON COLUMN tb_application_object_type_policy.updated_by_id IS '수정자 ID';
COMMENT ON COLUMN tb_application_object_type_policy.updated_at IS '수정 시각';
