# studio-api (one)

[![release](https://img.shields.io/badge/release-0.4-blue.svg)](https://github.com/metasfresh/metasfresh/releases/tag/5.175)
[![license](https://img.shields.io/badge/license-APACHE-blue.svg)](https://github.com/metasfresh/metasfresh/blob/master/LICENSE.md)

A backend spellbook for every creator.

A modular Spring Boot API platform that provides authentication, file management, user control, and logging — all in one base system for building web services at scale.

Think of it as your magic studio engine.

| Name             | Version  | Initial Release | EOS (End of Service) |
| ---------------- | -------- | --------------- | -------------------- |
| Spring Boot      | 2.7.18   |                 |                      |
| Spring Framework | 5.3.31   | 2020-10-27      | 2024-12-31           |
| Spring Security  | 5.7.11   | 2022-05-16      | 2023-05-16           |
| Spring Cloud     | 2021.0.8 |                 |                      |

Studio Api 는 component, starter 모듈들로 구성된다.

코어 모듈은 아래와 같이 구성되어 있다.

```
├── starter
│   ├── studio-platform-starter
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── java
│   │   │   │   │   └── studio.echo.platform.autoconfigure
│   │   │   │   │       ├── i18n
│   │   │   │   │       ├── jpa
│   │   │   │   │       │   └── auditor
│   │   │   │   └── resources
│   │   │   │       └── META-INF
│   │   │   │           ├── i18n/platform
│   │   │   │           └── spring
│   │   │   │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │   │   └── test
│   │   └── build.gradle.kts
│   ├── studio-platform-starter-jasypt
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── java
│   │   │   │   │   └── studio.echo.platform.ajasypt.autoconfigure
│   │   │   │   └── resources
│   │   │   │       └── META-INF
│   │   │   │           ├── i18n/jasypt
│   │   │   │           └── spring
│   │   │   │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │   │   └── test
│   │   └── build.gradle.kts
│   ├── studio-platform-starter-security
│   │   ├── src
│   │   │   ├── main
│   │   │   │   ├── java
│   │   │   │   │   └── studio.echo.platform.security.autoconfigure
│   │   │   │   └── resources
│   │   │   │       └── META-INF
│   │   │   │           ├── i18n/jasypt
│   │   │   │           └── spring
│   │   │   │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │   │   └── test
│   │   └── build.gradle.kts
│   └── studio-platform-starter-user
│       ├── src
│       │   └── main
│       │       ├── java
│       │       │   └── studio.echo.platform.user.starter
│       │       │       └── autoconfig
│       │       │           └── condition
│       │       └── resources
│       └── build.gradle.kts
├── studio-platform
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── studio.echo.platform
│   │       └── resources
│   │           ├── i18n/platform
│   │           ├── META-INF
│   │           └── banner.txt
├── studio-platform-autoconfigure
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── studio.echo.jplatform.autoconfigure
│   │       └── resources
│   │           └── META-INF
│   └── build.gradle.kts
│
├── studio-platform-jpa
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── studio.echo.jplatform
│   │       └── resources
│   │           └── schema
│   │               ├── mysql
│   │               └── postgres
│   └── build.gradle.kts
├── studio-platform-security
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── studio.echo.base.security
│   │       └── resources
│   │           ├── META-INF
│   │           └── i18n/security
│   └── build.gradle.kts
├── studio-platform-user
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── studio.echo.base.user
│   │       └── resources
│   │           ├── META-INF
│   │           └── i18n/security
│   │           └── schema
│   └── build.gradle.kts
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
└── settings.gradle.kts

```

## studio-platform-user 모듈

`studio-platform-user` 는 사용자, 그룹, 역할, 회사 도메인을 한꺼번에 다루는 관리용 모듈이다. 스프링 데이터 JPA 기반의 엔티티/레포지토리 계층과 캐시 지원 서비스, 그리고 `/api/mgmt` 하위의 REST 엔드포인트를 제공하여 사용자 관리를 손쉽게 애플리케이션에 통합할 수 있게 한다.【F:studio-platform-user/src/main/java/studio/one/base/user/domain/entity/ApplicationUser.java†L1-L114】【F:studio-platform-user/src/main/java/studio/one/base/user/service/impl/ApplicationUserServiceImpl.java†L1-L200】【F:studio-platform-user/src/main/java/studio/one/base/user/web/controller/UserController.java†L1-L118】

### 제공 기능

- **사용자(User)** – 생성, 조회, 검색, 수정, 활성/비활성화 등의 CRUD API와 계정 잠금 해제/감사 로깅을 지원한다.【F:studio-platform-user/src/main/java/studio/one/base/user/service/impl/ApplicationUserServiceImpl.java†L49-L164】【F:studio-platform-user/src/main/java/studio/one/base/user/web/controller/UserController.java†L54-L160】
- **그룹(Group)** – 그룹 단위로 사용자와 역할을 묶어 관리하며, `/groups` 엔드포인트를 통해 CRUD 및 멤버십 조작을 수행한다.【F:studio-platform-user/src/main/java/studio/one/base/user/web/controller/GroupController.java†L86-L236】
- **역할(Role)** – 애플리케이션 역할을 정의하고 그룹/사용자와의 매핑을 처리한다.【F:studio-platform-user/src/main/java/studio/one/base/user/web/controller/RoleController.java†L49-L214】
- **회사(Company)** – 회사 정보를 등록/조회하여 멀티 테넌시 시나리오를 지원한다.【F:studio-platform-user/src/main/java/studio/one/base/user/web/controller/CompanyController.java†L1-L165】
- **자기 조회(Self)** – 인증된 사용자가 자신의 프로필을 `/api/self` 경로에서 조회할 수 있다.【F:studio-platform-user/src/main/java/studio/one/base/user/web/controller/MeController.java†L1-L106】

모든 엔드포인트는 `studio.features.user.web.base-path` (기본값 `/api/mgmt`) 하위에 노출되며, `@PreAuthorize` 가 붙은 세부 권한 검사를 통해 역할 기반 접근 제어를 적용한다.【F:studio-platform-user/src/main/java/studio/one/base/user/web/controller/UserController.java†L54-L160】【F:starter/studio-platform-starter-user/src/main/java/studio/one/platform/user/autoconfigure/WebProperties.java†L18-L86】

### 사용 방법

1. **스타터 의존성 추가** – 자동 구성과 REST 엔드포인트를 사용하려면 `studio-platform-starter-user` 모듈을 애플리케이션에 추가한다.

   ```kotlin
   dependencies {
       implementation(project(":starter:studio-platform-starter-user"))
   }
   ```

2. **기능 활성화** – `application.yml` 혹은 `application.properties` 에서 사용자 기능을 켠다.

   ```yaml
   studio:
     features:
       user:
         enabled: true
         web:
           base-path: /api/mgmt
   ```

   기본 JPA 레포지토리/엔티티 패키지는 스타터가 제공하지만, 필요 시 `studio.features.user.repository-packages`, `studio.features.user.entity-packages`, `studio.features.user.component-packages` 로 커스터마이징할 수 있다.【F:starter/studio-platform-starter-user/src/main/java/studio/one/platform/user/autoconfigure/UserFeatureProperties.java†L16-L44】

3. **엔드포인트 구성** – 각 도메인 엔드포인트는 `studio.features.user.web.endpoints.<user|group|role|company>` 설정으로 개별 활성화/권한 모드를 조정할 수 있으며, 기본 역할 요구 사항은 `ADMIN`/`MANAGER` 조합으로 제공된다.【F:starter/studio-platform-starter-user/src/main/java/studio/one/platform/user/autoconfigure/WebProperties.java†L24-L86】【F:starter/studio-platform-starter-user/src/main/java/studio/one/platform/user/autoconfigure/UserEndpointsAutoConfiguration.java†L33-L121】

4. **도메인 서비스 확장** – `ApplicationUserService`, `ApplicationGroupService`, `ApplicationRoleService`, `ApplicationCompanyService` 인터페이스와 기본 구현이 빈으로 등록되므로, 커스터마이징이 필요한 경우 동일한 인터페이스를 구현한 빈을 제공하여 교체할 수 있다.【F:studio-platform-user/src/main/java/studio/one/base/user/service/ApplicationUserService.java†L1-L82】【F:studio-platform-user/src/main/java/studio/one/base/user/service/impl/ApplicationUserServiceImpl.java†L1-L200】【F:starter/studio-platform-starter-user/src/main/java/studio/one/platform/user/autoconfigure/UserServicesAutoConfiguration.java†L1-L120】

위 설정만으로 사용자/권한 관리를 위한 REST API, 서비스, 도메인 이벤트 퍼블리셔가 통합되며, 필요 시 MapStruct 기반 DTO 매퍼 빈도 자동으로 주입된다.【F:starter/studio-platform-starter-user/src/main/java/studio/one/platform/user/autoconfigure/UserEndpointsAutoConfiguration.java†L53-L106】【F:studio-platform-user/build.gradle.kts†L1-L24】

## studio-platform-security 모듈

`studio-platform-security` 는 JWT 인증, 리프레시 토큰 저장소, 계정 잠금(Account Lock), 로그인 실패 감사 로깅을 제공하는 보안 모듈이다. `/starter/studio-platform-starter-security` 스타터를 통해 자동 구성되며, 각 기능은 독립적인 퍼시스턴스 전략(JPA 또는 JDBC)을 선택할 수 있다.

### 제공 기능

- **JWT 인증** – 액세스/리프레시 토큰 발급과 검증, `/api/auth` 엔드포인트, 쿠키 기반 리프레시 토큰까지 포함한 토큰 프로바이더를 제공한다.【F:starter/studio-platform-starter-security/src/main/java/studio/one/platform/security/autoconfigure/JwtSecurtyAutoConfiguration.java†L52-L180】
- **리프레시 토큰 저장소** – `security.jwt.persistence`(기본값은 전역 `studio.persistence.type`)에 따라 JPA(`RefreshTokenJpaRepository`) 또는 JDBC(`RefreshTokenJdbcRepository`) 저장소를 자동 선택한다.【F:starter/studio-platform-starter-security/src/main/java/studio/one/platform/security/autoconfigure/JwtSecurtyAutoConfiguration.java†L132-L165】
- **계정 잠금(AccountLockService)** – 로그인 실패 누적 시 잠금/해제를 관리하며, `security.auth.lock.persistence` 설정으로 JPA/JDBC를 전환할 수 있다. JPA 사용 시 사용자 엔티티를 스캔할 수 있는 전역 JPA 설정이 반드시 필요하다.【F:starter/studio-platform-starter-security/src/main/java/studio/one/platform/security/autoconfigure/AccountLockAutoConfiguration.java†L36-L118】
- **로그인 실패 감사** – 실패 로그 저장, 실패/성공 이벤트 리스너, 보관(보존) 작업, 웹 조회용 `LoginFailureQueryService`/Controller 를 제공한다. `security.audit.login-failure.persistence` 로 저장소 방식을 선택한다.【F:starter/studio-platform-starter-security/src/main/java/studio/one/platform/security/autoconfigure/LoginFailureAuditAutoConfiguration.java†L1-L215】

### 사용 방법

1. **스타터 의존성 추가**

   ```kotlin
   dependencies {
       implementation(project(":starter:studio-platform-starter-security"))
   }
   ```

2. **기본 설정**

   ```yaml
   studio:
     persistence:
       type: jdbc # 전역 퍼시스턴스 기본값(jpa|jdbc)
     security:
       jwt:
         enabled: true
         secret: ${JWT_SECRET}
         issuer: studio-api
         persistence: jdbc # 선택: refresh token 저장 방식을 덮어쓰기
       auth:
         lock:
           enabled: true
           max-attempts: 5
           lock-duration: 15m
           persistence: jdbc # jpa 를 사용하려면 전역 persistence 도 jpa 여야 함
       audit:
         login-failure:
           enabled: true
           persistence: jdbc
           retention-days: 90
   ```

   `security.auth.lock.persistence` 를 `jpa` 로 설정할 때는 `studio.persistence.type` 또한 `jpa` 이어야 하며, 그렇지 않으면 자동 구성에서 오류를 발생시켜 개발자가 즉시 인지할 수 있도록 했다.【F:starter/studio-platform-starter-security/src/main/java/studio/one/platform/security/autoconfigure/AccountLockAutoConfiguration.java†L36-L118】

3. **엔드포인트 및 서비스 확장**
   - `/api/auth/login`, `/api/auth/refresh` 엔드포인트는 JWT 설정이 켜져 있을 때 자동으로 등록된다.【F:starter/studio-platform-starter-security/src/main/java/studio/one/platform/security/autoconfigure/JwtSecurtyAutoConfiguration.java†L102-L165】
   - `AccountLockService`, `LoginFailureQueryService`, `RefreshTokenStore` 는 빈 대체가 가능하므로, 필요 시 커스텀 구현을 동일한 인터페이스 이름으로 등록하면 자동으로 교체된다.

이렇게 하면 인증/토큰/로그 감사/계정 잠금 기능을 손쉽게 애플리케이션에 붙일 수 있으며, 각 기능의 저장소 방식을 속성만으로 독립적으로 제어할 수 있다.

## Create dababase (postgres)

```
-- 로그인 가능한 사용자 생성 + 비밀번호 지정
CREATE USER studioapi WITH PASSWORD 'studioapi';

-- 데이터베이스 생성 권한 부여
ALTER USER studioapi CREATEDB;

CREATE SCHEMA studioapi AUTHORIZATION studioapi;

GRANT ALL PRIVILEGES ON SCHEMA studioapi TO studioapi;

```
