# studio-platform-starter-security

Spring Security 기반 인증·인가 인프라를 빠르게 구성하기 위한 스타터이다.
JWT 인증, 리프레시 토큰, 계정 잠금, 비밀번호 재설정, 로그인 감사(audit), CORS 정책을
각각 독립적인 자동 구성 클래스로 제공하여 필요한 기능만 선택적으로 활성화할 수 있다.
핵심 구현은 `studio-platform-security` 모듈에 있으며, 이 스타터는 그 위에
자동 구성(autoconfigure) 레이어를 더한다.

## 1) 의존성 추가
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-security"))
    // 기반 스타터 (아직 추가하지 않은 경우)
    implementation(project(":starter:studio-platform-starter"))
    // REST API 및 Spring Security 필수
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // JPA 사용 시 (리프레시 토큰·계정 잠금 JPA 모드)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // 비밀번호 재설정 메일 발송 시
    implementation("org.springframework.boot:spring-boot-starter-mail")
}
```

## 2) 기능 활성화
Security 자동 구성 전체는 `studio.features.security.enabled=true` 일 때만 활성화된다.
개별 기능(JWT, 계정 잠금, 로그인 감사 등)은 각자의 `enabled` 속성으로 제어한다.

```yaml
studio:
  features:
    security:
      enabled: true
```

## 3) 설정

### 전역 보안 설정
```yaml
studio:
  features:
    security:
      enabled: true
      default-role: ROLE_USER
      password-encoder:
        algorithm: BCRYPT    # BCRYPT | PBKDF2 | CUSTOM
        bcrypt-strength: 10
