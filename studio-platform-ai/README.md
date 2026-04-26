# studio-platform-ai

AI 공통 계약 계층이다. 챗 완성, 임베딩 생성, 벡터 스토어 접근을 포트 인터페이스로 정의하고,
여러 모듈이 공유할 RAG 요청/응답 계약을 제공한다.
Spring AI 기반 런타임 구현체와 RAG pipeline 구현은 `studio-platform-starter-ai`가 담당한다.
RAG indexing용 chunking 계약과 구현은 `studio-platform-chunking`과 `studio-platform-starter-chunking`을 사용한다.

## 요약
포트/어댑터 원칙에 따라 AI 공급자(OpenAI, Ollama, Google AI Gemini 등)를 추상화한다. 애플리케이션 코드는 이 모듈의 포트 인터페이스에만 의존하고, 실제 공급자는 스타터가 빈으로 등록한다.

## 설계
- `ChatPort` / `EmbeddingPort` / `VectorStorePort`: 공급자 중립 포트 인터페이스
- `ChatResponseMetadata` / `TokenUsage`: provider 응답 metadata의 typed view
- `ChatStreamEvent`: provider-neutral streaming event 계약 (`delta`, `usage`, `complete`, `error`)
- `ConversationRepositoryPort`: conversation 저장소 구현을 위한 포트
- `AiProviderRegistry`: 공급자 이름을 키로 ChatPort/EmbeddingPort를 관리하는 레지스트리
- `TextChunker`: legacy fallback용 텍스트 chunking 인터페이스. 신규 RAG indexing은 `studio-platform-chunking`의 `ChunkingOrchestrator`를 사용한다.
- `RagPipelineService`: 인덱싱(`index`)과 검색(`search`, `searchByObject`, `listByObject`)을 정의하는 RAG facade 계약
- `RagIndexJobService` / `RagIndexJobRepository`: RAG 색인 작업 상태와 단계별 로그 저장소를 교체 가능하게 하는 계약
- `TextCleaner` / `KeywordExtractor` / `PromptRenderer`: RAG 전처리와 프롬프트 확장점 계약
- `RagPipelineOptions` 계열: 기본 RAG 구현을 대체하거나 테스트할 때 사용할 설정 계약
- `MetadataFilter`: retrieval 요청에서 `objectType`/`objectId` metadata convention을 표현하는 최소 필터 계약

## 주요 타입

| 타입 | 패키지 | 설명 |
|---|---|---|
| `MetadataFilter` | `core` | RAG/vector retrieval 요청의 object scope metadata filter |
| `ChatPort` | `core.chat` | 챗 완성 요청/응답 계약 |
| `ChatResponseMetadata` | `core.chat` | token usage, latency, provider, resolved model, memory, conversation metadata typed view |
| `ChatStreamEvent` | `core.chat` | streaming chat event 계약 |
| `ConversationRepositoryPort` | `core.chat` | conversation/message 저장소 포트 |
| `ChatConversation` / `ChatConversationMessage` / `ChatConversationSummary` | `core.chat` | conversation API 구현을 위한 provider-neutral 모델 |
| `EmbeddingPort` | `core.embedding` | 텍스트 임베딩 벡터 생성 계약 |
| `VectorStorePort` | `core.vector` | 벡터 저장/검색/하이브리드 검색 계약 |
| `VectorRecord` | `core.vector` | RAG chunk 저장을 표현하는 core vector storage 모델 |
| `VectorDocument` / `VectorSearchResult` | `core.vector` | 기존 vector 저장/검색 호출자 호환성을 위해 유지하는 모델 |
| `AiProviderRegistry` | `core.registry` | 공급자별 ChatPort/EmbeddingPort 룩업 |
| `TextChunk` / `TextChunker` | `core.chunk` | deprecated legacy fallback 계약. 신규 코드는 `studio-platform-chunking` 사용 |
| `RagPipelineService` | `service.pipeline` | 인덱싱/검색 RAG facade 계약 |
| `RagIndexJob` / `RagIndexJobLog` | `core.rag` | RAG 색인 작업 상태와 단계별 로그 모델 |
| `RagIndexJobRepository` / `RagIndexJobService` | `service.pipeline` | RAG 색인 작업 저장소와 실행 service 계약 |
| `RagIndexProgressListener` | `service.pipeline` | 색인 실행 단계와 count를 job service에 전달하는 listener 계약 |
| `TextCleaner` | `service.cleaning` | 색인 전 추출 텍스트 정제 계약 |
| `KeywordExtractor` | `service.keyword` | 색인/검색 keyword 추출 계약 |
| `PromptRenderer` | `service.prompt` | 프롬프트 렌더링 계약 |
| `RagPipelineOptions` | `service.pipeline` | RAG 구현 설정 계약 |
| `RagRetrievalDiagnostics` | `core.rag` | RAG 검색 fallback 전략과 결과 상태 진단 모델 |
| `AiProvider` | `core` | 지원 공급자 열거형 (OPENAI, OLLAMA, GOOGLE_AI_GEMINI) |

