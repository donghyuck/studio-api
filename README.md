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

모든 엔드포인트는 `studio.features.user.web.base-path` (기본값 `/api/mgmt`) 하위에 노출되며, `@PreAuthorize` 가 붙은 세부 권한 검사를 통해 역할 기반 접근 제어를 적용한다.【F:studio-platform-user/src/main/java/studio/one/base/user/web/controller/UserController.java†L54-L160】【F:starter/studio-platform-starter-user/src/main/java/studio/one/platform/user/autoconfigure/WebProperties.java†L18-L59】

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

3. **엔드포인트 구성** – 각 도메인 엔드포인트는 `studio.features.user.web.endpoints.<user|group|role|company>` 설정으로 개별 활성화 여부만 제어하며, 세부 권한 정책은 `studio.security.acl` 설정이나 DB 기반 ACL 정책으로 관리한다.【F:starter/studio-platform-starter-user/src/main/java/studio/one/platform/user/autoconfigure/WebProperties.java†L18-L59】【F:starter/studio-platform-starter-security/src/main/java/studio/one/platform/security/autoconfigure/SecurityAclAutoConfiguration.java†L30-L48】【F:starter/studio-platform-starter-security-acl/src/main/java/studio/one/platform/security/acl/autoconfigure/SecurityAclDatabaseAutoConfiguration.java†L135-L177】

4. **도메인 서비스 확장** – `ApplicationUserService`, `ApplicationGroupService`, `ApplicationRoleService`, `ApplicationCompanyService` 인터페이스와 기본 구현이 빈으로 등록되므로, 커스터마이징이 필요한 경우 동일한 인터페이스를 구현한 빈을 제공하여 교체할 수 있다.【F:studio-platform-user/src/main/java/studio/one/base/user/service/ApplicationUserService.java†L1-L82】【F:studio-platform-user/src/main/java/studio/one/base/user/service/impl/ApplicationUserServiceImpl.java†L1-L200】【F:starter/studio-platform-starter-user/src/main/java/studio/one/platform/user/autoconfigure/UserServicesAutoConfiguration.java†L1-L120】

위 설정만으로 사용자/권한 관리를 위한 REST API, 서비스, 도메인 이벤트 퍼블리셔가 통합되며, 필요 시 MapStruct 기반 DTO 매퍼 빈도 자동으로 주입된다.【F:starter/studio-platform-starter-user/src/main/java/studio/one/platform/user/autoconfigure/UserEndpointsAutoConfiguration.java†L53-L106】【F:studio-platform-user/build.gradle.kts†L1-L24】

## studio-platform-ai 모듈

`studio-platform-ai` 모듈은 LangChain4j 기반 OpenAI/Gemini 모델을 감싸는 도메인 포트, DTO, REST 컨트롤러를 묶어서 API로 노출하는 역할을 한다. `/api/ai/chat`, `/api/ai/embedding`, `/api/ai/vectors` 엔드포인트를 별도 컨트롤러로 분리하여 메시지 ↔ 도메인 변환을 담당하며, API 사용자에게는 `ApiResponse` 포맷으로 일관된 응답을 제공한다.【F:studio-platform-ai/src/main/java/studio/one/platform/ai/web/controller/ChatController.java#L1-L85】【F:studio-platform-ai/src/main/java/studio/one/platform/ai/web/controller/EmbeddingController.java#L1-L45】【F:studio-platform-ai/src/main/java/studio/one/platform/ai/web/controller/VectorController.java#L1-L103】
Auto-configuration, property binding, and the LangChain client wiring are hosted in the `studio-platform-starter-ai` module, so you only need to depend on the starter to enable them (`starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/autoconfigure/AiAutoConfiguration.java`).【F:starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/adapters/config/AiAdapterProperties.java#L1-L104】【F:starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/adapters/config/LangChainEmbeddingConfiguration.java#L1-L73】

### 제공 기능

