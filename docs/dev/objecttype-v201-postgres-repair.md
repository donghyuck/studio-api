# ObjectType V201 PostgreSQL repair guide

`studio-platform-objecttype`의 PostgreSQL `V201__seed_well_known_attachment_objecttypes.sql`은
well-known attachment objectType seed를 추가합니다. 기존 SQL은 `policy_json` 값이 `NULL`로만
작성되어 PostgreSQL `VALUES` 타입 추론이 `text`로 고정될 수 있었고, `policy_json JSONB`
컬럼에 insert할 때 아래 오류로 기동이 중단될 수 있습니다.

```text
ERROR: column "policy_json" is of type jsonb but expression is of type text
```

수정된 `V201`은 fresh install이 같은 위치에서 실패하지 않도록 `NULL::jsonb`를 명시합니다.
이 migration은 실패 지점이 `V201` 안에 있으므로, 후속 `V202` migration만으로는 fresh
PostgreSQL 환경을 복구할 수 없습니다.

## Fresh install

아직 `V201`이 적용되지 않은 PostgreSQL 환경은 수정된 artifact로 다시 기동하면 됩니다.

## Failed migration history

PostgreSQL은 보통 transactional migration을 사용하므로 실패한 `V201` row가 Flyway history에
남지 않을 수 있습니다. 그래도 환경별 Flyway 설정이나 수동 조치로 failed row가 남아 있으면,
수정된 artifact 배포 후 `repair`를 먼저 실행하고 다시 migrate 합니다.

```bash
flyway repair
flyway migrate
```

애플리케이션 서버에서만 Flyway를 실행하는 환경은 같은 datasource 설정으로 `repair`를 한 번
수행한 뒤 서버를 재기동합니다. Gradle 또는 CI에 Flyway task를 따로 구성한 애플리케이션은
동일한 datasource 설정을 사용하는 해당 task로 `repair`/`migrate`를 실행합니다.

## Already applied V201

이미 기존 `V201`이 성공으로 기록된 PostgreSQL 환경에서 artifact만 교체하면 checksum mismatch가
발생할 수 있습니다. 이 경우 `repair` 전에 reserved objectType/code 무결성만 먼저 검증합니다.
DB 모드의 objectType 이름, 설명, 상태, 업로드 정책은 운영자가 관리 API로 수정할 수 있는 값이므로
checksum repair를 위해 기본 seed 값으로 덮어쓰지 않습니다.

아래 SQL은 reserved code와 numeric `object_type`이 서로 다르게 묶였거나 canonical type/policy row가
누락된 경우를 반환합니다. 결과가 0건이면 기존 운영 커스텀 정책을 보존한 채 `repair`를 수행합니다.

```sql
WITH reserved_type AS (
    SELECT *
    FROM (
        VALUES
            (2001, 'attachment'),
            (2101, 'post-attachment'),
            (2102, 'mail-attachment'),
            (2103, 'workspace-attachment'),
            (2104, 'wiki-attachment')
    ) AS v(object_type, code)
)
SELECT 'missing_type' AS issue, r.object_type, r.code, NULL::INT AS actual_object_type, NULL::TEXT AS actual_code
FROM reserved_type r
LEFT JOIN tb_application_object_type t ON t.object_type = r.object_type
WHERE t.object_type IS NULL
UNION ALL
SELECT 'missing_policy' AS issue, r.object_type, r.code, t.object_type AS actual_object_type, t.code AS actual_code
FROM reserved_type r
JOIN tb_application_object_type t ON t.object_type = r.object_type AND t.code = r.code
LEFT JOIN tb_application_object_type_policy p ON p.object_type = r.object_type
WHERE p.object_type IS NULL
UNION ALL
SELECT 'wrong_code_for_type' AS issue, r.object_type, r.code, t.object_type AS actual_object_type, t.code AS actual_code
FROM reserved_type r
JOIN tb_application_object_type t ON t.object_type = r.object_type
WHERE t.code <> r.code
UNION ALL
SELECT 'wrong_type_for_code' AS issue, r.object_type, r.code, t.object_type AS actual_object_type, t.code AS actual_code
FROM reserved_type r
JOIN tb_application_object_type t ON t.code = r.code
WHERE t.object_type <> r.object_type;
```