## RAG metadata key reference

RAG metadata는 chunk 저장, retrieval filter, context expansion, provenance 표시를 위한 cross-module 계약이다.
`studio-platform-ai`가 key 기준을 문서화하고, 실제 값 생성은 `starter-ai`, `starter-chunking`, `content-embedding-pipeline` 같은 조립 모듈이 담당한다.
Provider-specific 구현이나 vector DB-specific 구현은 이 기준 문서의 범위가 아니다.

`VectorRecord`의 first-class field(`documentId`, `chunkId`, `contentHash`, `embeddingModel`, `embeddingDimension` 등)는
`VectorRecord.toMetadata()`에서 표준 key로 병합되며 record field 값이 우선한다.
Pipeline enrichment 또는 custom metadata는 모듈별로 `putIfAbsent`를 우선 사용해 호출자가 전달한 값을 보존한다.
Adapter는 아래 key를 `metadata` map에서 읽을 수 있어야 한다.

### Identity and object scope

| key | 표준 의미 | 생성/소비 기준 |
|---|---|---|
| `tenantId` | tenant 범위 식별자 | multi-tenant adapter가 필요할 때 metadata로 전달한다. |
| `objectType` | RAG 객체 범위 type | `MetadataFilter.objectScope(...)`, `searchByObject(...)`, `replaceRecordsByObject(...)`에서 사용한다. |
| `objectId` | RAG 객체 범위 id | 같은 `objectType`/`objectId` 재색인은 stale chunk 제거 기준이다. |
| `documentId` | 원본 문서 id | `VectorRecord.documentId()`가 `toMetadata()`에 병합한다. |
| `chunkId` | 검색 가능한 chunk id | `VectorRecord.chunkId()`가 `toMetadata()`에 병합한다. |
| `parentChunkId` | child chunk의 parent section id | context expansion과 parent-child 복구에 사용한다. |
| `previousChunkId` / `nextChunkId` | 같은 parent 안의 인접 chunk link | window context expansion 후보 탐색에 사용한다. |

`objectType` 또는 `objectId` 중 하나만 있어도 object scope filter로 해석할 수 있다.
정확한 동작은 store adapter의 filter 구현에 달려 있으므로, attachment RAG처럼 안전한 객체 범위 검색은 둘 다 제공하는 것을 권장한다.

### Chunk ordering and compatibility