```

### JWT 설정
```yaml
studio:
  features:
    security:
      jwt:
        enabled: true
        secret: your-very-long-secret-key-min-256-bits
        issuer: studio-api
        access-ttl: PT15M    # 15분 (ISO 8601 duration)
        refresh-ttl: PT7D    # 7일
        header: Authorization
        prefix: Bearer
        cookie-secure: true
        cookie-same-site: Strict
        refresh-cookie-name: refresh_token
        cookie-path: /api/auth
        endpoints:
          login-enabled: true
          refresh-enabled: true
          base-path: /api/auth
        permit:
          - /api/auth/**
        persistence: jpa     # jpa | jdbc (선택, 전역 설정 상속)
```

JWT refresh token, password reset token, 로그인 실패 감사 `jdbc` 영속성은 현재 PostgreSQL 전용 SQL(`returning`, `inet`, `::inet`)을 사용한다.
starter는 해당 `jdbc` 저장소 bean 생성 시 DB 제품명이 PostgreSQL이 아니면 fail-fast로 기동을 중단한다.
계정 잠금 `jdbc` 구현은 portable SQL만 사용하므로 PostgreSQL guard를 적용하지 않는다.
로그인 실패 감사 JPA 기본 엔티티와 schema는 PostgreSQL `inet` 기준이므로 non-PostgreSQL 환경은 DB별 schema와 repository override가 필요하다.

로그인 실패 감사의 클라이언트 IP는 기본적으로 `HttpServletRequest#getRemoteAddr()`에서 가져온다.
신뢰된 reverse proxy가 헤더를 재작성하는 환경에서만
`studio.security.audit.login-failure.capture-ip-header=X-Forwarded-For`와
`studio.security.audit.login-failure.trusted-proxy-cidrs`를 함께 설정한다.
`capture-ip-header` 값은 단일 IP literal 또는 `X-Forwarded-For`처럼 comma-separated IP 목록을 담는
헤더만 지원하며, comma-separated 목록은 오른쪽에서 왼쪽으로 읽어 첫 번째 비신뢰 hop을 클라이언트 IP로
판정한다. RFC 7239 `Forwarded` 헤더 문법은 해석하지 않는다.
해당 설정이 있으면 starter가 `SecurityFilterChain`의 username/password 인증 필터에도 동일한
`ClientRequestDetails` source를 적용한다. 이미 custom `AuthenticationDetailsSource`가 설정된
인증 필터는 덮어쓰지 않는다.
감사 로그 저장 실패는 인증 실패 응답을 500으로 바꾸지 않으며,
`LoginFailureAuditFailureMonitor` bean의 실패 카운터와 마지막 오류 타입으로 관측할 수 있다.
비동기 감사 executor 포화 시 인증 경로를 막지 않기 위해 짧은 bounded wait 후에도 queue 여유가 없는
감사 작업은 버리며, rejected/dropped execution 카운터로 포화와 drop을 관측한다.
계정 잠금 처리는 별도 synchronous listener에서 먼저 실행되어 감사 executor 포화와 분리된다.

### 계정 잠금 설정
```yaml
studio:
  features:
    security:
      account-lock:
        enabled: true
        max-attempts: 5
        window: PT10M        # 실패 집계 기간 (0이면 무제한 누적)
        lock-duration: PT30M # 자동 해제 대기 시간 (0이면 수동 해제만 가능)
        reset-on-success: true
        persistence: jpa     # jpa | jdbc
```

### 비밀번호 재설정 설정
```yaml
studio:
  features:
    security:
      password-reset:
        enabled: true
        reset-password-url: https://example.com/reset-password
        persistence: jpa
```

### 로그인 감사(Audit) 설정
```yaml
studio:
  features:
    security:
      audit:
        login-failure:
          enabled: true
          retention-days: 90   # 로그 보존 기간(일). 미설정 시 자동 삭제 안 함
          web:
            enabled: true
            base-path: /api/mgmt
          persistence: jpa
```

### CORS 설정
```yaml
studio:
  features:
    security:
      cors:
        enabled: true
        allowed-origins:
          - https://app.example.com
        allowed-origin-patterns: []
        allowed-methods:
          - GET
          - POST
          - PUT
          - DELETE
          - OPTIONS
        allowed-headers:
          - "*"
        exposed-headers: []
        allow-credentials: true
        max-age: 3600
```

## 4) 자동 구성되는 주요 빈

| 빈 이름 / 타입 | 자동 구성 클래스 | 설명 |
|---|---|---|
| `PasswordEncoder` | `SecurityAutoConfiguration` | DelegatingPasswordEncoder (BCrypt 기본) |
| `CorsConfigurationSource` | `SecurityAutoConfiguration` | CORS 정책 소스 (`/**` 전체 적용) |
| `AuthenticationErrorHandler` | `SecurityAutoConfiguration` | JSON 형식 인증 오류 응답 처리기 |
| `AuthenticationManager` | `SecurityAutoConfiguration` | DAO 기반 인증 매니저 |
| `UserDetailsService` | `SecurityAutoConfiguration` | `ApplicationUserDetailsService` (계정 잠금 연동 포함) |
| `PrincipalResolver` | `SecurityAutoConfiguration` | `SecurityPrincipalResolver` |
| `JwtTokenProvider` | `JwtAutoConfiguration` | JWT 서명·검증·파싱 |
| `RefreshTokenStore` | `JwtAutoConfiguration` | 리프레시 토큰 저장소 (JPA 또는 JDBC) |
| `JwtAuthController` | `JwtAutoConfiguration` | `POST /api/auth/login` |
| `JwtRefreshController` | `JwtAutoConfiguration` | `POST /api/auth/refresh` |
| `AccountLockService` | `AccountLockAutoConfiguration` | 계정 잠금 서비스 |
| `AuthenticationEventPublisher` | `LoginFailureAuditAutoConfiguration` | 인증 이벤트 발행 |
| `LoginFailureEventListener` | `LoginFailureAuditAutoConfiguration` | 로그인 실패 이벤트 리스너 |
| `LoginSuccessEventListener` | `LoginFailureAuditAutoConfiguration` | 로그인 성공 이벤트 리스너 |
| `LoginFailureQueryService` | `LoginFailureAuditAutoConfiguration` | 로그인 실패 이력 조회 서비스 |
| `LoginFailureLogController` | `LoginFailureAuditAutoConfiguration` | 로그인 실패 이력 REST 엔드포인트 |
| `LoginFailureLogRetentionJob` | `LoginFailureAuditAutoConfiguration` | 만료 감사 로그 자동 삭제 잡 |

## 관련 모듈
- `studio-platform-security` — 이 스타터가 자동 구성하는 핵심 구현 모듈 (JWT, 계정 잠금, 감사 도메인)
- `studio-platform-starter-security-acl` — ACL 기반 세밀한 권한 관리가 필요할 때 함께 추가하는 짝 스타터
- `studio-platform-starter-user` — `UserDetailsService` 구현(`ApplicationUserDetailsService`)을 제공하는 사용자 스타터

## 5) 참고 사항
- `studio-platform-security` 모듈이 핵심 구현(JWT, 계정 잠금, 감사 도메인 모델)을 제공하며,
  이 스타터는 해당 모듈을 `api` 의존성으로 전이 노출한다.
- JWT `secret`이 설정되지 않으면 `JwtSecretPresenceGuard`가 기동 실패를 일으킨다
  (`studio.features.security.fail-if-missing=false`로 우회 가능하나 권장하지 않는다).
- 리프레시 토큰, 계정 잠금 기록의 영속성 타입은 기능별 `persistence` 속성으로 개별 지정하거나,
  전역 `studio.persistence.type` 값을 상속한다.
- SHA-256 비밀번호 알고리즘은 보안상 허용되지 않으며, 설정 시 기동 오류가 발생한다.
- 기능을 완전히 끄려면 `studio.features.security.enabled=false`로 설정한다.
