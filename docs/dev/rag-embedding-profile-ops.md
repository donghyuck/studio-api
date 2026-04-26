# RAG embedding profile 운영 가이드

이 문서는 RAG 색인/검색에서 embedding provider, model, profile을 선택할 때의 운영 기준을 정리한다.
서버 런타임 설정은 Spring AI가 소유하고, Studio 설정은 RAG orchestration과 선택 정책을 소유한다.

## 설정 책임

| 영역 | Canonical source | 설명 |
|---|---|---|
| API key / base-url | `spring.ai.*` | provider SDK 접속 설정이다. |
| chat model | `spring.ai.<provider>.chat.options.model` | 실제 chat provider 호출 model이다. |
| embedding model / dimension | `spring.ai.<provider>.embedding.*` | 실제 embedding provider 호출 model과 dimension이다. |
| provider enable / default provider | `studio.ai.providers.*`, `studio.ai.default-provider` | Studio가 사용할 provider id와 기본 provider 선택이다. |
| RAG embedding profile | `studio.ai.rag.embedding-profiles.*` | RAG 요청에서 선택할 profile id와 metadata/filter 기준이다. |

`studio.ai.providers.<id>.chat.model`과 `studio.ai.providers.<id>.embedding.model`은 legacy/fallback 성격이다.
Spring AI provider를 쓰는 운영 환경에서는 가능하면 `spring.ai.*`만 runtime model source로 사용한다.

## 기본 예시

Google Gemini embedding을 RAG 기본 profile로 사용하는 예시다.

```yaml
studio:
  ai:
    enabled: true
    default-provider: gemini
    providers:
      gemini:
        type: GOOGLE_AI_GEMINI
        enabled: true
        chat:
          enabled: true
        embedding:
          enabled: true
        google-embedding:
          task-type: RETRIEVAL_DOCUMENT
    rag:
      default-embedding-profile: retrieval-ko
      embedding-profiles:
        retrieval-ko:
          provider: gemini
          model: gemini-embedding-001
          dimension: 768
          supported-input-types: [TEXT, TABLE_TEXT, IMAGE_CAPTION, OCR_TEXT]

spring:
  ai:
    google:
      genai:
        chat:
          api-key: ${GOOGLE_AI_API_KEY}
          options:
            model: gemini-2.5-flash
        embedding:
          api-key: ${GOOGLE_AI_API_KEY}
          text:
            options:
              model: gemini-embedding-001
              dimensions: 768
```

OpenAI embedding을 사용할 때는 `spring.ai.openai.embedding.options.model`을 실제 embedding model source로 둔다.

```yaml
studio:
  ai:
    default-provider: openai
    providers:
      openai:
        type: OPENAI
        enabled: true
        chat:
          enabled: true
        embedding:
          enabled: true
    rag:
      default-embedding-profile: openai-small
      embedding-profiles:
        openai-small:
          provider: openai
          model: text-embedding-3-small
          dimension: 1536

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

## 선택 우선순위

RAG 색인/검색 요청의 embedding 선택은 다음 순서로 처리한다.

1. 요청의 `embeddingProfileId`
2. 요청의 `embeddingProvider` / `embeddingModel`
3. `studio.ai.rag.default-embedding-profile`
4. 기본 `EmbeddingPort`

`embeddingProfileId`와 `embeddingProvider`/`embeddingModel`을 동시에 보내는 요청은 혼합 해석을 막기 위해 거부한다.
요청에서 provider/model만 지정하면 default profile의 model/dimension을 섞어 쓰지 않는다.

Spring AI adapter는 등록 시점의 `EmbeddingModel` 하나를 호출한다. 따라서 profile/request의 `embeddingModel`은
실제 `spring.ai.*` embedding model 설정과 일치해야 한다. 다른 model을 요청하면 metadata만 다른 vector space로
저장되는 것을 막기 위해 실패한다.

## API 요청 필드

다음 API는 기존 request body를 유지하면서 optional embedding 선택 필드를 받는다.

| API | 필드 |
|---|---|
| `POST /api/mgmt/ai/rag/index` | `embeddingProfileId`, `embeddingProvider`, `embeddingModel` |
| `POST /api/mgmt/ai/rag/search` | `embeddingProfileId`, `embeddingProvider`, `embeddingModel` |
| `POST /api/mgmt/ai/rag/jobs` | `embeddingProfileId`, `embeddingProvider`, `embeddingModel` |
| `POST /api/ai/chat/rag` | `embeddingProfileId`, `embeddingProvider`, `embeddingModel` |
| `POST /api/mgmt/attachments/{attachmentId}/rag/index` | `embeddingProfileId`, `embeddingProvider`, `embeddingModel` |
| `POST /api/mgmt/attachments/rag/search` | `embeddingProfileId`, `embeddingProvider`, `embeddingModel` |
| `POST /api/mgmt/ai/vectors/search` | `embeddingProfileId`, `embeddingProvider`, `embeddingModel` |

`/api/ai/chat/rag`에서 `chat.provider`와 `chat.model`은 답변 생성 model 선택이고,
`embeddingProfileId`/`embeddingProvider`/`embeddingModel`은 retrieval embedding 선택이다.

## Metadata와 검색 범위

새로 색인되는 `VectorRecord`에는 가능한 범위에서 아래 metadata가 기록된다.

- `embeddingProvider`
- `embeddingModel`
- `embeddingDimension`
- `embeddingProfileId`
- `embeddingInputType`

검색 요청에 embedding 선택 필드가 명시되면 같은 metadata 기준이 `VectorSearchRequest.metadataFilter()`에 추가된다.
반대로 minimal legacy 검색 요청에는 기존 chunk와의 호환성을 위해 default profile metadata filter를 강제로 붙이지 않는다.

## 구조화 chunk input type

textract/chunking 기반 structured RAG 색인은 chunk type을 embedding input type metadata로 보존한다.

| chunk | embedding input type |
|---|---|
| 일반 텍스트 | `TEXT` |
| table text | `TABLE_TEXT` |
| image caption | `IMAGE_CAPTION` |
| OCR text | `OCR_TEXT` |

현재 범위는 image bytes/URI embedding이 아니라 caption, alt text, OCR text 같은 텍스트 embedding이다.
provider-specific multimodal embedding은 별도 adapter/starter에서 다룬다.

## 운영 화면 권장 흐름

1. 설정 화면에서 `/api/ai/info/providers`로 활성 provider를 표시한다.
2. RAG 색인 화면은 대상별 기본 `embeddingProfileId`를 선택하거나 서버 default profile을 사용한다.
3. `POST /api/mgmt/ai/rag/jobs` 또는 attachment RAG index API에 profile field를 포함해 색인을 요청한다.
4. `X-RAG-Job-Id` 또는 job create response로 job 상태를 polling한다.
5. 색인 완료 후 object metadata/chunk API에서 `embeddingProfileId`, `embeddingModel`, `embeddingInputType`을 확인한다.
6. RAG 검색/채팅 화면은 색인에 사용한 profile과 같은 `embeddingProfileId`를 보낸다.

## 운영 주의사항

- 같은 provider에서 여러 embedding model을 동시에 쓰려면 서로 다른 `EmbeddingPort`가 실제로 등록되어야 한다.
- profile metadata가 없는 legacy chunk는 explicit profile 검색에서 제외될 수 있다.
- legacy chunk까지 검색해야 하는 화면은 embedding 선택 필드를 비워 minimal legacy 검색으로 호출한다.
- profile 기반 운영으로 전환할 때는 object scope 단위 재색인을 먼저 수행한다.
