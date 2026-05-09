# studio-platform-security

Spring Security 기반 인증/인가, JWT 발급/검증, 로그인 감사(audit), 계정 잠금, 비밀번호 재설정 토큰을 제공하는 모듈이다.
JPA/JDBC 영속성 구현과 REST 컨트롤러를 포함하며, 스타터에서 자동 구성된다.

## 요약
JWT 인증/인가, 리프레시 토큰 회전, 비밀번호 재설정 토큰, 로그인 감사/계정 잠금 기능을 제공한다.

## 설계
- Spring Security 필터 체인 + JWT 기반 인증을 기본으로 한다.
- 리프레시/리셋 토큰은 DB에 저장해 회전/만료를 관리한다.
- 실패 로그는 감사 테이블에 기록한다.

## 사용법
- `studio-platform-starter-security` 또는 직접 의존성 추가
- `JwtConfig` 구현체 제공
- SecurityFilterChain에서 JWT 필터를 등록

## 확장 포인트
- `JwtConfig`/`JwtTokenProvider` 교체
- `UserDetailsService` 커스텀 구현
- 토큰 저장소(JPA/JDBC) 교체
- 감사/잠금 정책 커스터마이징

## 설정
- `studio.security.enabled`
- `studio.security.jwt.*` (secret, issuer, access-ttl, refresh-ttl, endpoints)
- `studio.security.auth.password-reset.*`

## 환경별 예시
- **dev**: JWT TTL을 짧게 두고 refresh 토큰 회전 테스트
- **stage**: secret/issuer를 운영과 유사하게, 보안 로그 레벨은 INFO 이상
- **prod**: refresh 토큰 만료/회전 정책 강화, 비밀번호 재설정 TTL 최소화

## YAML 예시
```yaml
studio:
  security:
    enabled: true
    jwt:
      enabled: true
      secret: "change-me"
      issuer: "my-app"
      access-ttl: PT15M
      refresh-ttl: PT14D
      endpoints:
        base-path: /api/auth
        login-enabled: true
        refresh-enabled: true
    auth:
      password-reset:
        enabled: true
        reset-password-url: https://example.com/reset-password
        persistence: jpa
```

## ADR
- `docs/adr/0001-jwt-refresh-tokens.md`

## 구성 패키지
- `studio.one.base.security.jwt`  
  JWT 생성/검증, 필터, 설정 인터페이스
- `studio.one.base.security.jwt.refresh`  
  리프레시 토큰 도메인/저장소
- `studio.one.base.security.jwt.reset`  
  비밀번호 재설정 토큰 도메인/저장소
- `studio.one.base.security.userdetails`  
  UserDetails/서비스
- `studio.one.base.security.authentication.lock`  
  계정 잠금 서비스/리포지토리
- `studio.one.base.security.audit`  
  로그인 실패 로그/조회 서비스/리스너
- `studio.one.base.security.web`  
  컨트롤러/DTO/매퍼
- `studio.one.base.security.handler`  
  인증/인가 예외 처리 핸들러
- `studio.one.base.security.exception`  
  보안 관련 예외

## 스키마 (PostgreSQL)
다음 PostgreSQL 스키마 파일이 포함되어 있다.
- `studio-platform-security/src/main/resources/schema/security/postgres/V400__create_security_tables.sql`

Flyway 버전 범위는 `docs/flyway-versioning.md`의 security 범위(V400-V499)를 따른다.

JWT refresh token, password reset token, 로그인 실패 감사 JDBC 구현은 현재 PostgreSQL 문법(`returning`, `inet`, `::inet`)을 사용한다.
starter는 해당 JDBC 저장소 bean 생성 시 DB 제품명이 PostgreSQL이 아니면 fail-fast로 기동을 중단한다.
계정 잠금 JDBC 구현은 portable SQL만 사용하므로 PostgreSQL guard를 적용하지 않는다.
로그인 실패 감사 JPA 기본 엔티티와 schema는 PostgreSQL `inet` 기준이므로 non-PostgreSQL 환경은 DB별 schema와 repository override가 필요하다.

로그인 실패 감사 IP는 기본적으로 socket `remoteAddr`를 사용한다. `X-Forwarded-For` 같은 헤더 기반
IP 캡처는 신뢰된 proxy가 헤더를 재작성하는 환경에서만 starter의 `capture-ip-header`와
`trusted-proxy-cidrs` 설정으로 명시적으로 활성화한다.
`capture-ip-header`는 단일 IP literal 또는 `X-Forwarded-For`처럼 comma-separated IP 목록을 담는
헤더만 지원하며, comma-separated 목록은 오른쪽에서 왼쪽으로 읽어 첫 번째 비신뢰 hop을 클라이언트 IP로
판정한다. RFC 7239 `Forwarded` 헤더 문법은 해석하지 않는다.
starter는 `capture-ip-header`가 설정된 경우 `SecurityFilterChain`의 username/password 인증 필터에도
동일한 `ClientRequestDetails` source를 적용한다. 이미 custom `AuthenticationDetailsSource`가 설정된
인증 필터는 덮어쓰지 않는다.
감사 로그 저장 실패는 인증 실패 응답을 500으로 바꾸지 않으며,
`LoginFailureAuditFailureMonitor`로 실패 카운터와 마지막 오류 타입을 관측할 수 있다.
비동기 감사 executor가 포화되면 인증 경로를 막지 않기 위해 짧은 bounded wait 후에도 queue 여유가 없는
감사 작업은 버리며, `LoginFailureAuditFailureMonitor`의 rejected/dropped execution 카운터로 포화와 drop을 관측한다.
계정 잠금 처리는 별도 synchronous listener에서 먼저 실행되어 감사 executor 포화와 분리된다.

