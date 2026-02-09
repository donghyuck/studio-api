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
- `studio.security.jwt.*` (secret, issuer, ttl, endpoints)

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
      access-ttl-seconds: 900
      refresh-ttl-seconds: 1209600
      endpoints:
        base-path: /api/auth
        login-enabled: true
        refresh-enabled: true
    password-reset:
      ttl-seconds: 1800
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
다음 스키마 파일이 포함되어 있다.
- `studio-platform-security/src/main/resources/schema/security/postgres/V0__create_security_tables.sql`

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
실제 프로젝트에서는 `studio-server/src/main/java/studio/server/config/SecurityFilterConfig.java`를 참고해도 된다.

### 4) 스키마 준비
JWT/리프레시/비밀번호 재설정 토큰 저장을 사용하면 아래 스키마가 필요하다.
- `studio-platform-security/src/main/resources/schema/security/postgres/V0__create_security_tables.sql`

## 참고
- 사용자 테이블은 `studio-platform-user` 모듈의 `TB_APPLICATION_USER`를 사용한다.
- REST 컨트롤러는 스타터 설정에서 활성화된다.
