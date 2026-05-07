# studio-platform-starter-user

사용자 도메인(유저/그룹/권한/회사) 기능을 빠르게 붙이기 위한 스타터이다. JPA 또는 JDBC 영속성에 맞춰
엔터티/리포지토리 스캔과 서비스 빈을 등록하고, 선택적으로 REST 엔드포인트를 노출한다. 비밀번호 정책은
`studio.user.password-policy.*`를 사용하고, `studio.features.user.password-policy.*`는 migration window 동안만 fallback으로 남는다.
기본 구현(직접 사용자 엔터티/리포지토리/서비스/컨트롤러)은 `studio-platform-user-default`에서 제공된다.

## 1) 의존성 추가
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-user"))
    // 기본 사용자 구현 사용 시
    implementation(project(":studio-platform-user-default"))
    // REST 엔드포인트를 사용할 때
    implementation("org.springframework.boot:spring-boot-starter-web")
    // JPA 사용 시
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // 비밀번호 인코더 등 보안 구성 연계 시
    implementation("org.springframework.boot:spring-boot-starter-security")
}
```

## 2) 기능 활성화
```yaml
studio:
  features:
    user:
      use-default: true  # 기본 구현 사용 여부
      enabled: true
```

## 3) 영속성 타입 선택
전역 설정 또는 사용자 기능 전용 설정을 사용한다.

```yaml
studio:
  persistence:
    type: jpa   # jpa | jdbc

  features:
    user:
      persistence: jpa  # (선택) user 기능만 별도 지정
```

## 4) 비밀번호 정책
비밀번호 정책은 feature wiring이 아니라 runtime detail 이므로 `studio.user.password-policy.*`에 둔다.

```yaml
studio:
  user:
    password-policy:
      min-length: 12
      max-length: 64
      require-upper: true
      require-lower: true
      require-digit: true
      require-special: true
      allowed-specials: "!@#$%^&*"
      allow-whitespace: false
```

## 5) 패키지 스캔 설정
기본값이 있으므로 보통 설정 없이도 동작한다. 커스텀 패키지를 쓰는 경우 아래를 조정한다.

```yaml
studio:
  features:
    user:
      entity-packages:
        - studio.one.base.user.domain.entity
      repository-packages:
        - studio.one.base.user.persistence.jpa
      jdbc-repository-packages:
        - studio.one.base.user.persistence.jdbc
      component-packages:
        - studio.one.base.user.service.impl
      exclude-entity-packages: []
      exclude-repository-packages: []
      exclude-jdbc-repository-packages: []
```

기본 구현을 제외하고 싶다면 `exclude-*` 설정으로 기본 패키지를 제외하고,
대체 구현 모듈의 패키지로 스캔 범위를 바꾼다.

기본 패키지 경로는 core와 동일한 네임스페이스이지만,
실제 구현은 `studio-platform-user-default` 모듈에 있다.

예시 (커스텀 사용자 구현 사용 시):
```yaml
studio:
  features:
    user:
      entity-packages:
        - com.example.user.domain.entity
      repository-packages:
        - com.example.user.persistence.jpa
      jdbc-repository-packages:
        - com.example.user.persistence.jdbc
      component-packages:
        - com.example.user.service.impl
      exclude-entity-packages:
        - studio.one.base.user.domain.entity
      exclude-repository-packages:
        - studio.one.base.user.persistence.jpa
      exclude-jdbc-repository-packages:
        - studio.one.base.user.persistence.jdbc
```

## 6) 자동 구성되는 주요 빈
- `ApplicationUserService`, `ApplicationGroupService`, `ApplicationRoleService`, `ApplicationCompanyService`
- `ApplicationCompanyMemberService`, `ApplicationCompanyPermissionService`
- `ApplicationCompanyJoinRequestService`
- `UserMutator` (기본 `ApplicationUserMutator`)
- `UserCacheEvictListener` (CacheManager가 있을 때)

서비스는 `JdbcTemplate`을 사용하므로 `studio-platform-starter`의 JDBC 자동구성이 켜져 있어야 한다.

## 7) REST 엔드포인트 (선택)
엔드포인트는 `studio.features.user.enabled=true`일 때 활성화되며, 개별 토글로 제어한다.

```yaml
studio:
  features:
    user:
      web:
        base-path: /api/mgmt
        self:
          enabled: true
          path: /api/self
        endpoints:
          user:
            enabled: true
          group:
            enabled: true
          role:
            enabled: true
          company:
            enabled: true
