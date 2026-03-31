# Changelog

## 2026-03-31 (follow-up)

### 변경됨
- `AttachmentServiceImpl`의 `InputStream` 기반 size 계산을 `available()`에서 임시 파일 버퍼링으로 바꿔 정확한 크기를 보장하도록 수정했다.
- `AttachmentServiceImpl`의 `File` 기반 업로드는 입력 스트림을 try-with-resources로 닫고, 너무 큰 파일은 명시적으로 실패하도록 정리했다.
- storage save 실패 시 partial binary만 best-effort로 정리하고, 메타데이터는 트랜잭션 rollback에 맡기도록 저장 경계를 명확히 했다.
- `attachment-service`에 `AttachmentServiceImpl`, `LocalFileStore`, `JpaFileStore` 회귀 테스트를 추가해 unknown-size stream 처리, explicit size 유지, filesystem/database 저장 경로를 검증했다.

### 검증
- `./gradlew :studio-application-modules:attachment-service:test --tests 'studio.one.application.attachment.service.AttachmentServiceImplTest' --tests 'studio.one.application.attachment.storage.LocalFileStoreTest' --tests 'studio.one.application.attachment.storage.JpaFileStoreTest'`
- `./gradlew :studio-application-modules:attachment-service:compileJava`

## 2026-03-31

### 변경됨
- `attachment-service`의 `AttachmentController`, `AttachmentMgmtController`, `MeAttachmentController`가 파일명 정제, MIME 정규화, 다운로드 헤더 구성을 공통 `AttachmentWebSupport`로 공유하도록 정리했다.
- `AttachmentMgmtController`의 관리자 판별이 `ADMIN`과 `ROLE_ADMIN`을 모두 허용하도록 보강해 Spring Security authority 표현 차이로 인한 owner 우회 오판정을 줄였다.
- `attachment-service`에 attachment 웹 helper 회귀 테스트를 추가하고, mgmt 권한 테스트가 `ROLE_ADMIN` 경로를 검증하도록 보강했다.
- `attachment-service`의 접근 제어 helper를 `AttachmentAccessSupport`로 분리해 principal 조회, 관리자 판별, owner 접근 검사를 컨트롤러에서 공통으로 사용하도록 정리했다.

### 검증
- `./gradlew :studio-application-modules:attachment-service:test --tests 'studio.one.application.web.controller.AttachmentAccessSupportTest' --tests 'studio.one.application.web.controller.AttachmentControllerTest' --tests 'studio.one.application.web.controller.AttachmentMgmtControllerAuthorizationTest' --tests 'studio.one.application.web.controller.AttachmentWebSupportTest' --tests 'studio.one.application.web.controller.MeAttachmentControllerTest'`
- `./gradlew :studio-application-modules:attachment-service:compileJava`

## 2026-03-30

### 변경됨
- `PropertyValidator`가 점(`.`)과 하이픈(`-`)이 포함된 민감 프로퍼티 키도 감지하도록 수정했다.
- `RepositoryImpl`에 경로 탐색 방어를 추가하고, startup refresh 이벤트가 즉시 `UnsupportedOperationException`으로 실패하지 않도록 정리했다.
- `GlobalExceptionHandler`가 unsupported method/media type 예외를 각각 405/415로 응답하도록 수정했다.
- `JasyptHttpController`의 토큰 검증을 상수 시간 비교로 바꾸고, `JasyptProperties`에 암호화 비밀번호 최소 길이 검증을 추가했다.
- `studio-platform`과 `starter-jasypt`에 회귀 테스트를 추가하고, 테스트용 웹 의존성을 보강했다.

### 검증
- `./gradlew :studio-platform:test --tests 'studio.one.platform.component.PropertyValidatorTest' --tests 'studio.one.platform.component.RepositoryImplTest' --tests 'studio.one.platform.web.advice.GlobalExceptionHandlerTest'`
- `./gradlew :starter:studio-platform-starter-jasypt:test --tests 'studio.one.platform.autoconfigure.jasypt.JasyptHttpControllerTest' --tests 'studio.one.platform.autoconfigure.jasypt.JasyptPropertiesTest'`

## 2026-03-30 (follow-up)

