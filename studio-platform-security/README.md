# studio-platform-security

Spring Security 기반 인증/인가, JWT 발급/검증, 로그인 감사(audit), 계정 잠금, 비밀번호 재설정 토큰을 제공하는 모듈이다.
JPA/JDBC 영속성 구현과 REST 컨트롤러를 포함하며, 스타터에서 자동 구성된다.

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
- `studio-platform-security/src/main/resources/schema/postgres/V0.4.0__create_audit_tables.sql`
- `studio-platform-security/src/main/resources/schema/postgres/V0.5.0__create_jwt_tables.sql`

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

## 참고
- 사용자 테이블은 `studio-platform-user` 모듈의 `TB_APPLICATION_USER`를 사용한다.
- REST 컨트롤러는 스타터 설정에서 활성화된다.
