# Changelog

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
- OpenAI phase 1 운영 설정 원칙을 [spring-ai-openai-phase1.md](/Users/donghyuck.son/git/studio-api/docs/dev/spring-ai-openai-phase1.md)에 문서화했다.

### 검증
- `./gradlew -PnimbusJoseJwtVersion=9.37.3 -PjsonSmartVersion=2.5.2 :starter:studio-platform-starter-ai:test --tests 'studio.one.platform.ai.autoconfigure.AiSecretPresenceGuardTest' --tests 'studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapterTest' --tests 'studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapterTest' --tests 'studio.one.platform.ai.autoconfigure.config.SpringAiAliasProviderRegistrationTest' --tests 'studio.one.platform.ai.autoconfigure.config.SpringAiAliasProviderAutoConfigurationTest'`
