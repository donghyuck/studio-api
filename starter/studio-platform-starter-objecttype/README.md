# studio-platform-starter-objecttype

ObjectType 레지스트리와 정책을 자동 구성하는 스타터이다.
YAML 파일 또는 데이터베이스(JPA/JDBC) 두 가지 모드로 ObjectType 정보를 로딩·캐싱하며,
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
objectTypes:
  - type: 1001
    key: document
    name: 문서
    description: 문서 도메인 오브젝트
  - type: 1002
    key: image
    name: 이미지
policies:
  - objectType: 1001
    maxSize: 10485760
    allowedExtensions:
      - pdf
      - docx
```

### DB 모드 설정
```yaml
studio:
  objecttype:
    mode: db
  persistence:
    type: jpa   # jpa | jdbc
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
      persistence: jdbc   # 이 기능만 jdbc 사용
```

### REST 엔드포인트 설정
```yaml
studio:
  features:
    objecttype:
      web:
        enabled: true
        base-path: /api/mgmt
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
| `ObjectTypeController` | `ObjectTypeRuntimeService` 빈 존재 시 | ObjectType 목록 조회, 타입 기반 정책 조회 |
| `ObjectTypeMgmtController` | `ObjectTypeAdminService` 빈 존재 시 (DB 모드) | ObjectType 생성·수정·삭제 관리 API |

- `ObjectTypeController` — 공개 조회 엔드포인트 (YAML·DB 모드 모두 활성화 가능)
- `ObjectTypeMgmtController` — 관리 엔드포인트 (DB 모드에서 `ObjectTypeAdminService` 빈이 있을 때만 등록됨)
- `GET /api/mgmt/object-types/{objectType}/policy/effective` — 저장 정책이 없을 때도 클라이언트 안내용 적용 정책을 반환한다. 저장 정책이면 `source=stored`, 내부 기본 정책이면 `source=default`다. 기본 정책은 현재 제한 없음(`maxFileMb`, `allowedExt`, `allowedMime`, `policyJson` 모두 `null`)이다.

## 6) 참고 사항
- `studio-platform-objecttype` 모듈이 도메인 모델, 레지스트리, 정책 리졸버 구현을 제공하며,
  이 스타터는 해당 모듈을 `api` 의존성으로 전이 노출한다.
- YAML 모드에서는 애플리케이션 기동 시 YAML 파일을 읽어 메모리에 적재한다.
  파일이 없으면 기동에 실패하므로 리소스 경로를 정확히 설정해야 한다.
- DB 모드에서 JPA를 사용할 경우 `ObjectTypeEntity` 엔터티 클래스를 포함한 JPA 스캔이
  자동으로 구성된다 (`@EntityScan`, `@EnableJpaRepositories`).
- 레지스트리·정책 캐시는 기본적으로 활성화되어 있다(TTL 300초, 최대 1000건).
  캐시를 끄려면 `studio.objecttype.registry.cache.enabled=false` 또는
  `studio.objecttype.policy.cache.enabled=false`로 설정한다.
- 기능을 완전히 끄려면 `studio.features.objecttype.enabled=false`로 설정한다.
