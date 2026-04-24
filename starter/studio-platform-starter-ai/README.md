# studio-platform-starter-ai

AI 서비스(채팅, 임베딩, 벡터 스토어, RAG 파이프라인)를 자동 구성하는 스타터이다.
애플리케이션 모듈은 이 스타터가 제공하는 포트와 `RagPipelineService`를 통해 공통 AI 기능을 재사용할 수 있다.
멀티 프로바이더 팩토리 패턴을 통해 OpenAI, Google AI Gemini, Ollama 등 여러 LLM 프로바이더를
동시에 등록하고, `default-provider`로 지정한 프로바이더를 기본 `ChatPort` / `EmbeddingPort` 빈으로 노출한다.
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
    default-provider: openai   # 기본 ChatPort / EmbeddingPort 로 사용할 프로바이더 ID
    providers:
      <provider-id>:
        type: OPENAI            # OPENAI | GOOGLE_AI_GEMINI | OLLAMA
        enabled: true
        chat:
          enabled: true
          model: gpt-4o-mini    # OPENAI / OLLAMA 는 여기서 지정
        embedding:
          enabled: true
          model: text-embedding-3-small
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
        api-key: ${GOOGLE_AI_API_KEY}
        chat:
          enabled: true
          model: gemini-2.0-flash
        embedding:
          enabled: false

spring:
  ai:
    google:
      genai:
        embedding:
          api-key: ${GOOGLE_AI_API_KEY}
          text:
            options:
              model: text-embedding-004
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
| `ChatPort` (기본) | `default-provider`에 해당하는 채팅 포트 |
| `EmbeddingPort` (기본) | `default-provider`에 해당하는 임베딩 포트 |
| `VectorStorePort` | JdbcTemplate이 있을 때 pgvector 기반 벡터 스토어 자동 생성 |
| `RagPipelineService` | RAG 인덱싱/검색 facade 계약. 기본 구현은 `DefaultRagPipelineService` |
| `ChunkingOrchestrator` | `starter-chunking`이 있을 때 RAG indexing chunk 생성에 사용 |
| `PromptManager` | Mustache 템플릿 기반 프롬프트 렌더러 |
| `TextCleaner` | `studio.ai.pipeline.cleaner.enabled=true`일 때 색인 전 텍스트 정제 |

`RagPipelineService`는 문서/파일/도메인 객체별 텍스트를 chunk로 나누고, 임베딩과 메타데이터를 벡터 스토어에 저장한다.
`objectType`/`objectId` 메타데이터를 함께 저장하면 AI web starter의 RAG chat API에서 특정 파일이나 객체 범위에 한정해 답변할 수 있다.
같은 `objectType`/`objectId`를 재색인하면 기존 chunk를 삭제한 뒤 새 chunk를 저장해 stale chunk가 남지 않도록 한다.

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

기존 `studio.ai.pipeline.chunk-size`, `studio.ai.pipeline.chunk-overlap`는 legacy `TextChunker` fallback 설정이다.
신규 `ChunkingOrchestrator` 경로에서는 `studio.chunking.*`가 기준이다.

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
        max-list-limit: 100
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
| `studio.ai.pipeline.object-scope.max-list-limit` | `100` | object-scope list 최대 limit |
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

## 5) 참고 사항

- **fail-fast**: `studio.ai.default-provider`가 설정되지 않았거나, 해당 프로바이더에 chat/embedding 포트가
  모두 없으면 애플리케이션 시작 시 `IllegalStateException`으로 즉시 실패한다.
- **default-provider 생략 불가**: `default-provider`는 필수 항목이다. 값이 없으면 시작이 거부된다.
- OPENAI 타입 프로바이더는 동시에 하나만 활성화할 수 있다. 두 개 이상 활성화하면 시작 시 실패한다.
- Spring AI 표준 속성(`spring.ai.*`)과 Studio 전용 속성(`studio.ai.*`)을 혼합 사용한다.
  OpenAI의 API 키 및 모델은 `spring.ai.openai.*`로 설정하고, Google/Ollama 고유 옵션은
  각 프로바이더 `studio.ai.providers.<id>.*` 하위에 설정한다.
- 벡터 스토어는 PostgreSQL + pgvector 확장을 사용하며, `JdbcTemplate` 빈이 없으면 생성되지 않는다.
- Caffeine 캐시와 Resilience4j Retry가 내장되어 있어 LLM 호출에 재시도 및 캐싱이 적용된다.