| key | 표준 의미 | 호환성 기준 |
|---|---|---|
| `chunkIndex` | RAG chunk의 0-based index | 신규 core/vector 계약의 표준 순서 key다. |
| `chunkOrder` | legacy persisted chunk 순서 | 기존 pgvector schema와 chunking Phase 1 호환을 위해 유지한다. |
| `chunkType` | `child`, `parent`, `table`, `ocr`, `image-caption` 등 chunk 역할 | retrieval hit와 context expansion strategy 선택에 사용한다. |
| `chunkLength` | 개별 chunk content 길이 | starter-ai 기본 pipeline이 기록한다. |
| `chunkCount` | 한 index 요청에서 생성된 chunk 수 | starter-ai 기본 pipeline이 기록한다. |

신규 코드는 `chunkIndex`를 우선 기록한다.
기존 저장소와 정렬 쿼리 호환을 위해 `VectorStorePort`의 default adapter는 `chunkIndex`가 있으면 `chunkOrder`도 보강한다.
`content-embedding-pipeline`의 구조화 색인 경로는 `ChunkMetadata.order`를 `chunkIndex`/`chunkOrder`로 매핑한다.
`starter-ai` 기본 fallback pipeline은 반환된 chunk list의 순서를 기준으로 `chunkIndex`/`chunkOrder`를 기록한다.

### Provenance and structure

| key | 표준 의미 | 생성/소비 기준 |
|---|---|---|
| `headingPath` | 문서 section 경로 | structured chunking과 `VectorSearchHit` provenance에서 사용한다. |
| `sourceRef` | 단일 source 위치 참조 | page/block/table 등 원문 위치를 표현한다. |
| `sourceRefs` | 복수 source 위치 참조 | 여러 block을 합친 chunk에서 보존할 수 있다. |
| `page` | 1-based page number 또는 parser 제공 page 값 | `VectorSearchHit`은 문자열/숫자를 `Integer`로 변환해 노출한다. |
| `slide` | slide number | `VectorSearchHit`은 문자열/숫자를 `Integer`로 변환해 노출한다. |
| `blockIds` | chunk를 구성한 structured block id 목록 | context expansion과 source trace에 사용한다. |
| `parentBlockId` | parser block hierarchy의 parent id | structured provenance 보존용 key다. |
| `sourceFormat` | PDF, DOCX 등 parser/source format | structured indexing provenance에 사용한다. |
| `confidence` | OCR/parser confidence | 값이 있는 경우 metadata로 보존한다. |

### Embedding and deduplication

| key | 표준 의미 | 생성/소비 기준 |
|---|---|---|
| `contentHash` | chunk text hash | deduplication 또는 idempotency에 사용할 수 있다. |
| `embeddingModel` | embedding 생성 model 이름 | provider가 값을 제공하지 않으면 일부 pipeline은 `unknown` placeholder를 기록한다. |
| `embeddingDimension` | embedding vector dimension | Java `Integer`로 저장될 수 있으나 adapter는 `Number`로 읽어야 한다. |
| `createdAt` | record 생성 시각 | adapter 또는 pipeline이 필요할 때 기록한다. |
| `indexedAt` | index 요청 처리 시각 | content pipeline 같은 조립 모듈이 기록한다. |

`VectorStorePort.existsByContentHash(...)`의 기본 `false`는 미구현 fallback이다.
content hash deduplication이 필요한 adapter는 반드시 override해야 한다.

### Pipeline enrichment

`studio-platform-starter-ai`의 기본 `RagPipelineService` 구현은 색인 문서 metadata에 아래 key를 추가한다.

| key | 설명 |
|---|---|
| `cleaned` | text cleaner가 성공적으로 정제 텍스트를 생성했는지 여부 |
| `cleanerPrompt` | 사용한 cleaner prompt 이름. cleaner 미사용 시 빈 문자열 |
| `originalTextLength` | 원본 추출 텍스트 길이 |
| `indexedTextLength` | 실제 chunking/indexing에 사용한 텍스트 길이 |
| `keywords` | 문서 단위 keyword 목록. `studio.ai.pipeline.keywords.scope=document|both`일 때 기록 |
| `keywordsText` | 문서 단위 keyword를 공백으로 연결한 lexical 검색용 문자열 |
| `chunkKeywords` | chunk 단위 keyword 목록. `scope=chunk|both`일 때 기록 |
| `chunkKeywordsText` | chunk 단위 keyword를 공백으로 연결한 lexical 검색용 문자열 |

