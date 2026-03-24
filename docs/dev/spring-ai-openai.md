# Spring AI OpenAI

## 목적

OpenAI provider는 Spring AI 단일 경로로 동작하고, 런타임 설정은 `spring.ai.openai.*`가 소유한다.

## 설정 원칙

- OpenAI API key, base URL, chat model, embedding model은 `spring.ai.openai.*`가 소유한다.
- `studio.ai.default-provider`는 기본 provider 선택만 담당한다.
- OpenAI는 `studio.ai.providers.openai` 한 경로만 사용한다.
- LangChain OpenAI base path와 `openai-springai` alias는 더 이상 사용하지 않는다.

## 현재 권장 설정

```yaml
studio:
  ai:
    enabled: true
    default-provider: openai
    endpoints:
      enabled: true
      base-path: /api/ai
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
      chat:
        options:
          model: gpt-4o-mini
      embedding:
        options:
          model: text-embedding-3-small
```

## source of truth

- 권한/기능/라우팅:
  - `studio.ai.enabled`
  - `studio.ai.default-provider`
  - `studio.ai.endpoints.*`
- OpenAI runtime config:
  - `spring.ai.openai.api-key`
  - `spring.ai.openai.base-url`
  - `spring.ai.openai.chat.options.*`
  - `spring.ai.openai.embedding.options.*`

## deprecated / removed

다음 경로는 더 이상 사용하지 않는다.

- `studio.ai.spring-ai.enabled`
- `studio.ai.spring-ai.source-provider`
- `studio.ai.spring-ai.provider-suffix`
- `openai-springai` alias provider
- LangChain OpenAI base path
- `studio.ai.providers.openai.api-key`
- `studio.ai.providers.openai.base-url`
- `studio.ai.providers.openai.chat.model`
- `studio.ai.providers.openai.embedding.model`

## fail-fast expectations

- `studio.ai.default-provider`는 반드시 있어야 한다.
- enabled OPENAI provider는 정확히 하나만 허용한다.
- OPENAI provider가 켜져 있으면 `spring.ai.openai.api-key`가 반드시 있어야 한다.
- OPENAI chat이 켜져 있으면 `spring.ai.openai.chat.options.model`이 반드시 있어야 한다.
- OPENAI embedding이 켜져 있으면 `spring.ai.openai.embedding.options.model`이 반드시 있어야 한다.

## 검증

```bash
./gradlew -p /tmp/studio-api-spring-ai -PnimbusJoseJwtVersion=9.37.3 -PjsonSmartVersion=2.5.2 \
  :starter:studio-platform-starter-ai:test \
  --tests 'studio.one.platform.ai.autoconfigure.AiSecretPresenceGuardTest' \
  --tests 'studio.one.platform.ai.autoconfigure.config.OpenAiProviderAutoConfigurationTest' \
  --tests 'studio.one.platform.ai.autoconfigure.config.OpenAiSpringAiProviderRegistrationTest'
```
