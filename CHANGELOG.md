# Changelog

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
