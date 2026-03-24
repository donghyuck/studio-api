# Spring AI OpenAI Phase 1

## 목적

OpenAI 경로의 런타임 설정 소유권을 `studio.ai.*`에서 `spring.ai.*`로 옮기고, `studio.ai.spring-ai.*`는 전환 기간의 alias 제어 용도로만 유지한다.

## 설정 원칙

- OpenAI API key, base URL, chat model, embedding model은 `spring.ai.*`가 소유한다.
- `studio.ai.*`는 애플리케이션 기능/라우팅/기본 provider 선택만 담당한다.
- `studio.ai.spring-ai.enabled`는 Spring AI alias 등록을 켜는 임시 migration toggle이다.
- `studio.ai.spring-ai.provider-suffix`는 임시 alias suffix이다.
- `studio.ai.spring-ai.source-provider`는 Spring AI alias의 기준 provider를 지정한다.
- source provider로 지정된 OpenAI의 LangChain base 경로도 `spring.ai.openai.*`를 읽는다.
- `studio.ai.default-provider`를 비우면 Spring AI alias(`source-provider + provider-suffix`)가 기본 provider로 승격된다.

## 현재 권장 설정

```yaml
studio:
  ai:
    enabled: true
    default-provider: openai-springai
    spring-ai:
      enabled: true
      source-provider: openai
      provider-suffix: -springai
    endpoints:
      enabled: true
      base-path: /api/ai
    providers:
      openai:
        type: OPENAI
        enabled: true
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
- Spring AI alias migration control:
  - `studio.ai.spring-ai.enabled`
  - `studio.ai.spring-ai.source-provider`
  - `studio.ai.spring-ai.provider-suffix`
- OpenAI runtime config:
  - `spring.ai.openai.api-key`
  - `spring.ai.openai.base-url`
  - `spring.ai.openai.chat.options.*`
  - `spring.ai.openai.embedding.options.*`

## deprecated direction

OpenAI 경로에서는 아래 설정을 더 이상 source of truth로 사용하지 않는다.

- `studio.ai.providers.openai.api-key`
- `studio.ai.providers.openai.base-url`
- `studio.ai.providers.openai.chat.model`
- `studio.ai.providers.openai.embedding.model`

이 값들은 phase 1 동안 LangChain 경로 또는 호환성 유지 때문에 일부 남아 있을 수 있으나, 최종 목표는 제거 또는 deprecated 처리다.

source provider로 지정된 OpenAI는 base provider(`openai`)와 alias provider(`openai-springai`)가 모두 같은 `spring.ai.openai.*` 값을 읽고, 구현체만 LangChain/Spring AI로 나뉜다.

## fail-fast expectations

- `studio.ai.spring-ai.enabled=true`이면 `spring.ai.openai.api-key`가 반드시 있어야 한다.
- `studio.ai.spring-ai.source-provider`는 존재하는 `OPENAI` provider를 가리켜야 한다.
- 기본 provider가 `openai-springai`인데 chat 또는 embedding alias가 없으면 startup이 실패해야 한다.
- `studio.ai.default-provider`가 비어 있고 Spring AI alias promotion 조건도 없으면 startup이 실패해야 한다.

## 검증

```bash
./gradlew -PnimbusJoseJwtVersion=9.37.3 -PjsonSmartVersion=2.5.2 \
  :starter:studio-platform-starter-ai:test \
  --tests 'studio.one.platform.ai.autoconfigure.AiSecretPresenceGuardTest' \
  --tests 'studio.one.platform.ai.autoconfigure.config.SpringAiAliasProviderRegistrationTest' \
  --tests 'studio.one.platform.ai.autoconfigure.config.SpringAiAliasProviderAutoConfigurationTest'
```