- **Chat** – `ChatController`가 `ChatRequestDto`/`ChatResponseDto`를 받아 내부 `ChatPort`를 통해 LangChain4j `ChatModel`과 통신하며, `LangChainChatAdapter`가 DTO를 LangChain 메시지로 변환한다.【F:studio-platform-ai/src/main/java/studio/one/platform/ai/adapters/chat/LangChainChatAdapter.java#L1-L89】【F:studio-platform-ai/src/main/java/studio/one/platform/ai/web/controller/ChatController.java#L1-L85】
- **Embedding** – `EmbeddingController`와 `LangChainEmbeddingAdapter`는 텍스트를 `TextSegment`로 변환하고 LangChain에서 반환한 `Response<List<Embedding>>`를 도메인 `EmbeddingVector`로 변환해 `EmbeddingPort`를 구현한다.【F:studio-platform-ai/src/main/java/studio/one/platform/ai/adapters/embedding/LangChainEmbeddingAdapter.java#L1-L47】【F:studio-platform-ai/src/main/java/studio/one/platform/ai/web/controller/EmbeddingController.java#L1-L45】
- **Vector** – `/api/ai/vectors` 컨트롤러는 `VectorStorePort`를 통해 벡터 문서를 upsert하거나 검색하며, 벡터 검색 시 텍스트 쿼리를 자동으로 임베딩하여 사용한다.【F:studio-platform-ai/src/main/java/studio/one/platform/ai/web/controller/VectorController.java#L1-L103】

### 사용 방법

1. **AI 설정** – `application.yml`의 `ai.provider`를 `openai`, `ollama`, `google-ai-gemini` 중 하나로 설정하고, `AiAdapterProperties`를 통해 API 키/모델/베이스 URL을 주입한다.
2. **LangChain 의존성** – `studio-platform-ai` 모듈은 LangChain4j 본체와 각 모델별 어댑터(예: `langchain4j-open-ai`, `langchain4j-google-ai-gemini`)를 Gradle 의존성으로 추가한다. 새로운 모델을 줄 경우 `LangChainChatConfiguration`과 `LangChainEmbeddingConfiguration`에서 빈을 확장하면 된다.
3. **벡터 저장소** – PostgreSQL 기반 PGVector 어댑터(`PgVectorStoreAdapter`)를 함께 등록하면 `VectorController`가 벡터 검색/업서트를 처리하며, `VectorStorePort`가 없으면 `/api/ai/vectors` 호출 시 503을 반환한다.【F:studio-platform-ai/src/main/java/studio/one/platform/ai/adapters/vector/PgVectorStoreAdapter.java#L1-L146】【F:studio-platform-ai/src/main/java/studio/one/platform/ai/web/controller/VectorController.java#L32-L101】

### studio.ai 설정

AI 관련 설정은 이제 `studio.ai` 접두어로 이루어지며, 각 프로바이더별로 별도 옵션을 정의할 수 있다. 예를 들어 OpenAI 채팅/임베딩과 Ollama 임베딩 옵션을 동시에 다루려면 다음과 같이 작성한다.

```yaml
studio:
  ai:
    default-provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        enabled: true
        options:
          model: gpt-4o-mini
      embedding:
        enabled: true
        options:
          model: text-embedding-3-small
    ollama:
      base-url: http://ollama.internal:11434
      embedding:
        enabled: true
        options:
          model: bge-m3
```

이처럼 여러 프로바이더를 동시에 설정하고 `AiProviderRegistry`를 통해 필요에 따라 프로그램에서 선택할 수도 있다.【F:studio-platform-ai/src/main/java/studio/one/platform/ai/core/registry/AiProviderRegistry.java#L1-L44】

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

### ACL 기본 정책 시딩

