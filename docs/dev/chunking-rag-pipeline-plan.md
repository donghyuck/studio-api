# Chunking Starter + Spring AI Retrieval Pipeline 병렬 구현 계획

## 1. 문서 위치와 목적

- 목적: 기존 AI/RAG 구조를 유지하면서 chunking 기능을 starter 형태로 분리하고, Spring AI 기반 Chunk → Embedding → Retrieval pipeline을 점진 도입하기 위한 실행 체크리스트를 제공한다.
- 구현 원칙: `starter-ai-web`에는 구현을 넣지 않고 web adapter 역할만 유지한다.
- 병렬화 원칙: 계약 모듈, chunking 구현, AI pipeline 연결, 문서/테스트를 분리해 독립 작업 lane으로 진행한다.

## 2. 현재 구조 요약

- `studio-platform-ai`: AI/RAG 계약 모듈
  - `EmbeddingPort`, `VectorStorePort`, `RagPipelineService`, `RagIndexRequest`, `RagSearchRequest`, `VectorDocument`
- `starter:studio-platform-starter-ai`: AI/RAG 구현 모듈
  - Spring AI adapter, `DefaultRagPipelineService`, `PgVectorStoreAdapterV2`, `OverlapTextChunker`
- `starter:studio-platform-starter-ai-web`: HTTP adapter
  - chat, rag chat, embedding, vector, rag, query rewrite endpoint
  - 앞으로 chunking/retrieval 구현 추가 금지
- `studio-platform-data`: 문서/파일 파싱
  - `FileContentExtractionService`, `FileParser`, PDF/DOCX/PPTX/HTML/TXT/Image parser
- `studio-application-modules:content-embedding-pipeline`: attachment 기반 RAG indexing API
  - attachment → text extraction → `RagPipelineService.index()`

## 3. 체크리스트

### 설계 체크리스트

- [ ] `starter-ai-web`는 controller, DTO, exception mapping만 담당한다.
- [ ] chunking 계약은 web/AI 구현과 분리된 모듈에 둔다.
- [ ] chunking 구현은 starter 형태로 자동 구성한다.
- [ ] 기본 chunking 전략은 `recursive`로 한다.
- [ ] `fixed-size`, `structure-based`, `semantic`, `llm-based` 전략은 확장 가능하게 둔다.
- [ ] 기존 `TextChunker`/`OverlapTextChunker` 소비자는 깨지지 않게 fallback 또는 deprecated bridge를 둔다.
- [ ] 기존 `FileContentExtractionService`는 재사용한다.
- [ ] 기존 `EmbeddingPort`와 `VectorStorePort`는 유지한다.
- [ ] 기존 `tb_ai_document_chunk` schema는 Phase 1에서 변경하지 않는다.
- [ ] chunk metadata는 기존 `metadata` JSON에 additive로 저장한다.

### 구현 체크리스트

- [ ] `studio-platform-chunking` 모듈 추가
- [ ] `starter:studio-platform-starter-chunking` 모듈 추가
- [ ] `settings.gradle.kts`에 신규 모듈 include
- [ ] `Chunk`, `ChunkMetadata`, `ChunkingContext`, `Chunker`, `ChunkingOrchestrator`, `ChunkingStrategyType` 추가
- [ ] `FixedSizeChunker` 구현
- [ ] `RecursiveChunker` 구현
- [ ] `DefaultChunkingOrchestrator` 구현
- [ ] `ChunkingProperties` 추가
- [ ] `ChunkingAutoConfiguration` 추가
- [ ] `DefaultRagPipelineService`가 `ChunkingOrchestrator`를 optional로 사용하도록 수정
- [ ] `RagPipelineConfiguration`에서 `ChunkingOrchestrator` 주입
- [ ] `studio.ai.pipeline.chunk-size/chunk-overlap` legacy alias 유지
- [ ] `studio.chunking.*` 신규 설정 문서화
- [ ] `CHANGELOG.md` 갱신
- [ ] README 갱신

### 검증 체크리스트

- [ ] `:studio-platform-chunking:test`
- [ ] `:starter:studio-platform-starter-chunking:test`
- [ ] `:starter:studio-platform-starter-ai:test`
- [ ] `:starter:studio-platform-starter-ai-web:test`
- [ ] `:studio-application-modules:content-embedding-pipeline:test`
- [ ] `:studio-platform-ai:test`
- [ ] `git diff --check`

