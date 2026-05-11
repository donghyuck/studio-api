# studio-platform-starter-objecttype

ObjectType 레지스트리와 정책을 자동 구성하는 스타터이다.
YAML 파일 또는 데이터베이스(JPA/MyBatis/JDBC) 모드로 ObjectType 정보를 로딩·캐싱하며,
ObjectType 조회 REST 엔드포인트와 관리 REST 엔드포인트를 선택적으로 노출한다.
핵심 구현은 `studio-platform-objecttype` 모듈에 있으며, 이 스타터는 그 위에
자동 구성(autoconfigure) 레이어를 더한다.

## 1) 의존성 추가
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-objecttype"))
    // 기반 스타터 (아직 추가하지 않은 경우)
    implementation(project(":starter:studio-platform-starter"))
    // YAML 모드: SnakeYAML
    implementation("org.yaml:snakeyaml")
    // DB 모드 JPA 사용 시
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // DB 모드 JDBC 사용 시
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // DB 모드 MyBatis 사용 시
    implementation(project(":starter:studio-platform-starter-mybatis"))
    // REST API 노출 시
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

## 2) 기능 활성화
`studio.features.objecttype.enabled=true` 설정 시 ObjectType 자동 구성이 활성화된다.
REST 엔드포인트는 별도 플래그로 제어한다.

```yaml
studio:
  features:
    objecttype:
      enabled: true
```

## 3) 설정

### YAML 모드 vs DB 모드 선택
ObjectType 정보를 YAML 파일에서 읽거나 데이터베이스에서 읽도록 전환할 수 있다.
기본값은 YAML 모드이다.

```yaml
studio:
  objecttype:
    mode: yaml    # yaml (기본값) | db
```

### YAML 모드 설정
```yaml
studio:
  objecttype:
    mode: yaml
    yaml:
      resource: classpath:objecttype.yml   # YAML 파일 경로 (기본값)
    registry:
      cache:
        enabled: true
        ttl-seconds: 300   # 캐시 TTL (초)
        max-size: 1000     # 최대 캐시 항목 수
    policy:
      cache:
        enabled: true
        ttl-seconds: 300
        max-size: 1000
```

YAML 파일 예시 (`classpath:objecttype.yml`):
```yaml
objecttypes:
  - type: 1001
    key: document
    name: 문서
    description: 문서 도메인 오브젝트
    policy:
      key: document-policy
      maxFileMb: 10
      allowedExt: "pdf,docx"
      allowedMime: "application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  - type: 1002
    key: image
    name: 이미지
```

### DB 모드 설정
```yaml
studio:
  objecttype:
    mode: db
  persistence:
    type: jpa   # jpa | mybatis | jdbc
  features:
    objecttype:
      enabled: true
```

DB 모드에서는 `studio.persistence.type` 전역 설정을 따른다.
기능 전용 영속성 타입이 필요할 경우 아래와 같이 지정한다.

```yaml
studio:
  features:
    objecttype:
      persistence: mybatis   # 이 기능만 MyBatis 사용
```

`mybatis`를 사용할 때는 `starter:studio-platform-starter-mybatis`가 함께 필요하다.
ObjectType MyBatis mapper는 PostgreSQL, H2, MySQL, MariaDB를 지원하며, 그 외 `databaseId`
에서는 기동 시 fail-fast 된다.
`jdbc`는 기존 직접 JDBC 경로를 유지하는 호환 옵션이며, 외부 sqlset mapper 주입은 사용하지 않는다.

### REST 엔드포인트 설정
```yaml
studio:
  features:
    objecttype:
      web:
        enabled: true
        base-path: /api/object-types
        mgmt-base-path: /api/mgmt/object-types
```

## 4) 자동 구성되는 주요 빈

| 빈 이름 / 타입 | 모드 | 설명 |
|---|---|---|
| `YamlObjectTypeLoader` | YAML | YAML 파일 파싱 및 로딩 |
| `ObjectTypeRegistry` (`YamlObjectTypeRegistry`) | YAML | 타입·키 기반 레지스트리 |
| `ObjectPolicyResolver` (`YamlObjectPolicyResolver`) | YAML | YAML 기반 정책 리졸버 |
| `ObjectRebindService` (`YamlObjectRebindService`) | YAML | 리바인드 서비스 (YAML 모드에서는 no-op) |
| `ObjectTypeRegistry` (`JpaObjectTypeRegistry`) | DB/JPA | JPA 기반 레지스트리 |
| `ObjectPolicyResolver` (`JpaObjectPolicyResolver`) | DB/JPA | JPA 기반 정책 리졸버 |
| `ObjectTypeStore` (`JpaObjectTypeStore`) | DB/JPA | JPA 기반 저장소 |
| `ObjectTypeRegistry` (`MyBatisObjectTypeRegistry`) | DB/MyBatis | MyBatis 기반 레지스트리 |
| `ObjectPolicyResolver` (`MyBatisObjectPolicyResolver`) | DB/MyBatis | MyBatis 기반 정책 리졸버 |
| `ObjectTypeStore` (`ObjectTypeMyBatisStore`) | DB/MyBatis | MyBatis 기반 저장소 |
| `ObjectTypeRegistry` (`JdbcObjectTypeRegistry`) | DB/JDBC | JDBC 기반 레지스트리 |
| `ObjectPolicyResolver` (`JdbcObjectPolicyResolver`) | DB/JDBC | JDBC 기반 정책 리졸버 |
| `ObjectTypeAdminService` | DB | ObjectType 생성·수정·삭제 서비스 |
| `ObjectTypeRuntimeService` | 공통 | ObjectType 조회·정책 확인 서비스 |
| `CachedObjectTypeRegistry` | 공통 | 캐시 레이어 래퍼 (cache.enabled=true 시) |
| `CachedObjectPolicyResolver` | 공통 | 캐시 레이어 래퍼 (cache.enabled=true 시) |

