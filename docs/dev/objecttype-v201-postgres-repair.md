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
발생할 수 있습니다. 이 경우 아래를 먼저 확인합니다.

1. `tb_application_object_type`에 `attachment`, `post-attachment`, `mail-attachment`,
   `workspace-attachment`, `wiki-attachment` row가 있는지 확인합니다.
2. `tb_application_object_type_policy`에 `object_type` `2001`, `2101`, `2102`, `2103`,
   `2104` row가 있는지 확인합니다.
3. 데이터가 의도한 seed와 일치하면 `repair`로 checksum을 현재 artifact에 맞춘 뒤 재기동합니다.

```bash
flyway repair
```

seed 데이터가 누락되었거나 일부만 적용된 경우에는 수동으로 row를 정리한 뒤 수정된 artifact로
`migrate`를 다시 실행합니다. 운영 데이터가 있는 환경에서는 `tb_application_object_type`과
`tb_application_object_type_policy`의 기존 사용자 정의 row를 삭제하지 않습니다.
