# studio-platform-starter-user

사용자 도메인(유저/그룹/권한/회사) 기능을 빠르게 붙이기 위한 스타터이다. JPA 또는 JDBC 영속성에 맞춰
엔터티/리포지토리 스캔과 서비스 빈을 등록하고, 선택적으로 REST 엔드포인트를 노출한다.
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

기본 컨트롤러는 `ApplicationUserMapper`/`ApplicationUserService` 빈이 있을 때만 등록된다.
커스텀 컨트롤러를 제공할 때는 `UserMgmtControllerApi`/`UserPublicControllerApi`/`UserMeControllerApi`
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

## 7) 참고 사항
- JPA 사용 시 `EntityManagerFactory`가 필요하다.
- JDBC 모드에서는 `JdbcTemplate` 기반 리포지토리가 사용된다.
- 기능을 끄려면 `studio.features.user.enabled=false`로 비활성화한다.
- PostgreSQL 스키마는 `studio-platform-user-default`에 포함된다:
  `studio-platform-user-default/src/main/resources/schema/postgres/V0.1.0__create_user_tables.sql`
