# OpenAI provider configuration for studio-api 1.x

## 목적

1.x OpenAI provider는 LangChain4j 직접 adapter로 동작한다. Runtime canonical 설정은 `studio.ai.providers.openai.*`이며, 기존 `spring.ai.openai.*`는 migration fallback으로만 읽는다.

## 설정 원칙

- OpenAI API key, base URL, chat model, embedding model, embedding dimension은 `studio.ai.providers.openai.*`가 소유한다.
- `studio.ai.routing.default-chat-provider`와 `studio.ai.routing.default-embedding-provider`가 기본 provider 선택을 담당한다.
- legacy `studio.ai.default-provider`는 1.x 호환 fallback으로만 유지한다.
- `spring.ai.openai.*`는 기존 운영 yml 호환 fallback이다.
- `langchain4j.*` namespace는 운영 설정으로 노출하지 않는다.

## 권장 설정

```yaml
studio:
  features:
    ai:
      enabled: true
  ai:
    routing:
      default-chat-provider: openai
      default-embedding-provider: openai
    providers:
      openai:
        type: OPENAI
        enabled: true
        api-key: ${OPENAI_API_KEY}
        base-url: https://api.openai.com/v1
        chat:
          enabled: true
          model: gpt-4o-mini
        embedding:
          enabled: true
          model: text-embedding-3-small
          dimension: 1536
```

## Legacy fallback

기존 2.x 운영 설정과의 호환을 위해 아래 `spring.ai.openai.*` 값은 fallback으로 읽는다.

| Legacy key | Canonical key |
|---|---|
| `spring.ai.openai.api-key` | `studio.ai.providers.openai.api-key` |
| `spring.ai.openai.base-url` | `studio.ai.providers.openai.base-url` |
| `spring.ai.openai.chat.options.model` | `studio.ai.providers.openai.chat.model` |
| `spring.ai.openai.embedding.options.model` | `studio.ai.providers.openai.embedding.model` |
| `spring.ai.openai.embedding.options.dimensions` | `studio.ai.providers.openai.embedding.dimension` |
