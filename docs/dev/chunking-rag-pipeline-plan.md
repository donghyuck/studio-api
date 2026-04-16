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

## 8. 병렬 작업 체크리스트

### 병렬 작업 1: Contract

- [ ] `studio-platform-chunking` 생성
- [ ] core model 작성
- [ ] compile/test 통과
- [ ] README 초안 작성

### 병렬 작업 2: Strategy 구현

- [ ] `starter-chunking` 생성
- [ ] `FixedSizeChunker` 구현
- [ ] `RecursiveChunker` 구현
- [ ] `DefaultChunkingOrchestrator` 구현
- [ ] properties binding 테스트

### 병렬 작업 3: RAG 연결

- [ ] `DefaultRagPipelineService` optional orchestrator 주입
- [ ] fallback 유지
- [ ] metadata 병합 테스트
- [ ] 기존 RAG 검색 테스트 통과

### 병렬 작업 4: Attachment 회귀

- [ ] attachment RAG index 테스트 확인
- [ ] text extraction 재사용 확인
- [ ] 기존 endpoint contract 변경 없음 확인

### 병렬 작업 5: 문서/검증

- [ ] 신규 chunking starter README
- [ ] AI starter README 갱신
- [ ] AI web README에 책임 경계 명시
- [ ] CHANGELOG 갱신
- [ ] 전체 validation command 기록

## 9. 최종 권장안

- `starter-ai-web`에는 구현을 넣지 않는다.
- Chunking은 `studio-platform-chunking` + `starter-chunking`으로 분리한다.
- RAG orchestration은 `starter-ai`의 `DefaultRagPipelineService`에서 계속 담당한다.
- 기존 문서 파싱, embedding, vector storage는 재사용한다.
- Phase 1에서는 API/DB 변경 없이 metadata 확장만 적용한다.
- Semantic/Llm chunking은 Phase 2에서 opt-in으로 도입한다.

## 10. 바로 구현 가능한 첫 작업 목록

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