검증 SQL이 0건이면 `repair`로 checksum을 현재 artifact에 맞춘 뒤 재기동합니다.

```bash
flyway repair
```

검증 SQL이 `wrong_code_for_type` 또는 `wrong_type_for_code`를 반환하면 `repair`를 먼저 실행하지
않습니다. 해당 상태는 reserved code 또는 numeric ID가 이미 다른 의미로 사용되고 있다는 뜻입니다.
첨부 파일의 `object_type` 참조와 권한 판단에 영향을 줄 수 있으므로 운영 데이터 소유자와 충돌 row를
확인한 뒤 수동으로 재매핑해야 합니다. 이 문서의 backfill SQL은 충돌 row를 자동 변경하지 않습니다.

검증 SQL이 `missing_type` 또는 `missing_policy`만 반환하면 `V201`이 이미 성공으로 기록된 상태에서는
단순 `migrate`가 `V201`을 재실행하지 않습니다. 아래 SQL로 누락된 canonical type row와 누락된 policy
row만 보강한 뒤 다시 검증합니다. 기존 row의 이름, 설명, 상태, 업로드 정책은 운영 커스텀 값일 수
있으므로 덮어쓰지 않습니다.

```sql
INSERT INTO tb_application_object_type (
    object_type, code, name, domain, status, description,
    created_by, created_by_id, updated_by, updated_by_id
)
SELECT seed.object_type, seed.code, seed.name, seed.domain, seed.status, seed.description,
       'system', 0, 'system', 0
FROM (
    VALUES
        (2001, 'attachment', 'Attachment', 'attachment', 'active', 'Default attachment object type'),
        (2101, 'post-attachment', 'Post Attachment', 'post', 'active', 'Well-known attachment type for post files'),
        (2102, 'mail-attachment', 'Mail Attachment', 'mail', 'active', 'Well-known attachment type for mail message files'),
        (2103, 'workspace-attachment', 'Workspace Attachment', 'workspace', 'active', 'Well-known attachment type for workspace files'),
        (2104, 'wiki-attachment', 'Wiki Attachment', 'wiki', 'active', 'Well-known attachment type for wiki page files')
) AS seed(object_type, code, name, domain, status, description)
WHERE NOT EXISTS (
    SELECT 1
    FROM tb_application_object_type t
    WHERE t.object_type = seed.object_type
)
ON CONFLICT DO NOTHING;

INSERT INTO tb_application_object_type_policy (
    object_type, max_file_mb, allowed_ext, allowed_mime, policy_json,
    created_by, created_by_id, updated_by, updated_by_id
)
SELECT seed.object_type, seed.max_file_mb, seed.allowed_ext, seed.allowed_mime, seed.policy_json,
       'system', 0, 'system', 0
FROM (
    VALUES
        (2001, 50, NULL::text, NULL::text, NULL::jsonb),
        (2101, 50, NULL::text, NULL::text, NULL::jsonb),
        (2102, 50, NULL::text, NULL::text, NULL::jsonb),
        (2103, 50, NULL::text, NULL::text, NULL::jsonb),
        (2104, 50, NULL::text, NULL::text, NULL::jsonb)
) AS seed(object_type, max_file_mb, allowed_ext, allowed_mime, policy_json)
WHERE EXISTS (
    SELECT 1
    FROM tb_application_object_type t
    WHERE t.object_type = seed.object_type
)
ON CONFLICT DO NOTHING;
```

수동 backfill 후 검증 SQL이 0건이 되면 `flyway repair`를 수행하고 서버를 재기동합니다.