### 변경됨
- `studio-platform-autoconfigure`의 `CompositeAuditorAware`가 외부에서 주입한 `AuditorAware`를 우선 처리할 수 있도록 확장했다.
- `CompositeAuditorAware`의 기존 security/header/fixed 기본 합성 동작은 유지했다.
- `studio-platform-autoconfigure`에 `CompositeAuditorAware` 회귀 테스트와 테스트용 `spring-data-commons` 의존성을 추가했다.
- 루트 OWASP dependency-check의 `failBuildOnCVSS`를 `7.0F`로 낮춰 High 이상 취약점에서 빌드가 실패하도록 조정했다.
- `studio-platform-data`에 `PaginationDialect` 회귀 테스트를 추가했다.
- `studio-platform`에 `DomainPolicyRegistryImpl` 병합/정규화 회귀 테스트를 추가하고, contributor 병합 시 불변 맵을 다시 수정하던 경로를 안전하게 고쳤다.
- `starter`의 `perisitence`와 `studio-platform-autoconfigure`의 `perisistence` 오타 패키지에 대응해 정상 패키지명 `persistence` 경로를 추가하고, 기존 경로는 deprecated 호환 브리지로 유지했다.
- Spring Boot auto-configuration 등록 경로를 `persistence` 패키지로 전환했다.
- `studio-platform-identity` 계약에 principal/resolver 규약과 `UserDto` 용도를 문서화하고, identity service bean 이름 상수를 별도 상수 클래스로 분리했다.
- `studio-platform-identity`를 순수 계약 모듈로 유지하도록 Spring Boot 플러그인을 제거했다.

### 검증
- `./gradlew :studio-platform-autoconfigure:test --tests 'studio.one.platform.autoconfigure.perisistence.jpa.auditor.CompositeAuditorAwareTest'`
- `./gradlew :studio-platform:test --tests 'studio.one.platform.security.authz.DomainPolicyRegistryImplTest'`
- `./gradlew :studio-platform-data:test --tests 'studio.one.platform.data.jdbc.pagination.PaginationDialectTest'`
- `./gradlew :studio-platform-autoconfigure:test --tests 'studio.one.platform.autoconfigure.persistence.jpa.auditor.CompositeAuditorAwareTest'`
- `./gradlew :starter:studio-platform-starter:compileJava`
- `./gradlew :studio-platform-identity:test`
- `./gradlew :studio-platform-identity:build`

## 2026-03-31

### 변경됨
- `studio-platform-objecttype`의 `ObjectTypeRuntimeService`와 `ObjectTypeAdminService`가 `web.dto` 대신 서비스 전용 command/result 타입을 사용하도록 정리했다.
- `ObjectTypeController`와 `ObjectTypeMgmtController`가 서비스 모델을 기존 웹 DTO로 매핑하도록 책임을 이동해 HTTP 응답 형식은 유지했다.
- `attachment-service`의 objecttype 업로드 정책 검증 호출이 서비스 전용 `ValidateUploadCommand`를 사용하도록 변경했다.
- `studio-platform-objecttype`에 runtime 성공 경로와 controller 매핑 회귀 테스트를 추가했다.

### 검증
- `./gradlew :studio-platform-objecttype:test --tests 'studio.one.platform.objecttype.ObjectTypeRuntimeServiceTest' --tests 'studio.one.platform.objecttype.ObjectTypeControllerTest' --tests 'studio.one.platform.objecttype.ObjectTypeMgmtControllerTest'`
- `./gradlew :studio-application-modules:attachment-service:test --tests 'studio.one.application.attachment.service.AttachmentServiceImplTest' --tests 'studio.one.application.web.controller.AttachmentMgmtControllerAuthorizationTest'`
- `./gradlew :studio-application-modules:attachment-service:compileJava :starter:studio-platform-starter-objecttype:compileJava`

## 2026-03-26

### 변경됨
- `studio-platform-starter-ai`의 provider 의존성(OpenAI, Google GenAI, Ollama)을 `implementation`에서 `compileOnly`로 전환했다. 소비 애플리케이션이 필요한 provider 라이브러리를 직접 선언해야 한다.
- Spring AI BOM을 `api(platform(...))`으로 노출하여 소비 앱이 별도 BOM 선언 없이 Spring AI 버전을 일관되게 관리할 수 있도록 했다.
- `ProviderChatPortFactory` / `ProviderEmbeddingPortFactory` 인터페이스와 provider별 `@Configuration` 구현체를 도입했다. 각 구현체는 `@ConditionalOnClass`로 보호되어, provider 라이브러리가 classpath에 있을 때만 해당 factory가 등록된다.
- `ProviderChatConfiguration` / `ProviderEmbeddingConfiguration`의 switch 기반 직접 참조를 제거하고, 등록된 factory를 수집하는 방식으로 교체했다. factory가 없는 provider는 조용히 제외된다.
- `AiSecretPresenceGuard`에서 `ChatModel` / `EmbeddingModel` bean 주입을 제거했다. property 기반 검증만 유지한다.
- `AiProviderRegistryConfiguration`에 fail-fast guard를 추가했다. `studio.ai.default-provider`에 지정된 provider가 chat port와 embedding port 모두에 없으면 시작 시점에 명확한 오류로 실패한다.

### 사용 방법 (OpenAI 예시)
```kotlin
// build.gradle.kts
implementation("studio-platform-starter-ai")
implementation("org.springframework.ai:spring-ai-starter-model-openai")
```
```yaml
# application.yml
studio:
  ai:
    enabled: true
    default-provider: openai
    providers:
      openai:
        type: OPENAI
        chat:
          enabled: true
        embedding:
          enabled: true
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat.options.model: gpt-4o-mini
      embedding.options.model: text-embedding-3-small
```