## 4. 병렬 구현 Lane

### 의존 방향 결정

Phase 1의 `studio-platform-chunking`과 `starter:studio-platform-starter-chunking`은 AI provider, Spring AI,
JDBC, pgvector에 의존하지 않는다. 이 starter는 `FixedSize`, `Recursive`, `StructureBased`처럼
순수 텍스트/구조 기반 chunking까지만 담당한다.

`SemanticChunker`와 `LlmChunker`는 `EmbeddingPort`, `ChatPort`, `PromptRenderer`가 필요하므로
Phase 2에서 `starter:studio-platform-starter-ai` 또는 별도 AI 연계 starter에 둔다. 순수 chunking
starter가 AI runtime 의존성을 끌어오지 않도록 한다.

`studio-platform-ai`는 기존 RAG/AI 계약을 유지한다. Phase 1에서는 `starter:studio-platform-starter-ai`가
chunking 계약에 의존하고, `studio-platform-ai`가 `studio-platform-chunking`에 직접 의존하지 않는 방향을
우선한다.

### 병렬 구현 충돌 회피 규칙

Lane A가 `studio-platform-chunking` 계약과 `settings.gradle.kts` include를 먼저 병합한 뒤 Lane B/C를
분기한다. `settings.gradle.kts`, 공통 `build.gradle.kts`, `CHANGELOG.md`, 최상위 README는 integration
owner만 수정한다.

Lane B는 chunking starter 내부 구현에 집중하고, Lane C는 `starter-ai`의 `DefaultRagPipelineService`와
`RagPipelineConfiguration` 연결에 집중한다. `DefaultRagPipelineService`, `RagPipelineConfiguration`은
Lane C 소유 파일로 고정한다.

### Lane A: Chunking 계약 모듈

담당 범위:

- `studio-platform-chunking`
- 순수 계약/DTO만 작성
- Spring, Spring AI, JDBC 의존 금지

작업:

- `Chunk`
- `ChunkMetadata`
- `ChunkingContext`
- `Chunker`
- `ChunkingOrchestrator`
- `ChunkingStrategyType`
- 단위 테스트

완료 조건:

- 모듈 compile/test 통과
- 다른 모듈 의존 없이 독립 동작
- metadata 필드가 `sourceDocumentId`, `parentId`, `section`, `order`, `strategy`를 포함

### Lane B: Chunking Starter 구현

담당 범위:

- `starter:studio-platform-starter-chunking`
- strategy 구현과 auto-configuration

작업:

- `ChunkingProperties`
- `ChunkingAutoConfiguration`
- `FixedSizeChunker`
- `RecursiveChunker`
- `DefaultChunkingOrchestrator`
- 기존 `OverlapTextChunker`와 동작 호환 확인

완료 조건:

- 기본 strategy가 `recursive`
- `studio.chunking.strategy=fixed-size|recursive` 설정 지원
- overlap 정책 테스트 통과
- 빈 설정이 없으면 recursive 기본값으로 동작

### Lane C: AI/RAG Pipeline 연결

담당 범위:

- `starter:studio-platform-starter-ai`
- `DefaultRagPipelineService`
- `RagPipelineConfiguration`

작업:

- `DefaultRagPipelineService`에 `ChunkingOrchestrator` optional dependency 추가
- orchestrator가 있으면 새 `Chunk` 사용
- 없으면 기존 `TextChunker` fallback
- `ChunkMetadata`를 `VectorDocument.metadata`에 병합
- 기존 metadata는 `putIfAbsent` 원칙으로 보존
- `RagPipelineProperties.chunkSize/chunkOverlap`는 legacy alias로 유지

완료 조건:

- 기존 RAG index/search 테스트 통과
- attachment RAG index 테스트 통과
- metadata에 `strategy`, `sourceDocumentId`, `order` 저장
- 기존 API request/response 변경 없음

### Lane D: 문서 파싱/Attachment 연동 확인

담당 범위:

- `studio-platform-data`
- `studio-application-modules:content-embedding-pipeline`

작업:

- `FileContentExtractionService` 재사용 확인
- `AttachmentEmbeddingPipelineController` API 변경 없음 확인
- `AttachmentRagIndexRequestDto`는 Phase 1에서 변경하지 않음
- 구조 기반 chunking용 `ParsedDocument` 도입 여부는 Phase 2로 분리

완료 조건:

- attachment RAG index 기존 테스트 통과
- 첨부 기반 RAG flow 문서 현행화
- 파일 파싱 중복 구현 없음

### Lane E: Web Adapter 정리

담당 범위:

- `starter:studio-platform-starter-ai-web`

작업:

- chunking 구현 추가 금지
- `Chunker`, `ChunkingOrchestrator`, document parsing, embedding batch orchestration, `VectorStorePort.upsert` 구현 추가 금지
- endpoint 문서에서 chunking 설정은 `starter-chunking`으로 링크
- 기존 endpoint 테스트 유지
- 필요 시 “web starter는 adapter only” 문구 README에 추가

완료 조건:

- `starter-ai-web`에 신규 chunking/retrieval 구현 클래스 없음
- web 테스트 통과
- endpoint contract 변경 없음

## 5. 작업 순서

1. Lane A 먼저 진행
   - 계약이 안정되어야 Lane B/C가 병렬 진행 가능하다.
2. Lane B와 Lane C를 병렬 진행
   - Lane B는 chunking 구현
   - Lane C는 `ChunkingOrchestrator` optional 연결부 준비
   - C는 A의 interface만 필요하고 B 구현 완료를 기다리지 않아도 된다.
3. Lane D 병렬 검증
   - 기존 attachment/text extraction 흐름이 깨지지 않는지 확인한다.
   - API 변경 없이 RAG metadata만 확장되는지 검증한다.
4. Lane E는 마지막에 확인
   - web starter에 구현이 새로 들어가지 않았는지 점검한다.
   - 문서와 테스트만 보강한다.

## 6. Phase별 구현 계획

### Phase 1: 최소 동작 구조

목표:

- chunking을 별도 starter로 분리 가능한 구조로 만든다.
- 기본 recursive chunking을 RAG indexing에 연결한다.
- 기존 API와 DB schema를 유지한다.

작업:

- `studio-platform-chunking` 추가
- `starter:studio-platform-starter-chunking` 추가
- `FixedSizeChunker`, `RecursiveChunker`, `DefaultChunkingOrchestrator` 구현
- `DefaultRagPipelineService`에 optional `ChunkingOrchestrator` 연결
- 기존 `TextChunker` fallback 유지
- chunk metadata를 vector metadata에 추가

리스크:

- chunk id/order 변경으로 기존 deterministic 테스트가 깨질 수 있다.
- metadata key 충돌 가능성이 있으므로 기존 값 보존 필요

기대 효과:

- chunking 책임이 RAG 구현에서 분리된다.
- starter 형태 확장이 가능해진다.

### Phase 2: 전략 확장 / 품질 개선

목표:

- 구조 기반, semantic, LLM 기반 chunking을 선택적으로 조합한다.

작업:

- `StructureBasedChunker`
- `SemanticChunker`
- `LlmChunker`
- semantic은 `EmbeddingPort` 사용
- LLM은 `ChatPort`/`PromptRenderer` 사용
- `studio.chunking.structure.*`, `studio.chunking.semantic.*`, `studio.chunking.llm.*` 설정 추가
- 필요 시 `AttachmentRagIndexRequestDto`에 strategy override 추가

리스크:

- semantic/LLM 전략은 비용과 latency가 증가한다.
- LLM output parsing 실패 시 fail-open 정책 필요

기대 효과:

- 긴 문서와 구조 있는 문서의 retrieval 품질 개선

### Phase 3: 운영형 고도화

목표:

- 대량 문서 indexing, 재색인, 관찰 가능성을 강화한다.

작업:

- indexing job 모델 검토
- re-index API 검토
- chunking diagnostics 추가
- chunk quality metrics 추가
- document/index versioning 검토
- async/batch processing 검토

리스크:

- DB migration과 운영 플로우 설계가 필요하다.
- 배치 실패/재시도 정책이 필요하다.

기대 효과:

- 운영 환경에서 안정적인 대량 RAG indexing 가능

## 7. 파일/클래스 단위 변경 계획

신규 모듈:

- `studio-platform-chunking/build.gradle.kts`
- `starter/studio-platform-starter-chunking/build.gradle.kts`

신규 계약:

- `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/Chunk.java`
- `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/ChunkMetadata.java`
- `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/ChunkingContext.java`
- `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/Chunker.java`
- `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/ChunkingOrchestrator.java`
- `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/ChunkingStrategyType.java`

신규 구현:

- `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/service/FixedSizeChunker.java`
- `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/service/RecursiveChunker.java`
- `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/service/DefaultChunkingOrchestrator.java`
- `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/autoconfigure/ChunkingProperties.java`
- `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/autoconfigure/ChunkingAutoConfiguration.java`

수정:

- `settings.gradle.kts`
- `starter/studio-platform-starter-ai/build.gradle.kts`
- `starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/service/pipeline/DefaultRagPipelineService.java`
- `starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/autoconfigure/config/RagPipelineConfiguration.java`
- `starter/studio-platform-starter-ai/README.md`
- `starter/studio-platform-starter-ai-web/README.md`
- `CHANGELOG.md`

유지:

- `starter/studio-platform-starter-ai-web` controller 구조
- `AttachmentEmbeddingPipelineController`
- `FileContentExtractionService`
- `PgVectorStoreAdapterV2`
- `tb_ai_document_chunk`

## 8. 상세 변경/추가 파일 목록

### Phase 1 신규 파일

| 경로 | 목적 |
|---|---|
| `studio-platform-chunking/build.gradle.kts` | chunking 계약 모듈 빌드 정의 |
| `studio-platform-chunking/README.md` | 계약 모듈 역할과 사용법 문서 |
| `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/Chunk.java` | chunk content와 metadata를 담는 불변 모델 |
| `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/ChunkMetadata.java` | parent/section/order/source/strategy metadata 모델 |
| `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/ChunkingContext.java` | chunking 입력 context와 옵션 전달 모델 |
| `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/Chunker.java` | 전략 구현체 공통 인터페이스 |
| `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/ChunkingOrchestrator.java` | 전략 선택/조합 facade |
| `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/ChunkingStrategyType.java` | `FIXED_SIZE`, `RECURSIVE`, `STRUCTURE_BASED`, `SEMANTIC`, `LLM_BASED` enum |
| `studio-platform-chunking/src/test/java/studio/one/platform/chunking/core/ChunkMetadataTest.java` | metadata 기본값/immutability 검증 |
| `starter/studio-platform-starter-chunking/build.gradle.kts` | chunking 구현 starter 빌드 정의 |
| `starter/studio-platform-starter-chunking/README.md` | 설정, 기본 전략, 병렬 구현 lane 문서 |
| `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/autoconfigure/ChunkingAutoConfiguration.java` | chunker/orchestrator 자동 구성 |
| `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/autoconfigure/ChunkingProperties.java` | `studio.chunking.*` 설정 바인딩 |
| `starter/studio-platform-starter-chunking/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Spring Boot auto-configuration 등록 |
| `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/service/FixedSizeChunker.java` | 길이 기반 fallback chunker |
| `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/service/RecursiveChunker.java` | 기본 recursive chunker |
| `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/service/DefaultChunkingOrchestrator.java` | 기본 전략 선택/조합 구현 |
| `starter/studio-platform-starter-chunking/src/test/java/studio/one/platform/chunking/service/FixedSizeChunkerTest.java` | fixed-size/overlap 검증 |
| `starter/studio-platform-starter-chunking/src/test/java/studio/one/platform/chunking/service/RecursiveChunkerTest.java` | paragraph/sentence separator fallback 검증 |
| `starter/studio-platform-starter-chunking/src/test/java/studio/one/platform/chunking/autoconfigure/ChunkingAutoConfigurationTest.java` | properties binding과 기본 bean 검증 |

### Phase 1 수정 파일

| 경로 | 변경 내용 |
|---|---|
| `settings.gradle.kts` | `:studio-platform-chunking`, `:starter:studio-platform-starter-chunking` include 추가 |
| `studio-platform-ai/build.gradle.kts` | 변경 없음. 기존 AI/RAG 계약 모듈은 chunking 계약에 직접 의존하지 않음 |
| `starter/studio-platform-starter-ai/build.gradle.kts` | chunking 계약 모듈 의존 추가. chunking 구현 starter는 소비 앱이 선택적으로 추가 |
| `starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/service/pipeline/DefaultRagPipelineService.java` | `ChunkingOrchestrator` optional 사용, legacy `TextChunker` fallback 유지 |
| `starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/autoconfigure/config/RagPipelineConfiguration.java` | `ObjectProvider<ChunkingOrchestrator>` 주입, fallback 경로 유지 |
| `starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/autoconfigure/config/RagPipelineProperties.java` | `chunkSize`, `chunkOverlap`를 legacy `TextChunker` fallback 설정으로 유지. 신규 `ChunkingOrchestrator` 경로는 `studio.chunking.*` 사용 |
| `starter/studio-platform-starter-ai/src/test/java/studio/one/platform/ai/service/pipeline/RagPipelineServiceTest.java` | chunk metadata 병합, legacy fallback 검증 추가 |
| `starter/studio-platform-starter-ai/src/test/java/studio/one/platform/ai/service/pipeline/RagQualitySmokeTest.java` | recursive chunking 적용 후 기존 검색 품질 유지 검증 |
| `studio-platform-ai/README.md` | chunking 계약 모듈과의 관계 문서화 |
| `starter/studio-platform-starter-ai/README.md` | `starter-chunking` 의존/설정/metadata 설명 추가 |
| `starter/studio-platform-starter-ai-web/README.md` | web starter는 adapter-only라는 책임 경계 추가 |
| `CHANGELOG.md` | Phase 1 문서/구현 변경과 검증 기록 추가 |

### Phase 2 신규/수정 후보

| 경로 | 목적 |
|---|---|
| `starter/studio-platform-starter-chunking/src/main/java/studio/one/platform/chunking/service/StructureBasedChunker.java` | heading/page/table/paragraph boundary 기반 chunking |
| `starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/service/chunk/SemanticChunker.java` | `EmbeddingPort` 기반 semantic refinement 후보 |
| `starter/studio-platform-starter-ai/src/main/java/studio/one/platform/ai/service/chunk/LlmChunker.java` | `ChatPort`/`PromptRenderer` 기반 LLM refinement 후보 |
| `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/ParsedDocument.java` | 구조 기반 chunking 입력 모델 후보 |
| `studio-platform-chunking/src/main/java/studio/one/platform/chunking/core/DocumentSection.java` | section/page/table metadata 모델 후보 |
| `studio-application-modules/content-embedding-pipeline/src/main/java/studio/one/application/web/controller/AttachmentRagIndexRequestDto.java` | 선택적 strategy override 추가 후보 |

## 9. 관련 DB 테이블 및 스키마 영향

### 현재 사용 테이블

| 테이블 | 위치 | 현재 역할 |
|---|---|---|
| `tb_ai_document_chunk` | `studio-platform-ai/src/main/resources/schema/ai/postgres/V600__create_vector_tables.sql` | chunk text, embedding, metadata 저장 |

현재 컬럼:

| 컬럼 | 현재 용도 | Phase 1 변경 |
|---|---|---|
| `id` | surrogate key | 변경 없음 |
| `object_type` | 원본 객체 타입. 예: `attachment` | 변경 없음 |
| `object_id` | 원본 객체 ID | 변경 없음 |
| `chunk_index` | 문서 내 chunk 순서 | `ChunkMetadata.order`와 동일한 값을 유지 |
| `text` | chunk 본문 | 변경 없음 |
| `embedding` | pgvector embedding | 변경 없음 |
| `metadata` | JSONB metadata | chunk metadata additive 저장 |
| `created_at` | 생성 시각 | 변경 없음 |

현재 인덱스/제약:

| 이름 | 용도 | Phase 1 변경 |
|---|---|---|
| `idx_ai_chunk_object` | `object_type`, `object_id` 조회 최적화 | 변경 없음 |
| `idx_ai_chunk_vector` | vector cosine 검색 | 변경 없음 |
| `uq_ai_chunk` | `object_type`, `object_id`, `chunk_index` upsert 보장 | 변경 없음 |

### Phase 1 metadata 추가 키

Phase 1에서는 DB migration을 추가하지 않고 `metadata` JSONB에 아래 key를 additive로 넣는다.

| key | 값 출처 | 설명 |
|---|---|---|
| `sourceDocumentId` | `ChunkingContext.sourceDocumentId` | chunking 기준 문서 ID |
| `parentId` | `ChunkMetadata.parentId` | parent chunk/section ID. 없으면 null 또는 미저장 |
| `section` | `ChunkMetadata.section` | heading/page/section 이름 |
| `order` | `ChunkMetadata.order` | chunk 순서. `chunk_index`와 동일하게 유지 |
| `strategy` | `ChunkingStrategyType` | 실제 사용 전략. 기본 `RECURSIVE` |
| `chunkUnit` | `ChunkingContext.unit` | `CHARACTER` 또는 `TOKEN` |
| `chunkSize` | `ChunkingContext.maxChunkSize` | 적용된 chunk size |
| `overlap` | `ChunkingContext.overlap` | 적용된 overlap |

저장 규칙:

- 기존 metadata key는 보존한다.
- 신규 chunk metadata는 `putIfAbsent`를 우선 사용한다.
- null 또는 blank 값은 저장하지 않는다.
- `chunkOrder`는 Phase 1의 canonical order key로 유지하며 `chunk_index` 산정에도 계속 사용한다.
- `ChunkMetadata.order`를 사용하는 경우 반드시 `chunkOrder`와 동일한 값으로 매핑한다.

보존해야 할 기존 metadata key:

| key | 현재 출처 |
|---|---|
| `documentId` | `RagIndexRequest.documentId` |
| `chunkId` | 기존 `TextChunk.id()` 또는 신규 `Chunk.id()` |
| `chunkOrder` | 기존 RAG pipeline 순서 |
| `chunkLength` | chunk content 길이 |
| `objectType` | 호출자 metadata 또는 attachment 기본값 |
| `objectId` | 호출자 metadata 또는 attachment ID |
| `keywords`, `keywordsText` | keyword extraction |
| `chunkKeywords`, `chunkKeywordsText` | chunk-level keyword extraction |
| `cleaned`, `cleanerPrompt`, `originalTextLength`, `indexedTextLength`, `chunkCount` | cleaner/RAG pipeline |

### 재색인 replace 정책

Phase 1에서 DB schema는 변경하지 않지만, 동일 `objectType/objectId`를 다시 index할 때 stale chunk가
남지 않아야 한다. 신규 chunking 전략이 기존보다 적은 chunk를 만들 수 있으므로, RAG index 흐름은
동일 object scope의 기존 chunk를 삭제한 뒤 새 chunk를 upsert하는 replace semantics를 따른다.

이를 위해 `VectorStorePort`에 `deleteByObject(String objectType, String objectId)` 또는
`replaceByObject(String objectType, String objectId, List<VectorDocument> documents)` 추가를 검토한다.
`PgVectorStoreAdapterV2`에서 우선 구현한다.

### DB dialect 영향

Phase 1은 `tb_ai_document_chunk` schema를 변경하지 않는다. 이 결정은 PostgreSQL뿐 아니라
`schema/ai/postgres`, `schema/ai/mysql`, `schema/ai/mariadb`의 모든 V600 schema에 동일하게 적용된다.
추가 chunk 정보는 `metadata` JSON 컬럼에 additive로 저장한다.

### Phase 2/3 DB 후보

Phase 2까지는 기존 `metadata` JSONB 사용을 우선한다. 아래 테이블은 Phase 3 운영 고도화에서만 검토한다.

| 후보 테이블 | 목적 | 도입 조건 |
|---|---|---|
| `tb_ai_document` | 문서 단위 index 상태, 원본 hash, version 관리 | re-index/versioning 필요 시 |
| `tb_ai_index_job` | 비동기 indexing job 상태 관리 | 대량 문서 batch/재시도 필요 시 |
| `tb_ai_chunk_relation` | parent-child chunk 관계 정규화 | hierarchical retrieval이 JSONB metadata만으로 부족할 때 |

## 10. AutoConfiguration 및 전략 동작 기준

### ChunkingAutoConfiguration 기준

- `starter:studio-platform-starter-chunking`은 `AutoConfiguration.imports`로 등록한다.
- `studio.chunking.enabled=false`이면 chunking starter 기본 bean을 등록하지 않는다.
- 기본 bean은 `@ConditionalOnMissingBean`으로 제공한다.
- 사용자가 custom `Chunker` 또는 `ChunkingOrchestrator` bean을 등록하면 custom bean을 우선한다.
- 기본 strategy는 `recursive`다.
- Phase 1에서 지원하는 설정 값은 `studio.chunking.strategy=fixed-size|recursive`로 제한한다.

### RecursiveChunker 동작 기준

- separator 우선순위는 paragraph blank line → newline → sentence punctuation → whitespace → fixed-size 순이다.
- `overlap`은 이전 chunk tail을 다음 chunk prefix에 붙이는 방식으로 적용한다.
- `overlap`은 `maxSize - 1`을 넘지 않는다.
- chunk id는 deterministic하게 생성한다. 기본 형식은 `{sourceDocumentId}-{chunkOrder}`다.
- 동일 입력과 동일 설정은 동일 chunk id/order/content를 생성해야 한다.

### TextChunker 호환 정책

- Phase 1에서는 기존 `TextChunker`와 `OverlapTextChunker`를 유지한다.
- Phase 2에서 `OverlapTextChunker`를 deprecated 처리한다.
- Phase 3에서 외부 사용 여부를 확인한 뒤 제거를 검토한다.

### ChunkingContext 필드 기준

| 필드 | 필수 | 설명 |
|---|---:|---|
| `sourceDocumentId` | 예 | chunk id와 metadata 기준 문서 ID |
| `text` | 예 | chunking 대상 원문 |
| `objectType` | 아니오 | vector metadata의 `objectType` 후보 |
| `objectId` | 아니오 | vector metadata의 `objectId` 후보 |
| `contentType` | 아니오 | parser 또는 structure hint 참고값 |
| `filename` | 아니오 | parser 또는 structure hint 참고값 |
| `maxSize` | 예 | chunk 최대 크기 |
| `overlap` | 예 | chunk overlap 크기 |
| `unit` | 예 | `CHARACTER` 또는 `TOKEN` |
| `metadata` | 아니오 | 호출자가 전달한 부가 metadata |

## 11. 병렬 작업 체크리스트

### 병렬 작업 1: Contract

- [ ] `studio-platform-chunking` 생성
- [ ] core model 작성
- [ ] compile/test 통과
- [ ] README 초안 작성
- [ ] `settings.gradle.kts` include는 Lane A에서만 수정

### 병렬 작업 2: Strategy 구현

- [ ] `starter-chunking` 생성
- [ ] `FixedSizeChunker` 구현
- [ ] `RecursiveChunker` 구현
- [ ] `DefaultChunkingOrchestrator` 구현
- [ ] properties binding 테스트
- [ ] `:starter:studio-platform-starter-chunking:compileJava` 통과

### 병렬 작업 3: RAG 연결

- [ ] `DefaultRagPipelineService` optional orchestrator 주입
- [ ] fallback 유지
- [ ] metadata 병합 테스트
- [ ] 기존 RAG 검색 테스트 통과
- [ ] stale chunk 제거 또는 replace semantics 테스트 추가

### 병렬 작업 4: Attachment 회귀

- [ ] attachment RAG index 테스트 확인
- [ ] text extraction 재사용 확인
- [ ] 기존 endpoint contract 변경 없음 확인
- [ ] `studio-application-modules/content-embedding-pipeline/README.md`에 API 유지와 metadata 확장 설명 추가

### 병렬 작업 5: 문서/검증

- [ ] 신규 chunking starter README
- [ ] AI starter README 갱신
- [ ] AI web README에 책임 경계 명시
- [ ] CHANGELOG 갱신
- [ ] 전체 validation command 기록
- [ ] `:starter:studio-platform-starter-ai:compileJava` 통과
- [ ] `:starter:studio-platform-starter-chunking:compileJava` 통과

## 12. 최종 권장안

- `starter-ai-web`에는 구현을 넣지 않는다.
- Chunking은 `studio-platform-chunking` + `starter-chunking`으로 분리한다.
- RAG orchestration은 `starter-ai`의 `DefaultRagPipelineService`에서 계속 담당한다.
- 기존 문서 파싱, embedding, vector storage는 재사용한다.
- Phase 1에서는 API/DB 변경 없이 metadata 확장만 적용한다.
- Semantic/Llm chunking은 Phase 2에서 `starter-ai` 또는 별도 AI 연계 starter에 opt-in으로 도입한다.

## 13. 바로 구현 가능한 첫 작업 목록

1. `studio-platform-chunking` 모듈 추가
2. `starter:studio-platform-starter-chunking` 모듈 추가
3. `Chunk`, `ChunkMetadata`, `ChunkingContext`, `Chunker`, `ChunkingOrchestrator`, `ChunkingStrategyType` 추가
4. `FixedSizeChunker`, `RecursiveChunker` 구현
5. `ChunkingProperties`, `ChunkingAutoConfiguration` 구현
6. `DefaultRagPipelineService`에 optional `ChunkingOrchestrator` 연결
7. 기존 `TextChunker` fallback 유지
8. chunk metadata를 `VectorDocument.metadata`에 병합
9. `starter-ai-web`에는 구현을 추가하지 않고 README에 adapter-only 원칙 추가
10. 테스트와 CHANGELOG 갱신
