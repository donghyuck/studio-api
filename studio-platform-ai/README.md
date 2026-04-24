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
- `TextChunker`: 긴 문서를 임베딩에 적합한 크기로 분할하는 전략 인터페이스
- `RagPipelineService`: 인덱싱(`index`)과 검색(`search`, `searchByObject`, `listByObject`)을 정의하는 RAG facade 계약
- `TextCleaner` / `KeywordExtractor` / `PromptRenderer`: RAG 전처리와 프롬프트 확장점 계약
- `RagPipelineOptions` 계열: 기본 RAG 구현을 대체하거나 테스트할 때 사용할 설정 계약

## 주요 타입

| 타입 | 패키지 | 설명 |
|---|---|---|
| `ChatPort` | `core.chat` | 챗 완성 요청/응답 계약 |
| `ChatResponseMetadata` | `core.chat` | token usage, latency, provider, resolved model, memory, conversation metadata typed view |
| `ChatStreamEvent` | `core.chat` | streaming chat event 계약 |
| `ConversationRepositoryPort` | `core.chat` | conversation/message 저장소 포트 |
| `ChatConversation` / `ChatConversationMessage` / `ChatConversationSummary` | `core.chat` | conversation API 구현을 위한 provider-neutral 모델 |
| `EmbeddingPort` | `core.embedding` | 텍스트 임베딩 벡터 생성 계약 |
| `VectorStorePort` | `core.vector` | 벡터 저장/검색/하이브리드 검색 계약 |
| `AiProviderRegistry` | `core.registry` | 공급자별 ChatPort/EmbeddingPort 룩업 |
| `TextChunker` | `core.chunk` | 문서를 TextChunk 리스트로 분할 |
| `RagPipelineService` | `service.pipeline` | 인덱싱/검색 RAG facade 계약 |
| `TextCleaner` | `service.cleaning` | 색인 전 추출 텍스트 정제 계약 |
| `KeywordExtractor` | `service.keyword` | 색인/검색 keyword 추출 계약 |
| `PromptRenderer` | `service.prompt` | 프롬프트 렌더링 계약 |
| `RagPipelineOptions` | `service.pipeline` | RAG 구현 설정 계약 |
| `RagRetrievalDiagnostics` | `core.rag` | RAG 검색 fallback 전략과 결과 상태 진단 모델 |
| `AiProvider` | `core` | 지원 공급자 열거형 (OPENAI, OLLAMA, GOOGLE_AI_GEMINI) |

## RAG metadata
`studio-platform-starter-ai`의 기본 `RagPipelineService` 구현은 색인 문서 metadata에 아래 key를 추가한다.
호출자가 같은 key를 전달한 경우 기존 값을 보존한다.

| key | 설명 |
|---|---|
| `cleaned` | text cleaner가 성공적으로 정제 텍스트를 생성했는지 여부 |
| `cleanerPrompt` | 사용한 cleaner prompt 이름. cleaner 미사용 시 빈 문자열 |
| `originalTextLength` | 원본 추출 텍스트 길이 |
| `indexedTextLength` | 실제 chunking/indexing에 사용한 텍스트 길이 |
| `chunkCount` | 생성된 chunk 수 |
| `chunkLength` | 개별 chunk content 길이 |
| `keywords` | 문서 단위 keyword 목록. `studio.ai.pipeline.keywords.scope=document|both`일 때 기록 |
| `keywordsText` | 문서 단위 keyword를 공백으로 연결한 lexical 검색용 문자열 |
| `chunkKeywords` | chunk 단위 keyword 목록. `scope=chunk|both`일 때 기록 |
| `chunkKeywordsText` | chunk 단위 keyword를 공백으로 연결한 lexical 검색용 문자열 |

Keyword metadata는 trim, blank 제거, case-insensitive 중복 제거를 거친다.
기본 `scope=document`는 기존 동작과 동일하게 문서 단위 keyword만 기록한다.
`scope=chunk` 또는 `both`는 chunk별 keyword를 추가해 긴 파일에서 chunk 의미가 희석되는 문제를 줄이는 기반을 제공한다.
현재 PostgreSQL hybrid SQL ranking은 기존 `simple` text search config 동작을 유지한다.

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

이 계약은 Reactor, Spring Web, SSE 구현체에 의존하지 않는다. HTTP `text/event-stream` 변환은 web starter 책임이다.

## Conversation contracts
`ConversationRepositoryPort`는 conversation platform 구현을 위한 저장소 포트다. 이 모듈은 JPA/JDBC/InMemory 구현을 제공하지 않고, 아래 동작에 필요한 중립 계약만 제공한다.

- conversation 목록/상세/삭제
- message 저장과 조회
- assistant 응답 regenerate를 위한 replacement
- `truncate`, `fork`, `compact`, `cancel` 최소 연산

`ChatConversationSummary`는 목록 API에서 필요한 `conversationId`, `ownerId`, `title`, `summary`, `messageCount`, `lastUpdatedAt`, `status`를 표현한다.

## RAG diagnostics
`studio-platform-starter-ai`의 기본 RAG 구현은 diagnostics가 활성화된 경우 마지막 RAG 검색의 strategy,
result count, score threshold, hybrid weight, object scope, topK를 `RagRetrievalDiagnostics`로 기록한다.
진단 metadata에는 chunk 본문을 포함하지 않는다.
Web API에서 client debug 노출 여부는 `studio-platform-starter-ai-web` 설정이 결정한다.

## 구현 분리 원칙
이 모듈은 구현체를 포함하지 않는다. 의존성 역전 원칙에 따라 애플리케이션은 `ChatPort` 등 포트만 참조하며, 공급자별 어댑터는 스타터 모듈이 조건부로 등록한다. 공급자를 교체하거나 추가할 때 이 모듈을 수정할 필요가 없다.

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
