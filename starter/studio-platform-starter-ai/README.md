# studio-platform-starter-ai

AI 서비스(채팅, 임베딩, 벡터 스토어, RAG 파이프라인)를 자동 구성하는 스타터이다.
애플리케이션 모듈은 이 스타터가 제공하는 포트와 `RagPipelineService`를 통해 공통 AI 기능을 재사용할 수 있다.
멀티 프로바이더 팩토리 패턴을 통해 OpenAI, Google AI Gemini, Ollama 등 여러 LLM 프로바이더를
동시에 등록하고, 기본 `ChatPort` / `EmbeddingPort` 빈을 노출한다. 기본 chat provider와 embedding provider는
같은 provider를 쓰거나 별도 provider로 분리할 수 있다.
JdbcTemplate이 컨텍스트에 있으면 pgvector 기반 `VectorStorePort`도 자동으로 생성된다.
`studio-platform-ai`는 공통 계약만 제공하고, Spring AI adapter와 기본 RAG 구현은 이 스타터가 제공한다.
`starter:studio-platform-starter-chunking`이 있으면 `RagPipelineService`는 `ChunkingOrchestrator`를 우선 사용하고,
없으면 기존 `TextChunker` fallback을 사용한다.

> **중요** Spring AI BOM(`org.springframework.ai:spring-ai-bom:1.1.2`)이 `api(platform())` 으로
> 노출되므로, 소비 앱에서 별도로 BOM을 선언하지 않아도 Spring AI 의존성 버전이 자동 관리된다.

## 1) 의존성 추가

스타터 자체와 함께 **사용할 프로바이더 라이브러리를 소비 앱에서 직접 선언**해야 한다.
프로바이더 라이브러리는 `compileOnly`로 선언되어 있어 전이 의존성으로 포함되지 않는다.

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-ai"))

    // --- 프로바이더 라이브러리 (사용할 것만 선언) ---

    // OpenAI
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // Google AI Gemini (채팅)
    implementation("org.springframework.ai:spring-ai-google-genai")

    // Google AI Gemini (임베딩)
    implementation("org.springframework.ai:spring-ai-google-genai-embedding")

    // Ollama
    implementation("org.springframework.ai:spring-ai-ollama")

    // 벡터 스토어를 사용할 경우 (pgvector)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // RAG indexing chunking 전략
    implementation(project(":starter:studio-platform-starter-chunking"))
}
```

## 2) 기능 활성화

```yaml
studio:
  ai:
    enabled: true
```

## 3) 설정

### 기본 구조

```yaml
studio:
  ai:
    enabled: true
    default-provider: openai   # 기본 ChatPort / EmbeddingPort fallback provider ID
    # default-chat-provider: openai      # 선택: 기본 ChatPort provider ID
    # default-embedding-provider: openai # 선택: 기본 EmbeddingPort provider ID
    providers:
      <provider-id>:
        type: OPENAI            # OPENAI | GOOGLE_AI_GEMINI | OLLAMA
        enabled: true
        chat:
          enabled: true
        embedding:
          enabled: true