Keyword metadata는 trim, blank 제거, case-insensitive 중복 제거를 거친다.
기본 `scope=document`는 기존 동작과 동일하게 문서 단위 keyword만 기록한다.
`scope=chunk` 또는 `both`는 chunk별 keyword를 추가해 긴 파일에서 chunk 의미가 희석되는 문제를 줄이는 기반을 제공한다.
구체적인 hybrid ranking과 text search config는 vector store adapter 문서를 따른다.

## RAG retrieval filters

`MetadataFilter`는 RAG와 vector 검색 요청에서 객체 범위 metadata convention을 표준화한다.
현재 표준 필드는 `objectType`과 `objectId`이며, 기존 `searchByObject(...)` API는 하위 호환을 위해 유지한다.
신규 호출자는 `RagSearchRequest` 또는 `VectorSearchRequest`에 `MetadataFilter.objectScope(...)`를 담아 같은 객체 범위 검색을 요청할 수 있다.
`MetadataFilter.of(...)`의 `equalsCriteria`에 `objectType` 또는 `objectId` key가 있으면 동일한 object scope convention으로 해석되어 `hasObjectScope()`가 `true`를 반환한다.

중복 계약은 만들지 않는다.

- 새 `EmbeddingPort` 또는 `VectorStorePort` 대신 기존 포트를 확장한다.
- 새 chunking 계약은 이 모듈에 추가하지 않는다. `core.chunk.TextChunk`와 `core.chunk.TextChunker`는 deprecated legacy fallback이며, 신규 구현은 `studio-platform-chunking`의 `Chunk`, `ChunkingContext`, `ChunkingOrchestrator`를 사용한다.
- `VectorRecord`는 RAG chunk 저장을 표현하는 core vector storage 모델이다. 신규 호출자는 긴 생성자 대신 `VectorRecord.builder()`를 우선 사용한다.
- object-scoped RAG chunk 교체는 신규 호출자에서 `VectorStorePort.replaceRecordsByObject(...)`를 우선 사용한다. 기본 구현은 `VectorRecord.toVectorDocument()`로 변환해 기존 `replaceByObject(...)`에 위임한다.
- `chunkIndex`, `previousChunkId`, `nextChunkId`, `tenantId`, `createdAt`, `indexedAt`은 표준 metadata key로만 정의한다. first-class field가 아니므로 `metadata` map을 통해 전달한다.
- `embeddingDimension`은 `Number` metadata로 소비해야 한다. 현재 `VectorRecord.toMetadata()`는 Java `Integer` 값을 저장하지만 adapter는 DB/driver별 숫자 타입 차이를 고려해 `Number`로 읽어야 한다.
- `VectorSearchRequest.includeText=false`이면 `VectorSearchHit.text()`는 `null`일 수 있고, `includeMetadata=false`이면 `metadata()`는 empty map일 수 있다.
- `VectorStorePort.searchWithFilter(...)`는 filtered-search override를 위한 확장점이며 기본 구현은 `searchRecords(...)`에 위임한다.
- `VectorStorePort.existsByContentHash(...)`의 기본 `false`는 미구현 fallback이다. content hash deduplication이 필요한 adapter는 반드시 override해야 한다.
- 기존 `VectorDocument`와 `VectorSearchResult`는 기존 호출자 호환성을 위해 유지한다.
- 새 context assembly 계약은 아직 만들지 않는다. web context 조립은 `starter-ai-web`의 `RagContextBuilder`, chunk 주변 문맥 확장은 `studio-platform-chunking`의 `ChunkContextExpander`를 우선 사용한다.

