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
발생할 수 있습니다. 이 경우 `repair` 전에 seed 데이터가 현재 artifact의 의도와 일치하는지
먼저 검증합니다.

아래 SQL은 missing/drift row를 반환합니다. 결과가 0건일 때만 `repair`를 수행합니다.

```sql
WITH expected_type AS (
    SELECT *
    FROM (
        VALUES
            (2001, 'attachment', 'Attachment', 'attachment', 'active', 'Default attachment object type'),
            (2101, 'post-attachment', 'Post Attachment', 'post', 'active', 'Well-known attachment type for post files'),
            (2102, 'mail-attachment', 'Mail Attachment', 'mail', 'active', 'Well-known attachment type for mail message files'),
            (2103, 'workspace-attachment', 'Workspace Attachment', 'workspace', 'active', 'Well-known attachment type for workspace files'),
            (2104, 'wiki-attachment', 'Wiki Attachment', 'wiki', 'active', 'Well-known attachment type for wiki page files')
    ) AS v(object_type, code, name, domain, status, description)
),
expected_policy AS (
    SELECT *
    FROM (
        VALUES
            (2001, 50, NULL::text, NULL::text, NULL::jsonb),
            (2101, 50, NULL::text, NULL::text, NULL::jsonb),
            (2102, 50, NULL::text, NULL::text, NULL::jsonb),
            (2103, 50, NULL::text, NULL::text, NULL::jsonb),
            (2104, 50, NULL::text, NULL::text, NULL::jsonb)
    ) AS v(object_type, max_file_mb, allowed_ext, allowed_mime, policy_json)
)
SELECT 'type' AS table_name, e.object_type
FROM expected_type e
LEFT JOIN tb_application_object_type t ON t.object_type = e.object_type
WHERE t.object_type IS NULL
   OR t.code IS DISTINCT FROM e.code
   OR t.name IS DISTINCT FROM e.name
   OR t.domain IS DISTINCT FROM e.domain
   OR t.status IS DISTINCT FROM e.status
   OR t.description IS DISTINCT FROM e.description
UNION ALL
SELECT 'policy' AS table_name, e.object_type
FROM expected_policy e
LEFT JOIN tb_application_object_type_policy p ON p.object_type = e.object_type
WHERE p.object_type IS NULL
   OR p.max_file_mb IS DISTINCT FROM e.max_file_mb
   OR p.allowed_ext IS DISTINCT FROM e.allowed_ext
   OR p.allowed_mime IS DISTINCT FROM e.allowed_mime
   OR p.policy_json IS DISTINCT FROM e.policy_json;
```

검증 SQL이 0건이면 `repair`로 checksum을 현재 artifact에 맞춘 뒤 재기동합니다.

```bash
flyway repair
```

검증 SQL이 row를 반환하면 `repair`를 먼저 실행하지 않습니다. `V201`이 이미 성공으로 기록된
상태에서는 단순 `migrate`가 `V201`을 재실행하지 않으므로, 누락/불일치 seed는 아래처럼 수동
backfill한 뒤 다시 검증합니다. 운영 데이터가 있는 환경에서는 다른 `object_type` row를 삭제하지
않습니다.

```sql
INSERT INTO tb_application_object_type (
    object_type, code, name, domain, status, description,
    created_by, created_by_id, updated_by, updated_by_id
) VALUES
    (2001, 'attachment', 'Attachment', 'attachment', 'active', 'Default attachment object type',
     'system', 0, 'system', 0),
    (2101, 'post-attachment', 'Post Attachment', 'post', 'active', 'Well-known attachment type for post files',
     'system', 0, 'system', 0),
    (2102, 'mail-attachment', 'Mail Attachment', 'mail', 'active', 'Well-known attachment type for mail message files',
     'system', 0, 'system', 0),
    (2103, 'workspace-attachment', 'Workspace Attachment', 'workspace', 'active', 'Well-known attachment type for workspace files',
     'system', 0, 'system', 0),
    (2104, 'wiki-attachment', 'Wiki Attachment', 'wiki', 'active', 'Well-known attachment type for wiki page files',
     'system', 0, 'system', 0)
ON CONFLICT (object_type) DO UPDATE
SET code = EXCLUDED.code,
    name = EXCLUDED.name,
    domain = EXCLUDED.domain,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_by = 'system',
    updated_by_id = 0,
    updated_at = NOW();

INSERT INTO tb_application_object_type_policy (
    object_type, max_file_mb, allowed_ext, allowed_mime, policy_json,
    created_by, created_by_id, updated_by, updated_by_id
) VALUES
    (2001, 50, NULL, NULL, NULL::jsonb, 'system', 0, 'system', 0),
    (2101, 50, NULL, NULL, NULL::jsonb, 'system', 0, 'system', 0),
    (2102, 50, NULL, NULL, NULL::jsonb, 'system', 0, 'system', 0),
    (2103, 50, NULL, NULL, NULL::jsonb, 'system', 0, 'system', 0),
    (2104, 50, NULL, NULL, NULL::jsonb, 'system', 0, 'system', 0)
ON CONFLICT (object_type) DO UPDATE
SET max_file_mb = EXCLUDED.max_file_mb,
    allowed_ext = EXCLUDED.allowed_ext,
    allowed_mime = EXCLUDED.allowed_mime,
    policy_json = EXCLUDED.policy_json,
    updated_by = 'system',
    updated_by_id = 0,
    updated_at = NOW();
```

수동 backfill 후 검증 SQL이 0건이 되면 `flyway repair`를 수행하고 서버를 재기동합니다.
