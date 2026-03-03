-- MySQL Version
-- =================================================
-- PACKAGE: PLATFORM OBJECTTYPE
-- CREATE : 2026.01.22
-- UPDATE :
-- =================================================

-- object type registry
CREATE TABLE tb_application_object_type (
    object_type     INT           NOT NULL PRIMARY KEY COMMENT '숫자 타입 (고정 ID)',
    code            VARCHAR(80)   NOT NULL UNIQUE COMMENT '코드 (예: post_draft, post, document)',
    name            VARCHAR(200)  NOT NULL COMMENT '표시명',
    domain          VARCHAR(80)   NOT NULL COMMENT '도메인 (예: forum, document, template)',
    status          VARCHAR(20)   NOT NULL DEFAULT 'active' COMMENT '상태 (active/deprecated/disabled)',
    description     VARCHAR(1000) NULL COMMENT '설명',

    created_by      VARCHAR(120)  NOT NULL COMMENT '생성자',
    created_by_id   BIGINT        NOT NULL COMMENT '생성자 ID',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_by      VARCHAR(120)  NOT NULL COMMENT '수정자',
    updated_by_id   BIGINT        NOT NULL COMMENT '수정자 ID',
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    CONSTRAINT tb_app_obj_type_status_chk
        CHECK (status IN ('active', 'deprecated', 'disabled'))
) COMMENT='애플리케이션 오브젝트 타입 레지스트리';

CREATE INDEX tb_app_obj_type_idx1
    ON tb_application_object_type (domain, status);

CREATE TABLE tb_application_object_type_policy (
    object_type     INT          NOT NULL PRIMARY KEY COMMENT '오브젝트 타입 (1:1)',
    max_file_mb     INT          NULL COMMENT '최대 파일 크기(MB)',
    allowed_ext     TEXT         NULL COMMENT '허용 확장자 목록 (comma-separated, lowercase 권장)',
    allowed_mime    TEXT         NULL COMMENT '허용 MIME 목록 (comma-separated, lowercase 권장)',
    policy_json     JSON         NULL COMMENT '확장 정책(JSON)',

    created_by      VARCHAR(120) NOT NULL COMMENT '생성자',
    created_by_id   BIGINT       NOT NULL COMMENT '생성자 ID',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_by      VARCHAR(120) NOT NULL COMMENT '수정자',
    updated_by_id   BIGINT       NOT NULL COMMENT '수정자 ID',
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    CONSTRAINT tb_app_obj_type_policy_fk1
        FOREIGN KEY (object_type)
        REFERENCES tb_application_object_type (object_type)
        ON DELETE CASCADE
) COMMENT='오브젝트 타입 정책(1:1)';
