# studio-platform-starter-security-acl

ACL(Access Control List) 기반 도메인 권한 관리를 자동 구성하는 스타터이다.
JPA 기반 ACL 엔터티·리포지토리 스캔, 도메인 정책 레지스트리, 정책 동기화 서비스,
Micrometer 메트릭, 감사 로그를 자동으로 등록한다.
REST 엔드포인트를 통한 정책 동기화·관리 기능도 선택적으로 활성화할 수 있다.
핵심 구현은 `studio-platform-security-acl` 모듈에 있으며, 이 스타터는 그 위에
자동 구성(autoconfigure) 레이어를 더한다.

## 1) 의존성 추가
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-security-acl"))
    // 기반 스타터 (아직 추가하지 않은 경우)
    implementation(project(":starter:studio-platform-starter"))
    // JPA 필수
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // REST API 사용 시
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Spring ACL 사용 시 (use-spring-acl: true)
    implementation("org.springframework.security:spring-security-acl")
    // Micrometer 메트릭 사용 시
    implementation("io.micrometer:micrometer-core")
}
```

## 2) 기능 활성화
`studio.features.security-acl.enabled=true` 설정 시 ACL 자동 구성 전체가 활성화된다.
REST 엔드포인트와 정책 동기화는 별도 플래그로 제어한다.

```yaml
studio:
  features:
    security-acl:
      enabled: true
```

## 3) 설정

### ACL 기본 설정
```yaml
studio:
  features:
    security-acl:
      enabled: true
      admin-role: ROLE_ADMIN        # ACL 관리 권한 역할
      metrics-enabled: true         # Micrometer 메트릭 기록 여부
      audit-enabled: true           # ACL 변경 감사 로그 여부
      use-spring-acl: false         # Spring Security MutableAclService 사용 여부
      cache-name: aclCache          # Spring ACL 캐시 이름 (use-spring-acl: true 일 때)
      domain-aliases:               # 도메인 별칭 매핑
        users: user-management
      domain-indicators:            # 도메인 루트 식별자
        - "*"
        - __domain__
        - __root__
      entity-packages:
        - studio.one.base.security.acl.domain.entity
      repository-packages:
        - studio.one.base.security.acl.persistence
```

### 정책 동기화 REST 엔드포인트 설정
```yaml
studio:
  features:
    security-acl:
      web:
        enabled: true
        base-path: /api/mgmt
      sync:
        enabled: true
```

### 기본 ACL 정책 시딩(Seeding) 설정
```yaml
studio:
  security:
    acl:
      defaults:
        enabled: false
        policies:
          - domain: user-management
            sid: ROLE_ADMIN
            permissions:
              - READ
              - WRITE
              - DELETE
```

## 4) 자동 구성되는 주요 빈

| 빈 이름 / 타입 | 자동 구성 클래스 | 조건 |
|---|---|---|
| `AclResourceMapper` | `SecurityAclDatabaseAutoConfiguration` | `studio.features.security-acl.enabled=true` |
| `AclPermissionMapper` | `SecurityAclDatabaseAutoConfiguration` | 위 동일 |
| `DomainPolicyContributor` | `SecurityAclDatabaseAutoConfiguration` | `AclEntryRepository` 빈 존재 시 |
| `DomainPolicyRegistry` | `SecurityAclDatabaseAutoConfiguration` | 위 동일 |
| `EndpointAuthorizationImpl` | `SecurityAclDatabaseAutoConfiguration` | `DomainPolicyRegistry`, `EndpointModeGuard` 빈 존재 시 |
| `AclPermissionService` | `SecurityAclDatabaseAutoConfiguration` | `RepositoryAclPermissionService` (기본) 또는 `DefaultAclPermissionService` (`use-spring-acl: true`) |
| `AclMetricsRecorder` | `SecurityAclDatabaseAutoConfiguration` | Micrometer 클래스패스 존재 시 `MicrometerAclMetricsRecorder`, 없으면 noop |
| `AclPolicyRefreshPublisher` | `SecurityAclDatabaseAutoConfiguration` | 위 동일 |
| `AclAdministrationService` | `SecurityAclAdminAutoConfiguration` | `web.enabled=true` |
| `AclAdminController` | `SecurityAclAdminAutoConfiguration` | `AclAdministrationService` 빈 존재 시 |
| `AclActionController` | `SecurityAclAdminAutoConfiguration` | `web.enabled=true` |
| `RoleAclSidSyncListener` | `SecurityAclAdminAutoConfiguration` | 역할 변경 시 SID 자동 동기화 |
| `AclPolicySynchronizationService` | `SecurityAclWebAutoConfiguration` | `web.enabled=true` |
| `AclSyncController` | `SecurityAclWebAutoConfiguration` | 정책 동기화 REST 엔드포인트 |
| `AclPolicySeeder` | `DefaultAclPolicyAutoConfiguration` | `sync.enabled=true` |
| `AclPolicySyncEventListener` | `DefaultAclPolicyAutoConfiguration` | 위 동일 |

JPA 엔터티·리포지토리는 `SecurityAclDatabaseAutoConfiguration.EntityScanConfig` 및
`JpaWiring` 내부 구성 클래스에 의해 자동 스캔된다.

## 5) 참고 사항
- `studio-platform-security-acl` 모듈이 ACL 도메인 모델, 리포지토리, 정책 서비스 구현을 제공하며,
  이 스타터는 해당 모듈을 `api` 의존성으로 전이 노출한다.
- 기본적으로 Spring Security `MutableAclService` 없이 직접 리포지토리를 통해 권한을 관리한다
  (`use-spring-acl: false`). Spring ACL 표준 방식이 필요할 때만 `use-spring-acl: true`로 변경한다.
- SpEL을 통한 엔드포인트 권한 체크는 `@endpointAuthz.can('domain','component','action')` 형식을 사용한다.
- ACL 캐시는 Spring ACL 모드(`use-spring-acl: true`)에서만 활성화된다.
- 정책 동기화 REST 엔드포인트는 `studio.features.security-acl.web.enabled=true` 및
  `studio.features.security-acl.sync.enabled=true` 가 모두 활성화될 때만 노출된다.
- 기능을 완전히 끄려면 `studio.features.security-acl.enabled=false`로 설정한다.