## ERD (개념)
```text
tb_login_failure_log
  - 로그인 실패 감사 로그

TB_APPLICATION_REFRESH_TOKEN
  - USER_ID -> TB_APPLICATION_USER.USER_ID
  - REPLACED_BY_ID -> TB_APPLICATION_REFRESH_TOKEN.ID

TB_APPLICATION_PASSWORD_RESET_TOKEN
  - USER_ID -> TB_APPLICATION_USER.USER_ID
```

## 주요 기능
- JWT 액세스/리프레시 토큰 발급 및 검증
- 리프레시 토큰 저장/회전/폐기
- 비밀번호 재설정 토큰 저장/만료
- 로그인 실패 감사 로그 기록/조회
- 계정 잠금(실패 횟수/시간 창/잠금 기간)

## 개발자 사용 가이드
### 1) 의존성 추가
```kotlin
dependencies {
    implementation(project(":studio-platform-security"))
    // 또는 스타터 사용 시
    implementation(project(":starter:studio-platform-starter-security"))
}
```

### 2) 스키마 준비
Flyway 등 마이그레이션 도구에 스키마를 등록한다.

### 3) JWT 구성
`JwtConfig` 구현을 통해 시크릿/TTL/헤더/클레임 키 등을 주입한다.

```java
@Component
public class AppJwtConfig implements JwtConfig {
    // secret, issuer, ttl 등 구현
}
```

### 4) 서비스 사용 예시
```java
public class AuthFacade {
    private final JwtTokenProvider tokenProvider;

    public AuthFacade(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public String issueToken(Authentication auth) {
        return tokenProvider.generateToken(auth);
    }
}
```

### 5) 감사 로그/계정 잠금
- 로그인 실패 이벤트 리스너가 감사 로그를 기록한다.
- 계정 잠금은 `AccountLockService`를 통해 실패 횟수와 잠금 시간을 관리한다.

## 웹 응용프로그램 적용 가이드
스타터가 자동 구성해주더라도, 실제 웹 애플리케이션에서는 보안 필터 체인과 사용자 정보 제공을 직접 연결해야 한다.

### 1) 필수 의존성/빈 준비
- `studio-platform-starter-security` 의존성 추가
- 사용자 기능을 사용할 경우 `studio-platform-starter-user` 의존성 추가
- `UserDetailsService` 빈이 반드시 있어야 한다
  - user 스타터 사용 시 자동 제공됨
  - 커스텀 사용자 저장소를 쓰면 직접 구현/등록해야 함

### 2) 필수 프로퍼티 설정
최소 설정 예시:
```yaml
studio:
  security:
    enabled: true
    jwt:
      enabled: true
      secret: "change-me"
      issuer: "my-app"
      endpoints:
        base-path: /api/auth
        login-enabled: true
        refresh-enabled: true
```

### 3) SecurityFilterChain 구성 클래스 추가
SecurityFilterChain은 애플리케이션의 보안 필터 흐름, 인증/인가 정책, 예외 처리 방식을 결정한다.  
스타터는 필요한 빈을 제공하지만, 실제 요청 경로/허용 정책은 앱마다 달라 직접 구성해야 한다.  
JWT 필터/예외 처리/권한 정책을 직접 구성해야 한다. 아래는 최소 구성 예시이다.
```java
@Configuration
@RequiredArgsConstructor
public class SecurityFilterConfig {

    private final SecurityProperties securityProperties;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            @Qualifier(ServiceNames.USER_DETAILS_SERVICE) UserDetailsService userDetailsService,
            AuthenticationErrorHandler authenticationErrorHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(
                securityProperties.getJwt().getEndpoints().getBasePath(),
                jwtTokenProvider,
                userDetailsService,
                authenticationErrorHandler);

        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> {
                    authz.antMatchers(securityProperties.getJwt().getEndpoints().getBasePath() + "/login").permitAll();
                    authz.antMatchers(securityProperties.getJwt().getEndpoints().getBasePath() + "/refresh").permitAll();
                    authz.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(new ApplicationAccessDeniedHandler(authenticationErrorHandler))
                        .authenticationEntryPoint(new ApplicationAuthenticationEntryPoint(authenticationErrorHandler)))
                .authenticationManager(authenticationManager)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```
실제 프로젝트에서는 애플리케이션 보안 설정 클래스에서 동일한 필터 체인 구성을 참고해 적용하면 된다.

### 4) 스키마 준비
JWT/리프레시/비밀번호 재설정 토큰 저장을 사용하면 아래 스키마가 필요하다.
- `studio-platform-security/src/main/resources/schema/security/postgres/V400__create_security_tables.sql`

## 참고
- 사용자 테이블은 `studio-platform-user` 모듈의 `TB_APPLICATION_USER`를 사용한다.
- REST 컨트롤러는 스타터 설정에서 활성화된다.