## Chat metadata
`ChatResponse.metadata()` map은 기존 호환성을 위해 유지한다. 신규 코드는 `ChatResponse.typedMetadata()`로 표준 metadata를 타입 안전하게 읽을 수 있다.

| key | 타입 | 설명 |
|---|---|---|
| `tokenUsage` | `TokenUsage` 또는 map | 입력/출력/전체 token 사용량 |
| `latencyMs` | `Long` | provider 호출 latency |
| `provider` | `String` | 실제 사용 provider |
| `resolvedModel` | `String` | 최종 선택된 model 이름 |
| `memoryUsed` | `Boolean` | conversation memory 사용 여부 |
| `conversationId` | `String` | 연결된 conversation 식별자 |

기존 `modelName`, `tokenUsage` map 등 legacy metadata key는 제거하지 않는다. `resolvedModel`이 없으면 typed view는 `modelName`을 fallback으로 사용할 수 있다.

## Chat streaming
`ChatPort.stream(ChatRequest)`는 provider-neutral stream 계약이다. 기본 구현은 기존 `chat(ChatRequest)` 결과를 `delta`, `usage`, `complete` event sequence로 변환하는 fallback이다. native streaming provider는 이 메서드를 override하면 된다.

stream event type:

- `delta`: assistant text 조각
- `usage`: token/latency/provider metadata
- `complete`: stream 완료
- `error`: provider 호출 실패

event payload는 `ChatStreamEvent.toMap()` 기준으로 비어 있지 않은 필드만 포함한다.
예를 들어 기본 fallback stream은 아래 순서를 보장한다. `requestId`는 HTTP SSE adapter가 추가하는 web 전용 필드이므로 core event에는 포함되지 않는다.

```jsonl
{"type":"delta","delta":"답변 조각","model":"gpt-4o-mini","metadata":{"provider":"OPENAI","resolvedModel":"gpt-4o-mini"}}
{"type":"usage","metadata":{"tokenUsage":{"inputTokens":10,"outputTokens":5,"totalTokens":15},"latencyMs":120}}
{"type":"complete","model":"gpt-4o-mini","metadata":{"provider":"OPENAI","resolvedModel":"gpt-4o-mini"}}
```

이 계약은 Reactor, Spring Web, SSE 구현체에 의존하지 않는다. HTTP `text/event-stream` 변환은 web starter 책임이다.

## Conversation contracts
`ConversationRepositoryPort`는 conversation platform 구현을 위한 저장소 포트다. 이 모듈은 JPA/JDBC/InMemory 구현을 제공하지 않고, 아래 동작에 필요한 중립 계약만 제공한다.

- conversation 목록/상세/삭제
- message 저장과 조회
- assistant 응답 regenerate를 위한 replacement
- `truncate`, `fork`, `compact`, `cancel` 최소 연산

`ChatConversationSummary`는 목록 API에서 필요한 `conversationId`, `ownerId`, `title`, `summary`, `messageCount`, `lastUpdatedAt`, `status`를 표현한다.
message 조회는 장기 conversation을 고려해 `offset`/`limit` 기반 페이지네이션을 계약에 포함한다.
`fork`는 호출자가 새 conversation id를 제공하는 방식이며, 구현체는 중복 `newConversationId`를 거부해야 한다.

## RAG diagnostics
`studio-platform-starter-ai`의 기본 RAG 구현은 diagnostics가 활성화된 경우 마지막 RAG 검색의 strategy,
result count, score threshold, hybrid weight, object scope, topK를 `RagRetrievalDiagnostics`로 기록한다.
진단 metadata에는 chunk 본문을 포함하지 않는다.
Web API에서 client debug 노출 여부는 `studio-platform-starter-ai-web` 설정이 결정한다.

## RAG index job contracts

RAG 운영 화면은 `RagIndexJobService`와 `RagIndexJobRepository` 계약으로 색인 작업 상태와 로그를 조회한다.
이 모듈은 계약만 제공하며, 기본 in-memory 구현은 `starter:studio-platform-starter-ai`가 제공한다.
영구 저장소나 분산 실행이 필요하면 repository/service 구현을 교체한다.