### 검증
- `./gradlew :starter:studio-platform-starter-ai:build`

## 2026-03-23

### 변경됨
- JWT refresh cookie가 설정된 cookie name/path/SameSite/Secure 값을 따르도록 수정했다.
- realtime STOMP 기본값을 same-origin, JWT 요구, 익명 연결 거부 방향으로 강화했다.
- 파일 텍스트 추출에 기본 10MB 상한을 추가해 과도한 메모리 적재를 막았다.
- realtime JWT 의존성 누락을 startup 단계에서 fail-fast 처리하고, text extraction 상한 설정을 바인딩 단계에서 검증하도록 보강했다.

### 검증
- `./gradlew -PnimbusJoseJwtVersion=9.37.3 -PjsonSmartVersion=2.5.2 :studio-platform-security:test --tests 'studio.one.base.security.web.controller.JwtCookieSettingsTest' :studio-platform-realtime:test --tests 'studio.one.platform.realtime.stomp.config.RealtimeStompPropertiesTest' :studio-platform-data:test --tests 'studio.one.platform.text.service.FileContentExtractionServiceTest' ':studio-application-modules:attachment-service:test' --tests 'studio.one.application.web.controller.AttachmentMgmtControllerAuthorizationTest' :starter:studio-platform-starter-realtime:compileJava`

## 2026-03-24

### 변경됨
- `starter:studio-platform-starter-ai`에 Spring AI OpenAI starter 기반 스파이크를 추가하고, OpenAI 직접 모델 생성 대신 Spring AI auto-configuration bean을 alias port에 연결하도록 정리했다.
- `studio.ai.spring-ai.source-provider`와 fail-fast guard를 추가해 Spring AI alias가 명시된 OpenAI provider와 `spring.ai.openai.*` 설정을 사용하도록 고정했다.
- source provider로 지정된 OpenAI의 LangChain base 경로도 `spring.ai.openai.*`를 사용하도록 바꿔, OpenAI runtime 설정의 단일 소스를 유지한 채 LangChain/Spring AI 비교가 가능하게 했다.
- `openai-springai` default cutover 검증을 위해 `AiInfoController`, `ChatController`, `EmbeddingController` smoke 테스트를 추가했다.
- `studio.ai.default-provider`를 비웠을 때 Spring AI alias를 기본 provider로 승격하고, `default-provider=openai`를 명시하면 LangChain base provider로 rollback할 수 있게 정리했다.
- OpenAI provider를 Spring AI 단일 경로로 정리하고, `openai-springai` alias 및 LangChain OpenAI base path 제거 방향을 [spring-ai-openai.md](/Users/donghyuck.son/git/studio-api/docs/dev/spring-ai-openai.md)에 문서화했다.

### 검증
- `./gradlew -PnimbusJoseJwtVersion=9.37.3 -PjsonSmartVersion=2.5.2 :starter:studio-platform-starter-ai:test --tests 'studio.one.platform.ai.autoconfigure.AiSecretPresenceGuardTest' --tests 'studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapterTest' --tests 'studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapterTest' --tests 'studio.one.platform.ai.autoconfigure.config.SpringAiAliasProviderRegistrationTest' --tests 'studio.one.platform.ai.autoconfigure.config.SpringAiAliasProviderAutoConfigurationTest'`
# 2026-03-24

- refactor(ai): start splitting AI HTTP endpoints into a dedicated web starter module.
- refactor(ai): remove remaining core bean stereotypes so AI service ownership stays in starter auto-configuration.
- refactor(ai): narrow AI dependency ownership so web/security concerns stay with the web starter boundary.
- refactor(ai): replace AI starter component scanning with explicit auto-configuration bean registration.
- refactor(ai): prune unused compileOnly Spring starter dependencies from `studio-platform-starter-ai`.
- refactor(ai): remove LangChain4j `TokenUsage` coupling from ai-web starter and normalize chat `tokenUsage` metadata shape.
- refactor(ai): migrate Ollama embedding wiring from LangChain4j to Spring AI and validate `spring.ai.ollama.embedding.options.model` at startup.
- refactor(ai): migrate Google embedding wiring from LangChain4j to Spring AI and validate `spring.ai.google.genai.embedding.*` at startup.
- refactor(ai): preserve Google embedding `taskType` during the Spring AI migration; `titleMetadataKey` remains inactive because the current embedding request model carries text only.
- refactor(ai): remove the remaining LangChain4j embedding adapter and dead embedding wiring, keeping LangChain4j only for the Google chat path.
- refactor(ai): migrate Google chat wiring from LangChain4j to Spring AI and remove the remaining LangChain4j chat adapter path.
- fix(ai): preserve custom Google chat base URL when building the Spring AI Google GenAI client.
- refactor(ai): rename provider wiring configurations to neutral names after removing LangChain4j runtime paths.
