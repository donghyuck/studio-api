# 다중 embedding model adapter 설계

이 문서는 같은 Studio 서버에서 여러 embedding model을 동시에 운영하고, RAG 요청별로 선택하기 위한 구조를 정의한다.
현재 core는 provider-neutral 계약만 제공하고, 실제 provider SDK 호출은 starter/adapter가 담당한다.

## 현재 구조

- `studio-platform-ai`
  - `EmbeddingPort`: text list를 embedding vector로 변환하는 core port
  - `EmbeddingRequest`: optional `provider`, `model`, `inputType`, `metadata`를 담는 request 계약
  - `AiProviderRegistry`: provider id별 `ChatPort` / `EmbeddingPort` map을 보관
  - `RagEmbeddingProfile`: RAG profile id, provider, model, dimension, input type 지원 범위를 표현
- `starter/studio-platform-starter-ai`
  - `ProviderEmbeddingConfiguration`: `studio.ai.providers.<id>`마다 `EmbeddingPort`를 생성
  - `ProviderEmbeddingPortFactory`: provider type별 `EmbeddingPort` 생성 확장점
  - `SpringAiEmbeddingAdapter`: 생성 시점에 주입된 Spring AI `EmbeddingModel` 하나를 호출
  - `DefaultRagEmbeddingProfileResolver`: profile/request를 `EmbeddingPort`와 metadata로 해석

현재 구조에서 `EmbeddingRequest.model()`은 metadata/filter 계약으로 전달되지만,
Spring AI `EmbeddingModel` 자체가 단일 model로 생성되어 있으므로 같은 provider 안에서 request마다 다른 model을
실행하지는 못한다. 이 때문에 현재 adapter는 request model이 실제 구성 model과 다르면 거부해야 한다.

## 목표

- 같은 provider type 안에서도 여러 embedding model을 동시에 등록할 수 있어야 한다.
- RAG profile이 실제 `EmbeddingPort` 선택으로 이어져야 한다.
- core는 provider-specific SDK와 DB-specific 구현을 알지 않아야 한다.
- 기존 single provider/single model 설정은 그대로 동작해야 한다.

## 용어

| 용어 | 의미 |
|---|---|
| provider type | `OPENAI`, `GOOGLE_AI_GEMINI`, `OLLAMA` 같은 adapter 종류 |
| provider id | `studio.ai.providers.<id>`의 key. registry lookup key다. |
| embedding model | provider SDK가 실제 호출하는 model name |
| embedding profile | RAG에서 색인/검색 시 선택하는 logical profile id |
| embedding port id | 여러 model port를 구분하는 registry key. 1차 구현에서는 provider id와 동일하게 둔다. |

## 설계 방향

### 1단계: provider id를 model별로 분리

가장 작은 확장 방식은 provider id를 model별로 분리하는 것이다.
현재 auto-config는 `defaultEmbeddingPort`를 `AiProviderRegistry.embeddingPort(null)`로 만들고,
`null` lookup은 `studio.ai.default-provider`를 사용한다. 따라서 이 단계에서는 `default-provider`가
embedding-capable provider여야 한다. chat default와 embedding default를 분리하려면 별도 선행 변경이 필요하다.

```yaml
studio:
  ai:
    default-provider: gemini-embed-768
    providers:
      gemini-chat:
        type: GOOGLE_AI_GEMINI
        enabled: true
        chat:
          enabled: true
      gemini-embed-768:
        type: GOOGLE_AI_GEMINI
        enabled: true
        embedding:
          enabled: true
        google-embedding:
          task-type: RETRIEVAL_DOCUMENT
      gemini-embed-3072:
        type: GOOGLE_AI_GEMINI
        enabled: true
        embedding:
          enabled: true
        google-embedding:
          task-type: RETRIEVAL_DOCUMENT
    rag:
      embedding-profiles:
        retrieval-ko:
          provider: gemini-embed-768
          model: gemini-embedding-001
          dimension: 768
        retrieval-large:
          provider: gemini-embed-3072
          model: gemini-embedding-001
          dimension: 3072
```

이 방식은 core 변경 없이 `AiProviderRegistry.embeddingPort(provider)`를 그대로 사용한다.
단, 현재 Spring AI property namespace는 provider id별로 분리되어 있지 않으므로 provider별 runtime model/options를
주입하는 factory 보강이 필요하다. 특히 OpenAI는 현재 Spring AI가 만든 singleton `EmbeddingModel` bean을
재사용하므로 provider id를 나눠도 실제 model이 나뉘지 않는다.

