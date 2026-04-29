# Configuration Namespace Guide

이 문서는 `studio-*` 모듈의 설정 네임스페이스 기준을 정리한다. 현재 기준은 3층 모델이다.

## 3층 모델

1. `spring.*`
- 외부 라이브러리와 공급자 SDK 설정을 둔다.
- 예: `spring.ai.openai.api-key`, `spring.ai.google.genai.chat.options.model`, `spring.ai.ollama.embedding.options.model`
- API key, model, base-url, dimensions 같은 runtime provider 옵션은 가능하면 이 층을 단일 소스로 사용한다.

2. `studio.features.<module>.*`
- feature wiring, exposure, enable/disable, persistence 선택, web endpoint 노출을 둔다.
- 예: `studio.features.user.enabled`, `studio.features.mail.enabled`, `studio.features.ai.enabled`
- 깊은 정책 값은 두지 않는다.

3. `studio.<module>.*`
- 모듈 runtime detail, policy, cache, routing, rag, storage 같은 내부 동작을 둔다.
- 예: `studio.user.password-policy.*`, `studio.attachment.storage.*`, `studio.thumbnail.*`, `studio.ai.routing.*`, `studio.ai.rag.*`

## 기본 원칙

- 같은 의미의 키는 한 층에만 둔다.
- 새 키가 생기면 문서와 metadata는 새 키를 기준으로 작성한다.
- 레거시 키는 migration window 동안만 fallback으로 유지한다.
- 가능한 기본 migration window는 1-2 release다.

## 각 층에 넣는 내용

### `spring.*`
- provider SDK 인증 정보
- provider SDK model, dimensions, base-url, task type
- 외부 라이브러리의 표준 속성

### `studio.features.<module>.*`
- `enabled`
- `persistence.*`
- `web.*`
- feature gate와 endpoint exposure

### `studio.<module>.*`
- policy
- cache
- storage
- routing
- rag
- provider registry metadata
- runtime behavior

## 예시

```yaml
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

studio:
  features:
    ai:
      enabled: true
    user:
      enabled: true
      web:
        enabled: true

  ai:
    routing:
      default-chat-provider: openai
      default-embedding-provider: openai
    providers:
      openai:
        type: OPENAI
        enabled: true
        chat:
          enabled: true
        embedding:
          enabled: true
    rag:
      default-embedding-profile: retrieval-ko

  user:
    password-policy:
      min-length: 12
      max-length: 64
      require-upper: true
      require-lower: true
      require-digit: true
      require-special: true
      allowed-specials: "!@#$%^&*"
      allow-whitespace: false
```

## 충돌과 우선순위

- 같은 의미의 값이 target key와 legacy key에 동시에 있으면 target key를 우선한다.
- legacy key는 동일 의미의 fallback 으로만 사용한다.
- legacy key를 읽으면 startup log에 deprecation warning을 남긴다.

## Migration Rule

1. target key를 먼저 추가한다.
2. legacy key는 fallback으로 1-2 release 유지한다.
3. deprecation warning을 추가한다.
4. migration window가 끝나면 legacy key를 제거한다.

## Legacy -> Target Mapping

| Legacy key | Target key | 상태 | 비고 |
| --- | --- | --- | --- |
| `studio.features.user.password-policy.*` | `studio.user.password-policy.*` | partial | user password policy는 target key를 기준으로 읽고 legacy fallback을 유지한다. |
| `studio.features.attachment.storage.*` | `studio.attachment.storage.*` | partial | attachment 저장소 runtime 설정 이동. |
| `studio.features.attachment.thumbnail.enabled/base-dir/ensure-dirs` | `studio.attachment.thumbnail.enabled/base-dir/ensure-dirs` | partial | attachment thumbnail 저장/cache 통합 설정 이동. |
| `studio.features.attachment.thumbnail.default-size/default-format` | `studio.thumbnail.default-size/default-format` | partial | thumbnail 생성 기본값은 platform thumbnail 서비스가 소유한다. |
| `studio.attachment.thumbnail.default-size/default-format` | `studio.thumbnail.default-size/default-format` | partial | attachment-local generation default는 platform thumbnail 설정으로 이동. |
| `studio.features.mail.imap.*` | `studio.mail.imap.*` | partial | IMAP runtime 설정 이동. |
| `studio.ai.enabled` | `studio.features.ai.enabled` | partial | AI feature gate. |
| `studio.ai.default-provider` | `studio.ai.routing.default-chat-provider` / `studio.ai.routing.default-embedding-provider` | partial | legacy fallback provider. |
| `studio.ai.default-chat-provider` | `studio.ai.routing.default-chat-provider` | partial | chat routing default. |
| `studio.ai.default-embedding-provider` | `studio.ai.routing.default-embedding-provider` | partial | embedding routing default. |
| `studio.ai.providers.<id>.api-key` | `spring.ai.*` | partial | provider SDK API key는 spring.ai.*로 옮긴다. |
| `studio.ai.providers.<id>.base-url` | `spring.ai.*` | partial | provider SDK base URL은 spring.ai.*로 옮긴다. |
| `studio.ai.providers.<id>.chat.model` | `spring.ai.*` | partial | chat model은 spring.ai.*를 canonical source로 둔다. |
| `studio.ai.providers.<id>.embedding.model` | `spring.ai.*` | partial | embedding model은 spring.ai.*를 canonical source로 둔다. |
| `studio.ai.pipeline.*` | `studio.ai.rag.*` | partial | RAG pipeline namespace를 rag namespace로 이동. |

## 현재 권장 네임스페이스

- `studio.features.user.enabled`
- `studio.features.user.persistence.*`
- `studio.features.user.web.*`
- `studio.user.password-policy.*`
- `studio.features.attachment.enabled`
- `studio.features.attachment.web.*`
- `studio.attachment.storage.*`
- `studio.attachment.thumbnail.enabled`
- `studio.attachment.thumbnail.base-dir`
- `studio.attachment.thumbnail.ensure-dirs`
- `studio.features.thumbnail.enabled`
- `studio.thumbnail.*`
- `studio.features.mail.enabled`
- `studio.features.mail.persistence.*`
- `studio.features.mail.web.*`
- `studio.mail.imap.*`
- `studio.features.ai.enabled`
- `studio.ai.routing.*`
- `studio.ai.providers.*`
- `studio.ai.rag.*`
- `spring.ai.*`

## 현재 코드와의 정합성

- `studio.features.user.enabled`, `studio.features.user.persistence.*`, `studio.features.user.web.*`는 유지한다.
- `studio.user.password-policy.*`가 user password policy의 target key다.
- `studio.attachment.storage.*`가 attachment binary storage target key다.
- `studio.attachment.thumbnail.enabled/base-dir/ensure-dirs`는 attachment thumbnail 저장/cache 통합 target key다.
- `studio.thumbnail.*`는 독립 thumbnail generation target key다.
- `studio.mail.imap.*`가 mail IMAP target key다.
- `studio.features.ai.enabled`가 AI feature gate다.
- `studio.ai.routing.*`가 AI default provider routing key다.
- provider SDK 값은 `spring.ai.*`를 우선한다.
- `studio.ai.rag.*`가 RAG runtime key다.

## 운영 메모

- `studio.ai.providers.<id>.*`는 provider registry와 fallback metadata를 함께 담는다.
- provider SDK 값은 가능하면 `spring.ai.*`에만 둔다.
- `studio.ai.pipeline.*`는 legacy RAG fallback으로만 해석한다.
- attachment/mail/user/thumbnail의 legacy namespace는 migration window 동안만 유지한다.