```

기본 엔드포인트 경로:
- `/api/mgmt/users`
- `/api/mgmt/groups`
- `/api/mgmt/roles`
- `/api/mgmt/companies`
- `/api/self`
- `/api/self/company-join-requests`

Company endpoint는 `features:company/*` 권한을 먼저 확인하고, 단일 Company 조회/수정,
member 조회/변경, permission 조회에는 Company 객체 권한을 추가로 적용한다.
이 객체 권한 검사는 `IdentityService`가 principal을 userId로 해석할 수 있어야 하며,
해석할 수 없으면 fail-closed로 거부한다. Company 목록 조회는 교차 tenant 메타데이터
노출을 막기 위해 `features:company/admin` 권한만 허용한다.

Company join request API는 멤버 키 기반 가입 요청 흐름을 제공한다.

| Method | Path | 권한 |
|---|---|---|
| `POST` | `/api/mgmt/companies/{companyId}/member-keys` | `features:company/write` + `company.member.manage` |
| `GET` | `/api/mgmt/companies/{companyId}/member-join-requests` | `features:company/write` + `company.member.manage` |
| `POST` | `/api/mgmt/companies/{companyId}/member-join-requests/{requestId}/approve` | `features:company/write` + `company.member.manage` |
| `POST` | `/api/mgmt/companies/{companyId}/member-join-requests/{requestId}/reject` | `features:company/write` + `company.member.manage` |
| `POST` | `/api/self/company-join-requests` | 인증 사용자 가입 요청 경로 |

멤버 키는 생성 응답에서만 평문으로 반환하고 DB에는 `SHA-256` hash만 저장한다.
Company member 관리자는 자신의 Company role보다 높은 role의 멤버 키를 발급할 수 없고, 플랫폼 관리자는 관리용 endpoint에서 이 제한을 우회할 수 있다.
Company member role 변경/삭제는 마지막 `OWNER`를 제거하지 못하도록 거부한다.
가입 요청은 인증 사용자 경로인 `/api/self/company-join-requests`에서 생성한다.
email만 받는 비로그인 가입 요청은 소유권 증명 없이 기존 계정에 안전하게 연결할 수 없으므로 이번 범위에서 노출하지 않는다.
만료되었거나 사용 횟수를 초과한 키는 가입 요청을 만들 수 없고, `maxUses`는 승인 시점에 차감된다.
`maxUses` 한도 계산은 승인 완료 건과 대기 중인 요청을 함께 반영해 과도한 대기 요청을 제한한다.
승인/거절된 요청은 중복 처리되지 않는다.
`/api/self/company-join-requests`와 관리 API는 인증/권한 검사를 유지한다.

기본 사용자 관리 API에는 `DELETE /api/mgmt/users/{id}`가 포함된다. 이 엔드포인트는
`features:user` admin 권한을 요구하며, 성공 시 `204 No Content`를 반환한다.

기본 컨트롤러는 `ApplicationUserMapper`/`ApplicationUserService` 빈이 있을 때만 등록된다.
커스텀 컨트롤러를 제공할 때는 `UserMgmtApi`/`UserPublicApi`/`UserAuthPublicApi`/`UserMeApi`
인터페이스를 구현하면 기본 컨트롤러가 자동으로 비활성화된다.

커스텀 사용자 구현을 사용하는 경우 기본 컨트롤러를 끄는 것을 권장한다.
```yaml
studio:
  features:
    user:
      web:
        endpoints:
          user:
            enabled: false
          group:
            enabled: false
          role:
            enabled: false
        self:
          enabled: false
```

## 8) 참고 사항
- JPA 사용 시 `EntityManagerFactory`가 필요하다.
- JDBC 모드에서는 `JdbcTemplate` 기반 리포지토리가 사용된다.
- 기능을 끄려면 `studio.features.user.enabled=false`로 비활성화한다.
- 사용자 스키마는 `studio-platform-user-default`에 포함된다:
  `studio-platform-user-default/src/main/resources/schema/user/{db}/V300__create_user_tables.sql`
  (`docs/flyway-versioning.md`의 user 범위 V300-V399 참고)
- Company member/permission 기반은 `V302__extend_company_and_create_company_members.sql`에서 추가된다.
  `TB_APPLICATION_COMPANY.STATUS`, `ARCHIVED_AT`, `ARCHIVED_BY`와
  `TB_APPLICATION_COMPANY_MEMBERS`를 생성한다.
- Company 멤버 키와 가입 요청 기반은 `V303__create_company_join_request_tables.sql`에서 추가된다.
  `TB_APPLICATION_COMPANY_MEMBER_KEY`는 평문 키가 아니라 hash만 저장하고,
  `TB_APPLICATION_COMPANY_JOIN_REQUEST`는 요청/승인/거절 actor와 일시를 보존한다.
- PostgreSQL에서는 그룹 멤버 summary 검색의 `username`/`name`/`email` 부분 검색을 위해
  `schema/user/postgres/V301__optimize_group_member_summary_search.sql`가 `pg_trgm` 확장과
  `lower(...)` GIN trigram index를 추가한다. 운영 DB의 Flyway 계정은 `CREATE EXTENSION IF NOT EXISTS pg_trgm`
  실행 권한을 가져야 한다.
- MySQL/MariaDB의 V301 migration은 PostgreSQL 전용 최적화와 버전 이력을 맞추기 위한 schema-neutral migration이다.
  선행 wildcard 검색(`LIKE '%keyword%'`)은 일반 B-tree index로 안정적으로 최적화되지 않으므로 별도 full-text/search 정책이 필요하면
  후속 DB별 설계로 분리한다.