`RagPipelineService.index(RagIndexRequest, RagIndexProgressListener)`는 기존 `index(request)`를 깨지 않는
default wrapper로 추가됐다. 구현체는 listener를 통해 `CHUNKING`, `EMBEDDING`, `INDEXING`,
`COMPLETED` 단계와 chunk/embedded/indexed count, warning/error log를 전달할 수 있다.
상태(`PENDING`, `RUNNING`, `SUCCEEDED`, `WARNING`, `FAILED`, `CANCELLED`)와 단계는 별도 필드다.
`WARNING`은 색인은 완료됐지만 경고 로그가 있는 상태로 사용한다.

## 구현 분리 원칙
이 모듈은 구현체를 포함하지 않는다. `ai.core`는 provider 구현과 DB 구현에 의존하지 않는 계약 계층으로 유지한다.
의존성 역전 원칙에 따라 애플리케이션은 `ChatPort` 등 포트만 참조하며, 공급자별 어댑터는 스타터 모듈이 조건부로 등록한다.
공급자를 교체하거나 추가할 때 이 모듈을 수정할 필요가 없다. Adapter 구현과 RAG pipeline 구현 변경은 이 core 계약 문서의 범위 밖이다.

## 사용법
- `studio-platform-starter-ai` 의존성 추가 (런타임 구현 포함)
- `AiProviderRegistry`에서 공급자 이름으로 `ChatPort` / `EmbeddingPort` 조회
- `RagPipelineService` bean으로 공통 RAG 인덱싱/검색 기능 사용

## 의존성 추가
```kotlin
dependencies {
    implementation(project(":studio-platform-ai"))
    // 런타임 구현은 스타터 사용
    runtimeOnly(project(":starter:studio-platform-starter-ai"))
}
```

## 사용 예시
```java
// 레지스트리에서 포트 조회
ChatPort chat = aiProviderRegistry.chatPort("openai");
ChatResponse response = chat.chat(ChatRequest.builder()
    .messages(messages)
    .build());
ChatResponseMetadata metadata = response.typedMetadata();

// RAG 인덱싱
ragPipelineService.index(RagIndexRequest.builder()
    .documentId("doc-001")
    .text(fullText)
    .metadata(Map.of("objectType", "article", "objectId", "42"))
    .build());

// RAG 검색
List<RagSearchResult> results = ragPipelineService.searchByObject(
    new RagSearchRequest("검색어", 5), "article", "42");

// filter 기반 RAG 검색
List<RagSearchResult> filtered = ragPipelineService.search(
    new RagSearchRequest("검색어", 5, MetadataFilter.objectScope("article", "42")));
```

## 관련 모듈
- `studio-platform-starter-ai` — 공급자별 어댑터·벡터 스토어 자동 구성 (OpenAI, Ollama, Gemini, pgvector)
- `studio-platform-chunking` / `studio-platform-starter-chunking` — RAG indexing용 chunking 계약과 기본 전략 구현
- `studio-platform-starter-ai-web` — AI HTTP 엔드포인트 노출 (chat, embedding, RAG, vector)
- `studio-platform-textract` / `starter:studio-platform-textract-starter` — 파일 텍스트 추출(`FileContentExtractionService`)로 RAG 인덱싱 전처리
- `studio-application-modules/content-embedding-pipeline` — 이 모듈의 포트를 활용해 첨부파일 임베딩·RAG 인덱싱을 수행하는 소비자

## 스키마
마이그레이션 파일 위치: `src/main/resources/schema/ai/{postgres,mysql,mariadb}/V600__create_vector_tables.sql`

Flyway 버전 범위는 `docs/flyway-versioning.md`의 ai 범위(V600-V699)를 따른다.