## 5) REST 엔드포인트

`studio.features.objecttype.web.enabled=true` 일 때 다음 컨트롤러가 등록된다.

| 컨트롤러 | 조건 | 역할 |
|---|---|---|
| `ObjectTypeController` | `ObjectTypeRuntimeService` 빈 존재 시 | ObjectType 정의 조회, 업로드 검증 |
| `ObjectTypeKeyController` | `ObjectTypeKeyRuntimeService` 빈 존재 시 | key 기반 ObjectType 정의 조회, 업로드 검증 |
| `ObjectTypeMgmtController` | `ObjectTypeAdminService` 빈 존재 시 (DB 모드) | ObjectType 생성·수정·삭제 관리 API |

- `ObjectTypeController` — 런타임 엔드포인트 (기본 `/api/object-types`)
- `GET /api/object-types/{objectType}/definition`
- `POST /api/object-types/{objectType}/validate-upload`
- `ObjectTypeKeyController` — key 기반 런타임 엔드포인트 (기본 `/api/object-types`, 지원 빈이 있을 때만 등록)
- `GET /api/object-types/keys/{key}/definition`
- `POST /api/object-types/keys/{key}/validate-upload`
- 런타임 엔드포인트는 well-known attachment 정책과 도메인 정보를 노출할 수 있으므로 `features:objecttype/read` 권한을 요구한다.
- 관리 엔드포인트의 조회 API는 `features:objecttype/read`, 생성/수정/삭제/reload API는 `features:objecttype/manage` 권한을 요구한다. 관리 API의 audit actor는 요청 body가 아니라 현재 `ApplicationPrincipal`에서 산출한다.
- 이 스타터는 `endpointAuthz` 빈이 있을 때만 ObjectType web controller를 등록한다. `studio.features.objecttype.web.enabled=true`를 사용할 때는 security starter와 method security 구성을 함께 활성화한다.
- `ObjectTypeMgmtController` — 관리 엔드포인트 (기본 `/api/mgmt/object-types`, DB 모드에서 `ObjectTypeAdminService` 빈이 있을 때만 등록됨)
- `GET /api/mgmt/object-types/{objectType}/policy/effective` — 저장 정책이 없을 때도 클라이언트 안내용 적용 정책을 반환한다. 저장 정책이면 `source=stored`, 내부 기본 정책이면 `source=default`다. 기본 정책 응답은 `maxFileMb=null`, `allowedExt=null`, `allowedMime=null`, `policyJson=null`이며 ObjectType별 추가 제한 없음으로 해석한다. Spring multipart 제한이나 attachment 서비스 공통 제한은 별도로 적용될 수 있다.

## 6) 참고 사항
- `studio-platform-objecttype` 모듈이 도메인 모델, 레지스트리, 정책 리졸버 구현을 제공하며,
  이 스타터는 해당 모듈을 `api` 의존성으로 전이 노출한다.
- 이 스타터는 기반 계약인 `studio-platform`, `studio-platform-data`, autoconfigure 관련 타입을
  `compileOnly`로 참조하므로 애플리케이션에는 `starter:studio-platform-starter`를 함께 추가해야 한다.
- `studio-platform-objecttype` 구현 패키지는 `domain/application/infrastructure/web` 구조로 정리되었고,
  이전 `studio.one.platform.objecttype.service`, `model`, `error`, `db`, `cache`, `yaml`, `web.dto` 패키지 wrapper는
  제공하지 않는다. 직접 import하는 코드는 `application.usecase`, `application.command`,
  `application.result`, `application.service`, `domain.model`, `domain.error`, `domain.port`,
  `infrastructure.persistence`, `infrastructure.cache`, `infrastructure.yaml`, `web.dto.request`,
  `web.dto.response` 기준으로 갱신해야 한다. 전체 mapping은 `studio-platform-objecttype/README.md`의
  패키지 구조 표를 따른다.
  REST endpoint와 JSON 응답 shape는 변경되지 않았다.
- MyBatis mapper XML을 복사하거나 커스터마이징한 경우 XML namespace와 row type FQCN도
  `infrastructure.persistence.mybatis` 및 `infrastructure.persistence.model` 기준으로 갱신해야 한다.
- YAML 모드에서는 애플리케이션 기동 시 YAML 파일을 읽어 메모리에 적재한다.
  파일이 없거나 `objecttypes` 목록이 없으면 경고 로그를 남기고 빈 레지스트리로 기동한다.
  YAML 파싱 또는 읽기 오류는 기동 실패로 처리한다.
  운영 환경에서는 리소스 경로와 로딩 결과를 별도 smoke check로 확인해야 한다.
- DB 모드에서 JPA를 사용할 경우 `ObjectTypeEntity` 엔터티 클래스를 포함한 JPA 스캔이
  자동으로 구성된다 (`@EntityScan`, `@EnableJpaRepositories`).
- 레지스트리·정책 캐시는 기본적으로 활성화되어 있다(TTL 300초, 최대 1000건).
  캐시를 끄려면 `studio.objecttype.registry.cache.enabled=false` 또는
  `studio.objecttype.policy.cache.enabled=false`로 설정한다.
- 기능을 완전히 끄려면 `studio.features.objecttype.enabled=false`로 설정한다.