### 2단계: provider별 embedding model map 지원

설정 표현을 줄이려면 `studio.ai.providers.<id>.embedding.models.<model-id>` 형태를 추가할 수 있다.

```yaml
studio:
  ai:
    providers:
      gemini:
        type: GOOGLE_AI_GEMINI
        embedding:
          enabled: true
          models:
            retrieval-768:
              model: gemini-embedding-001
              dimension: 768
            retrieval-3072:
              model: gemini-embedding-001
              dimension: 3072
```

이 단계에서는 `ProviderEmbeddingConfiguration`이 `providerId:modelId` 형태의 port id로 여러 `EmbeddingPort`를 등록한다.
`AiProviderRegistry.availableEmbeddingPorts()`는 이미 있으므로 registry 목록 조회 계약은 유지한다.
신규 lookup 계약으로는 아래 additive API를 고려한다.

```java
EmbeddingPort embeddingPort(String provider, String model);
```

기존 `embeddingPort(String provider)`는 default model port를 반환하는 wrapper로 유지한다.

### 3단계: routing EmbeddingPort

더 많은 provider가 request-time options를 안정적으로 지원하면 `RoutingEmbeddingPort`를 둘 수 있다.
이 port는 `EmbeddingRequest.model()`을 보고 내부 model-specific port로 위임한다.

권장 조건:
- model별 dimension이 다르면 routing 전에 profile dimension 검증이 가능해야 한다.
- request-time SDK options가 provider별로 다르므로 core에 직접 넣지 않는다.
- routing 구현은 starter/adapter에 둔다.

## Resolver 동작

`RagEmbeddingProfileResolver`는 다음 순서를 유지한다.

1. `embeddingProfileId`가 있으면 profile을 찾는다.
2. profile이 있으면 profile의 provider/model/dimension/input type을 사용한다.
3. profile과 request provider/model을 동시에 보내면 거부한다.
4. provider/model만 있으면 default profile과 섞지 않는다.
5. legacy minimal request는 기존 default port 범위를 유지한다.

다중 model 지원 시 resolver는 profile의 provider/model을 실제 port lookup key로 변환해야 한다.
model이 실제 port 선택에 쓰이지 않는 adapter는 request model 불일치를 거부한다.

## Vector metadata와 검색

다중 model 운영에서는 아래 metadata가 검색 격리 기준이다.

- `embeddingProvider`
- `embeddingModel`
- `embeddingDimension`
- `embeddingProfileId`
- `embeddingInputType` (저장 metadata 기준, query-side filter 적용 여부는 후속 구현에서 결정)

explicit profile 검색은 같은 metadata filter를 추가한다.
legacy minimal 검색은 기존 metadata 없는 chunk 호환성을 위해 filter를 강제하지 않는다.

## 첫 구현 PR 제안

1. `ProviderEmbeddingPortFactory`에 model-specific port 생성에 필요한 factory contract를 추가한다.
2. 한 provider type만 먼저 지원한다. Google GenAI 또는 Ollama처럼 starter가 직접 `EmbeddingModel`을 build하는
   provider가 OpenAI singleton `EmbeddingModel` 재사용 방식보다 구현 범위가 작다.
3. `AiAdapterProperties.Provider.Channel`에 optional `models` map을 추가한다.
4. `ProviderEmbeddingConfiguration`이 provider id 또는 provider id + model id를 registry key로 등록한다.
5. `RagEmbeddingProfileResolver`가 profile의 provider/model을 실제 registry key로 해석하는 adapter를 둔다.
6. 기존 `studio.ai.providers.<id>.embedding.enabled=true` 단일 model 설정은 그대로 유지한다.
7. 지원 provider type에 대해 model별 port 생성 테스트를 추가하고, OpenAI는 singleton adapter 한계를 문서화한다.

## 금지 범위

- `studio-platform-ai` core에 OpenAI/Gemini/Ollama SDK 의존성을 추가하지 않는다.
- pgvector 등 vector DB 구현을 core로 이동하지 않는다.
- image bytes/URI embedding 같은 multimodal provider 구현은 별도 PR로 분리한다.
- 기존 `EmbeddingPort`와 `AiProviderRegistry.embeddingPort(String)`를 제거하지 않는다.
