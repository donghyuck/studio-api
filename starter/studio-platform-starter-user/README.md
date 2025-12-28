# studio-platform-starter-user

사용자 도메인(유저/그룹/권한/회사) 기능을 빠르게 붙이기 위한 스타터이다. JPA 또는 JDBC 영속성에 맞춰
엔티티/리포지토리 스캔과 서비스 빈을 등록하고, 선택적으로 REST 엔드포인트를 노출한다.

## 1) 의존성 추가
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-user"))
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

## 4) 패키지 스캔 설정
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

## 5) 자동 구성되는 주요 빈
- `ApplicationUserService`, `ApplicationGroupService`, `ApplicationRoleService`, `ApplicationCompanyService`
- `UserMutator` (기본 `ApplicationUserMutator`)
- `UserCacheEvictListener` (CacheManager가 있을 때)

서비스는 `JdbcTemplate`을 사용하므로 `studio-platform-starter`의 JDBC 자동구성이 켜져 있어야 한다.

## 6) REST 엔드포인트 (선택)
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
```

기본 엔드포인트 경로:
- `/api/mgmt/users`
- `/api/mgmt/groups`
- `/api/mgmt/roles`
- `/api/self`

## 7) 참고 사항
- JPA 사용 시 `EntityManagerFactory`가 필요하다.
- JDBC 모드에서는 `JdbcTemplate` 기반 리포지토리가 사용된다.
- 기능을 끄려면 `studio.features.user.enabled=false`로 비활성화한다.