```

`studio.ai.*`는 provider 선택, 활성화, Studio RAG orchestration 설정을 담당한다.
`studio.ai.default-provider`만 설정하면 기존처럼 chat/embedding 기본 provider fallback으로 모두 사용한다.
이 값이 설정된 경우 기존 계약 보존을 위해 해당 provider에는 chat과 embedding port가 모두 등록되어야 한다.
chat과 embedding을 분리해야 하면 `studio.ai.default-chat-provider`와
`studio.ai.default-embedding-provider`를 지정한다. 이 두 값이 있으면 legacy `default-provider` 없이도
기동할 수 있다.
provider SDK의 실제 API key, model, dimensions, temperature, base-url 같은 실행 옵션은
`spring.ai.*`를 canonical source로 둔다. `studio.ai.providers.<id>.chat.model`과
`studio.ai.providers.<id>.embedding.model`은 legacy/fallback 성격이며 Spring AI provider에서는
가능하면 `spring.ai.*` 설정을 사용한다.

RAG 색인/검색에서 embedding provider/model을 고정해 운영해야 하면 `studio.ai.rag.embedding-profiles`
를 사용한다. profile은 Studio orchestration 설정이며 실제 provider SDK의 기본 API key/model/dimensions는
계속 `spring.ai.*`가 소유한다.
선택 우선순위는 요청의 `embeddingProfileId`, 요청의 `embeddingProvider`/`embeddingModel`, 기본
`studio.ai.rag.default-embedding-profile`, 기본 `EmbeddingPort` 순서다. 요청이 provider/model만 지정하면
기본 profile의 model/dimension을 섞어 쓰지 않는다.
Spring AI adapter는 등록 시점의 `EmbeddingModel`을 사용하므로, profile/request의 `model`은 실제 Spring AI
embedding model 설정과 일치해야 한다. 다른 model이 들어오면 metadata만 다른 vector space로 기록되는 것을 막기 위해
요청을 거부한다. 여러 embedding model을 동시에 쓰려면 provider id/profile을 분리해 각각 다른 `EmbeddingPort`로
등록하는 adapter 구성이 필요하다.
운영 설정 기준과 API 요청 흐름은
[`RAG embedding profile 운영 가이드`](../../docs/dev/rag-embedding-profile-ops.md)를 따른다.

```yaml
studio:
  ai:
    rag:
      default-embedding-profile: retrieval-ko
      embedding-profiles:
        retrieval-ko:
          provider: gemini
          model: gemini-embedding-001
          dimension: 768
          supported-input-types: [TEXT, TABLE_TEXT, IMAGE_CAPTION, OCR_TEXT]
```

### OpenAI 예시

```yaml
studio:
  ai:
    enabled: true
    default-provider: openai
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

### Google AI Gemini 예시

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

### Ollama 예시

```yaml
studio:
  ai:
    enabled: true
    default-provider: ollama
    providers:
      ollama:
        type: OLLAMA
        enabled: true
        chat:
          enabled: true
          model: llama3.2
        embedding:
          enabled: true

spring:
  ai:
    ollama:
      embedding:
        options:
          model: nomic-embed-text
```

### 엔드포인트 기본 경로 (선택)

```yaml
studio:
  ai:
    endpoints:
      enabled: false       # AI 정보 조회 엔드포인트 활성화 여부
      base-path: /api/ai   # 기본값
```

## 4) 자동 구성되는 주요 빈

| 빈 타입 | 설명 |
|---|---|
| `AiProviderRegistry` | 등록된 모든 프로바이더의 ChatPort / EmbeddingPort 를 보관하는 레지스트리 |
| `ChatPort` (기본) | `default-chat-provider` 또는 `default-provider`에 해당하는 채팅 포트 |
| `EmbeddingPort` (기본) | `default-embedding-provider` 또는 `default-provider`에 해당하는 임베딩 포트 |
| `VectorStorePort` | JdbcTemplate이 있을 때 pgvector 기반 벡터 스토어 자동 생성 |
| `RagPipelineService` | RAG 인덱싱/검색 facade 계약. 기본 구현은 `DefaultRagPipelineService` |
| `RagIndexJobRepository` | RAG 색인 작업 상태/로그 저장소. 기본 구현은 단일 인스턴스용 in-memory repository |
| `RagIndexJobService` | RAG 색인 작업 생성/조회/취소/재시도와 progress listener 연결 |
| `RagIndexJobSourceExecutor` | source 기반 RAG job 실행 확장점. 등록된 Bean은 job service가 ordered stream으로 조회 |
| `ChunkingOrchestrator` | `starter-chunking`이 있을 때 RAG indexing chunk 생성에 사용 |
| `PromptManager` | Mustache 템플릿 기반 프롬프트 렌더러 |
| `TextCleaner` | `studio.ai.pipeline.cleaner.enabled=true`일 때 색인 전 텍스트 정제 |