`studio-platform-security-acl` 스타터는 `studio.security.acl.defaults` 설정을 통해 `tb_application_role` 등 기존 역할 테이블에 정의된 `ROLE_*` 정보를 ACL 테이블( `acl_sid`, `acl_class`, `acl_object_identity`, `acl_entry` )로 쉽게 전파할 수 있습니다. `AclPolicySeeder`는 `DefaultAclPolicyProperties`로 선언된 도메인/컴포넌트별 정책을 읽어서 필요한 행을 `INSERT ... ON CONFLICT` 방식으로 적재하고, 이후 `DatabaseAclDomainPolicyContributor`가 해당 정보를 읽어 `@endpointAuthz`로 노출합니다. 예:

```yaml
studio:
  security:
    acl:
      defaults:
        enabled: true
        policies:
          - domain: user
            roles:
              - role: ROLE_ADMIN
                actions: [READ, WRITE, ADMIN]
              - role: ROLE_MANAGER
                actions: [READ]
          - domain: group
            component: members
            roles:
              - role: ROLE_ADMIN
                actions: [READ, WRITE]
```

`domain`/`component` 이름은 자동으로 소문자·하이픈으로 정규화되고, `actions`에는 `READ`, `WRITE`, `ADMIN` 중 하나 이상을 지정해 ACL 마스크를 생성합니다. `ROLE_*` 이름은 `studio-platform-user` 모듈에서 이미 관리중인 역할과 일치시킬 수 있으므로, 기존 역할/그룹 기능을 활용하면서 ACL 기반 권한 평가를 쉽게 연결할 수 있습니다.

### ACL Web API

`studio.security.acl.web.enabled=true`로 설정하면 기본 경로 `/api/mgmt/acl` 아래에 다음 엔드포인트가 자동으로 공개됩니다.

```
GET  /api/mgmt/acl/defaults       → 현재 등록된 service/policy descriptor 목록
POST /api/mgmt/acl/sync           → body로 단일 descriptor를 받아 즉시 ACL 테이블에 동기화
POST /api/mgmt/acl/sync/defaults  → YAML/프로퍼티로 정의한 defaults 전체를 다시 동기화
```

`studio.security.acl.web.base-path` 값을 변경하면 해당 경로 아래로 API가 이동하고, `AclPolicySynchronizationService`를 직접 주입하거나 `AclPolicySyncEvent`를 publish하여 추가적인 배치/이벤트 핸들링을 만들 수 있습니다. 이벤트 핸들러는 `studio.security.acl.sync.enabled=false`로 끌 수 있어 컨트롤러로만 수동 처리하는 구성도 가능합니다.

`studio.security.acl.admin.enabled=true`로 설정하면 `/api/mgmt/acl/admin` (기본값, `studio.security.acl.admin.base-path`로 조절) 아래에 `acl_class`, `acl_sid`, `acl_object_identity`, `acl_entry`를 위한 CRUD 엔드포인트가 등록되어 Vue 같은 관리 UI가 해당 테이블을 직접 조회·삽입할 수 있습니다. `AclAdministrationService`를 사용하면 필요한 요청과 바꾸어 락 없이 상태를 갱신할 수 있습니다.

`AclPolicySynchronizationService`를 `@Autowired` 받아 `AclPolicyDescriptor`를 직접 전달하거나, `ApplicationEventPublisher`/`DomainEvents`로 `AclPolicySyncEvent`를 발생시켜 배치·이벤트 기반으로 동기화할 수 있습니다. `studio.security.acl.sync.enabled=false` 로 설정하면 이벤트 리스너는 비활성화되므로 배치 서비스나 커스텀 핸들러만 동작하게 됩니다. 이는 새 그룹/롤이 등록되었을 때 ACL 테이블을 강제로 최신 상태로 만들거나, 주기적으로 검증하는 배치/스케줄러에서 유용합니다.

## Create dababase (postgres)

```
-- 로그인 가능한 사용자 생성 + 비밀번호 지정
CREATE USER studioapi WITH PASSWORD 'studioapi';

-- 데이터베이스 생성 권한 부여
ALTER USER studioapi CREATEDB;

CREATE SCHEMA studioapi AUTHORIZATION studioapi;

GRANT ALL PRIVILEGES ON SCHEMA studioapi TO studioapi;

```