`RagPipelineService`는 문서/파일/도메인 객체별 텍스트를 chunk로 나누고, 임베딩과 메타데이터를 벡터 스토어에 저장한다.
기본 구현은 RAG chunk 저장 모델로 `VectorRecord.builder()`를 사용하고, 기존 `VectorDocument` 기반
store 구현은 `VectorStorePort`의 default adapter를 통해 계속 호환된다.
`embeddingModel` metadata가 없으면 core 필수 필드 호환을 위해 `unknown` placeholder가 기록된다.
RAG embedding profile 또는 요청 단위 embedding 선택이 사용되면 `embeddingProvider`,
`embeddingModel`, `embeddingDimension`, `embeddingProfileId`, `embeddingInputType` metadata가 가능한 범위에서
`VectorRecord`에 기록된다.
검색 요청이 embedding 선택 필드를 포함하면 같은 metadata 기준을 `VectorSearchRequest.metadataFilter()`에 추가해
다른 embedding space의 chunk가 섞이지 않도록 한다.
minimal legacy 검색 요청에는 기존 색인 metadata가 없는 chunk와의 호환성을 위해 default profile filter를 강제로 붙이지 않는다.
`objectType`/`objectId` 메타데이터를 함께 저장하면 AI web starter의 RAG chat API에서 특정 파일이나 객체 범위에 한정해 답변할 수 있다.
같은 `objectType`/`objectId`를 재색인하면 기존 chunk를 삭제한 뒤 새 chunk를 저장해 stale chunk가 남지 않도록 한다.
검색 요청은 기존 `searchByObject(...)`와 함께 `RagSearchRequest`의 `MetadataFilter.objectScope(...)` 경로도 지원한다.
두 경로는 같은 객체 범위 retrieval 동작을 사용한다.
RAG metadata key의 표준 의미와 legacy alias 기준은
[`studio-platform-ai` RAG metadata key reference](../../studio-platform-ai/README.md#rag-metadata-key-reference)를 따른다.

`RagIndexJobService`는 `RagPipelineService.index(request, listener)` overload를 사용해
`CHUNKING`, `EMBEDDING`, `INDEXING`, `COMPLETED` 단계와 chunk/embedding/index count를 기록한다.
raw text가 없는 source 기반 job은 등록된 `RagIndexJobSourceExecutor`가 `supports(...)`로 선택되면
해당 executor가 실행한다. 기본 starter는 source 구현을 포함하지 않으며, attachment 같은 application module이
자기 source executor를 별도 Bean으로 제공한다.
기본 `InMemoryRagIndexJobRepository`는 운영 화면 개발 및 단일 인스턴스 smoke 용도이며, 외부 queue나
분산 worker를 포함하지 않는다. 재시도를 위해 최근 raw text 요청을 메모리에 보관하되 저장 request 수는
bounded eviction으로 제한한다. 영구 이력, 다중 인스턴스 공유, 감사 로그가 필요하면 같은
`RagIndexJobRepository` 계약으로 DB 기반 구현을 등록한다. JDBC 기반 기본 구현을 사용하려면
`NamedParameterJdbcTemplate` bean과 AI schema migration(`schema/ai/{db}/V601__create_rag_index_job_tables.sql`)을
적용한 뒤 아래 설정을 사용한다.

```yaml
studio:
  ai:
    pipeline:
      jobs:
        repository: jdbc # 기본값: memory
```

`repository=jdbc`는 job 상태와 로그만 영속화한다. 재시도 실행에 필요한 원본 raw text/source request는
기본 `DefaultRagIndexJobService`의 bounded memory cache에 남아 있을 때만 즉시 재실행할 수 있다.
서버 재시작 또는 request cache eviction 이후에는 retry가 `409 Conflict`로 거절된다. 장기 재시도까지
보장하려면 source별 request 복원 전략을 별도 구현한다.
`cancelJob(jobId)`는 `PENDING`/`RUNNING` job을 `CANCELLED`로 표시하고, 이미 도착한 late progress callback이
취소 상태를 성공/실패로 덮어쓰지 않도록 방어한다. 실행 중인 외부 provider/vector 호출 자체를 강제 중단하지는 않는다.

### Chat metadata / streaming

`SpringAiChatAdapter`는 기존 metadata key를 유지하면서 `studio-platform-ai`의 typed metadata key도 함께 채운다.

| key | 설명 |
|---|---|
| `responseId` | Spring AI response id |
| `modelName` | Spring AI metadata model name |
| `finishReason` | generation finish reason |
| `tokenUsage` | `inputTokens`, `outputTokens`, `totalTokens` |
| `provider` | provider type (`OPENAI`, `GOOGLE_AI_GEMINI`, `OLLAMA`) |
| `resolvedModel` | response metadata model, request model, configured model 순서로 해석한 model |
| `latencyMs` | provider 호출 latency |

`ChatPort.stream(ChatRequest)`는 native Spring AI `ChatModel.stream(Prompt)`를 우선 사용한다. native streaming이 지원되지 않거나 명시적으로 unsupported인 경우 기존 `chat()` 호출 결과를 `delta`, `usage`, `complete` event sequence로 변환하는 fallback을 사용한다.
`usage` / `complete` event의 `tokenUsage`는 provider가 마지막 stream chunk에 제공한 usage metadata를 기준으로 한다.
`ChatPort.stream(ChatRequest)`는 Java `Stream` 기반 동기 계약이므로 WebFlux/Netty event-loop thread에서 직접 소비하지 말고 web 계층에서 별도 scheduler 또는 blocking boundary를 둔다.
HTTP `text/event-stream` endpoint 변환은 `starter-ai-web` 책임이다.

호환성 기준:

- 기존 `ChatResponse.metadata()` map은 유지되며 신규 key는 additive하게 추가된다.
- `resolvedModel`은 response metadata model, request model, configured model 순서로 결정된다.
- native stream이 빈 stream을 반환하거나 unsupported를 명시하면 fallback stream으로 대체된다.
- stream 도중 provider 오류가 발생하면 오류를 숨기지 않고 소비 시점에 전파한다. HTTP error event 변환은 web starter가 담당한다.

### RAG 청킹 설정

`starter:studio-platform-starter-chunking`이 classpath에 있으면 `RagPipelineService`는 신규
`ChunkingOrchestrator`를 사용한다. 없으면 기존 `TextChunker` 기반 fallback이 적용된다.
`TextChunker`와 `OverlapTextChunker`는 deprecated legacy fallback이다. 신규 RAG indexing 또는
구조화 chunking 확장은 `starter:studio-platform-starter-chunking`의 auto-configuration과
`studio-platform-chunking`의 `ChunkingOrchestrator`를 기준으로 구현한다.
`DefaultRagPipelineService` 내부에서는 `RagChunker` adapter가 `ChunkingOrchestrator` 우선 경로와
legacy fallback 변환을 캡슐화한다.
auto-configuration도 같은 기준을 따른다. `ChunkingOrchestrator` bean이 있으면 기본
`OverlapTextChunker` bean을 만들지 않고, 없을 때만 legacy fallback bean을 등록한다.

```yaml
studio:
  chunking:
    enabled: true
    strategy: recursive
    max-size: 800
    overlap: 100
```

| 설정 | 기본값 | 설명 |
|---|---:|---|
| `studio.chunking.enabled` | `true` | chunking starter 기본 bean 등록 여부 |
| `studio.chunking.strategy` | `recursive` | Phase 1 전략. `recursive`, `fixed-size` 지원 |
| `studio.chunking.max-size` | `800` | chunk 최대 문자 수 |
| `studio.chunking.overlap` | `100` | 이전 chunk tail을 다음 chunk에 포함할 문자 수 |

기존 `studio.ai.pipeline.chunk-size`, `studio.ai.pipeline.chunk-overlap`는 deprecated legacy `TextChunker` fallback 설정이다.
신규 `ChunkingOrchestrator` 경로에서는 `studio.chunking.*`가 기준이다.

#### legacy RAG chunk 설정 migration

신규 RAG indexing에서는 `starter:studio-platform-starter-chunking`을 추가하고 `studio.chunking.*` 설정을 사용한다.
`studio.ai.pipeline.*` chunk 설정은 `ChunkingOrchestrator` bean이 없어서 `starter-ai`가 deprecated
`OverlapTextChunker` fallback을 생성하는 경우에만 적용된다.

| legacy 설정 | 신규 설정 | 적용 조건 |
|---|---|---|
| `studio.ai.pipeline.chunk-size` | `studio.chunking.max-size` | legacy `TextChunker` fallback에서만 legacy key 적용 |
| `studio.ai.pipeline.chunk-overlap` | `studio.chunking.overlap` | legacy `TextChunker` fallback에서만 legacy key 적용 |

Migration 예시:

```yaml
# legacy fallback only
studio:
  ai:
    pipeline:
      chunk-size: 500
      chunk-overlap: 50

# recommended
studio:
  chunking:
    max-size: 800
    overlap: 100
```

`starter:studio-platform-starter-chunking`이 classpath에 있고 `ChunkingOrchestrator`가 등록되면
기본 `OverlapTextChunker` bean은 생성되지 않는다. 이때 `studio.ai.pipeline.chunk-size`와
`studio.ai.pipeline.chunk-overlap`은 신규 chunking 경로에 전달되지 않는다. 신규 경로의 chunk 크기와
overlap은 `studio.chunking.max-size`, `studio.chunking.overlap`로 조정한다.

### RAG 파이프라인 튜닝

이슈 #202부터 RAG 검색과 객체 범위 조회 제한은 운영 설정으로 조정할 수 있다. 기본값은 기존 동작과 유사하게 유지하되,
query 없는 객체 범위 조회가 과도한 chunk를 반환하지 않도록 service layer에서 limit을 보정한다.

```yaml
studio:
  ai:
    pipeline:
      retrieval:
        vector-weight: 0.7
        lexical-weight: 0.3
        min-relevance-score: 0.15
        keyword-fallback-enabled: true
        semantic-fallback-enabled: true
        query-expansion:
          enabled: true
          max-keywords: 10
      keywords:
        scope: document
        max-input-chars: 4000
      object-scope:
        default-list-limit: 20
        max-list-limit: 200
      diagnostics:
        enabled: false
        log-results: false
        max-snippet-chars: 120
    vector:
      postgres:
        text-search-config: simple
```

| 설정 | 기본값 | 설명 |
|---|---:|---|
| `studio.ai.pipeline.retrieval.vector-weight` | `0.7` | hybrid 검색의 벡터 점수 비중 |
| `studio.ai.pipeline.retrieval.lexical-weight` | `0.3` | hybrid 검색의 lexical 점수 비중 |
| `studio.ai.pipeline.retrieval.min-relevance-score` | `0.15` | fallback 성공으로 판단할 최소 relevance score |
| `studio.ai.pipeline.retrieval.keyword-fallback-enabled` | `true` | keyword-enriched hybrid fallback 사용 여부 |
| `studio.ai.pipeline.retrieval.semantic-fallback-enabled` | `true` | semantic fallback 사용 여부 |
| `studio.ai.pipeline.retrieval.query-expansion.enabled` | `true` | keyword fallback에서 LLM keyword extractor로 query를 보강할지 여부 |
| `studio.ai.pipeline.retrieval.query-expansion.max-keywords` | `10` | 원본 query 외에 추가할 최대 keyword 수 |
| `studio.ai.pipeline.keywords.scope` | `document` | 색인 metadata keyword 범위. `document`, `chunk`, `both` 지원 |
| `studio.ai.pipeline.keywords.max-input-chars` | `4000` | LLM keyword extractor에 전달할 입력 최대 길이 |
| `studio.ai.pipeline.object-scope.default-list-limit` | `20` | query 없는 object-scope list 기본 limit |
| `studio.ai.pipeline.object-scope.max-list-limit` | `200` | object-scope list 최대 limit |
| `studio.ai.pipeline.diagnostics.enabled` | `false` | RAG 검색 fallback 전략과 결과 카운트 수집 여부 |
| `studio.ai.pipeline.diagnostics.log-results` | `false` | diagnostics 활성화 시 bounded result snippet debug log 출력 여부 |
| `studio.ai.pipeline.diagnostics.max-snippet-chars` | `120` | result snippet debug log 최대 문자 수 |

`vector-weight`와 `lexical-weight`는 각각 0 이상이어야 하며 두 값의 합은 0보다 커야 한다.
diagnostics metadata에는 chunk 본문을 포함하지 않고 strategy, 결과 수, threshold, weight, object scope, topK만 기록한다.
`keywords.scope=document`는 기존 동작과 동일하게 문서 단위 `keywords`/`keywordsText`만 기록한다.
`chunk` 또는 `both`를 사용하면 chunk metadata에 `chunkKeywords`/`chunkKeywordsText`를 추가한다.
호출자가 제공한 `RagIndexRequest.keywords`는 document-level keyword로만 사용되며, `keywords.scope=chunk`에서는 저장되지 않는다.
`query-expansion.enabled`는 `keyword-fallback-enabled=true`일 때만 효과가 있다.
Keyword 값은 trim, blank 제거, case-insensitive de-duplication을 거친다.
PostgreSQL lexical 검색은 현재 SQL ranking 동작을 유지한다. `studio.ai.vector.postgres.text-search-config=simple`은 향후 PostgreSQL FTS config 지원을 위한 문서화된 설정 후보이며, 이번 PR에서는 실제 SQL에 적용되지 않는다.

### RAG 색인 전 텍스트 정제

이슈 #204부터 RAG 색인 전에 LLM 기반 텍스트 정제를 선택적으로 적용할 수 있다. 기본값은 비활성화이므로
기존 raw indexing 동작은 유지된다. 활성화하면 `rag-cleaner` Mustache prompt를 렌더링해 LLM에 전달하고,
응답 JSON의 `clean_text` 필드를 색인 대상 텍스트로 사용한다.

```yaml
studio:
  ai:
    pipeline:
      cleaner:
        enabled: false
        prompt: rag-cleaner
        max-input-chars: 20000
        fail-open: true
```

| 설정 | 기본값 | 설명 |
|---|---:|---|
| `studio.ai.pipeline.cleaner.enabled` | `false` | 색인 전 LLM text cleaner 사용 여부 |
| `studio.ai.pipeline.cleaner.prompt` | `rag-cleaner` | 사용할 Mustache prompt 이름 |
| `studio.ai.pipeline.cleaner.max-input-chars` | `20000` | cleaner에 전달할 입력 최대 길이 |
| `studio.ai.pipeline.cleaner.fail-open` | `true` | cleaner 호출/파싱 실패 시 원문으로 색인을 계속할지 여부 |

정제 결과는 vector metadata에 additive key로 기록된다.
`cleaned`, `cleanerPrompt`, `originalTextLength`, `indexedTextLength`, `chunkCount`, `chunkLength`가 추가되며,
호출자가 같은 key를 이미 전달한 경우 기존 metadata 값을 보존한다.

## 관련 모듈
- `studio-platform-ai` — 이 스타터가 구현하는 포트 인터페이스 모듈
- `studio-platform-starter-ai-web` — AI HTTP 엔드포인트를 노출하는 짝 스타터 (이 스타터와 함께 사용)
- `studio-application-modules/content-embedding-pipeline` — 이 스타터의 `EmbeddingPort`/`VectorStorePort`를 소비하는 임베딩 파이프라인 모듈
- `docs/dev/spring-ai-openai.md` — OpenAI provider의 Spring AI 전환 방향과 롤백 참고 문서

## Adapter 분리 원칙

이 스타터는 현재 Spring AI provider adapter와 pgvector `VectorStorePort` adapter를 함께 자동 구성한다.
첫 호환 범위에서는 기존 자동 구성을 유지하지만, 새 provider-specific 구현이나 새 vector DB 구현은 `studio-platform-ai` core에 넣지 않는다.
반복 사용이 확인된 provider/vector DB 구현은 별도 adapter 또는 starter 모듈로 분리하는 방향을 우선한다.

## 5) 참고 사항

- **fail-fast**: 기본 chat provider에 `ChatPort`가 없거나 기본 embedding provider에 `EmbeddingPort`가 없으면
  애플리케이션 시작 시 `IllegalStateException`으로 즉시 실패한다.
- **default-provider fallback**: `default-provider`는 기존 호환용 fallback이다. 생략하려면
  `default-chat-provider`와 `default-embedding-provider`를 모두 설정해야 한다.
  `default-provider`를 함께 설정하는 경우 해당 provider는 기존 호출자 호환을 위해 chat/embedding port를 모두 제공해야 한다.
- OPENAI 타입 프로바이더는 동시에 하나만 활성화할 수 있다. 두 개 이상 활성화하면 시작 시 실패한다.
- Spring AI 표준 속성(`spring.ai.*`)과 Studio 전용 속성(`studio.ai.*`)을 혼합 사용한다.
  OpenAI의 API 키 및 모델은 `spring.ai.openai.*`로 설정하고, Google/Ollama 고유 옵션은
  각 프로바이더 `studio.ai.providers.<id>.*` 하위에 설정한다.
- 벡터 스토어는 PostgreSQL + pgvector 확장을 사용하며, `JdbcTemplate` 빈이 없으면 생성되지 않는다.
- Caffeine 캐시와 Resilience4j Retry가 내장되어 있어 LLM 호출에 재시도 및 캐싱이 적용된다.
