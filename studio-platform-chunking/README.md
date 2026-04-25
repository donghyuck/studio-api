# Studio Platform Chunking

`studio-platform-chunking`은 AI/RAG 색인을 위한 provider-neutral chunking 계약 모듈입니다.

## 책임 범위

- 불변 chunking 요청/결과 모델을 정의합니다.
- 전략 중립적인 chunking 확장 지점을 정의합니다.
- 검색용 child chunk와 context 복구용 parent 관계를 모델링합니다.
- Spring, Spring AI, JDBC, vector store 구현에 의존하지 않습니다.
- embedding API 호출, vector DB 저장, LLM 호출, OCR 실행, 파일 parser 실행을 하지 않습니다.

## 핵심 타입

- `Chunker`: chunking 전략 구현 계약입니다.
- `ChunkingOrchestrator`: 전략 선택과 조합 계약입니다.
- `ChunkingContext`: 텍스트 chunking 입력 컨텍스트입니다.
- `Chunk`: chunk content와 metadata를 담는 불변 결과입니다.
- `ChunkMetadata`: vector indexing 전에 보존할 표준 metadata입니다.
- `ChunkType`: `child`, `parent`, `table`, `ocr` 등 chunk 역할입니다.
- `ChunkingStrategyType`: 지원 전략 식별자입니다.
- `ChunkUnit`: character/token 기준 size 단위입니다.
- `NormalizedDocument`: parser에 독립적인 구조화 문서 입력입니다.
- `NormalizedBlock`: parser에 독립적인 논리 block과 provenance입니다.
- `NormalizedDocumentChunker`: normalized document chunking 확장 계약입니다.
- `ChunkContextExpander`: 검색된 child chunk를 답변 context로 확장하는 계약입니다.
- `ChunkContextExpansionRequest` / `ChunkContextExpansion`: context expansion 요청/결과 모델입니다.

## Metadata 규칙

- metadata map은 defensive copy 됩니다.
- null key, blank key, null value, blank string value는 제거됩니다.
- `chunkOrder`는 Phase 1 호환성을 위한 canonical persisted order key로 유지됩니다.
- `ChunkMetadata.order`는 downstream 저장 시 `chunkOrder`와 같은 값으로 매핑되는 것을 전제로 합니다.
- 구조화 provenance key는 downstream 소비자를 위해 표준화합니다.
- parent-child 관계 key는 additive metadata이며 기존 key를 대체하지 않습니다.
- `parentId`는 legacy compatibility 용도로 유지하며 `parentChunkId`와 다른 의미입니다.

### Metadata Key Reference

| Key | 목적 | 호환성 |
| --- | --- | --- |
| `sourceDocumentId` | 생성된 chunk를 원본 문서 단위로 묶는 식별자입니다. | 기존 key |
| `chunkOrder` | 저장/정렬용 chunk 순서입니다. | 기존 key, order 기준 유지 |
| `strategy` | `recursive`, `structure-based` 등 chunking 전략입니다. | 기존 key |
| `chunkType` | `child`, `parent`, `table`, `ocr`, `image-caption` 등 검색 역할입니다. | 추가 key |
| `parentChunkId` | child chunk가 속한 deterministic parent section id입니다. | 추가 key, `parentId`와 별도 |
| `parentChunkContent` | 답변 시 parent context 복구에 사용할 저장된 section content입니다. | 추가 key |
| `previousChunkId` / `nextChunkId` | 같은 parent section 안의 인접 child chunk 링크입니다. | 추가 key |
| `sourceRef` / `sourceRefs` | parser/source provenance 참조입니다. | 추가 key |
| `blockType`, `blockIds`, `parentBlockId` | 구조화 추출 block identity와 hierarchy입니다. | 추가 key |
| `page`, `slide`, `headingPath`, `sourceFormat` | 위치와 section context입니다. | 추가 key |
| `confidence` | parser/OCR confidence가 있는 경우 보존합니다. | 추가 key |
| `tokenEstimate`, `chunkUnit`, `maxSize`, `overlap` | size 정책과 검증 evidence입니다. | 추가 key |

## 구조화 입력

`NormalizedDocument`와 `NormalizedBlock`은 chunking이 parser 구현에 직접 의존하지 않고 구조화 추출 결과를 소비하기 위한 입력 모델입니다.
기존 텍스트 기반 API는 호환성 기준으로 유지됩니다.

```java
ChunkingContext context = ChunkingContext.builder("plain text")
        .sourceDocumentId("doc-1")
        .build();
```

구조 기반 chunking은 normalized block을 사용합니다.

```java
NormalizedDocument document = NormalizedDocument.builder("doc-1")
        .sourceFormat("PDF")
        .blocks(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "Install")
                        .id("page[1]/h[0]")
                        .order(0)
                        .headingPath("Install")
                        .blockIds(List.of("page[1]/h[0]"))
                        .confidence(0.98d)
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "Install the engine.")
                        .id("page[1]/p[1]")
                        .order(1)
                        .blockIds(List.of("page[1]/p[1]"))
                        .confidence(0.92d)
                        .build()))
        .build();
```

## Parent-Child 모델

호환성을 위해 반환 타입은 계속 `List<Chunk>`이며, 기본 출력은 검색용 child chunk입니다.
parent section chunk는 별도 indexing record로 반환하지 않고 additive metadata link로 표현합니다.

- child chunk는 기본 retrieval 단위입니다.
- parent context는 deterministic `parentChunkId`로 식별합니다.
- `parentChunkContent`는 parent context 복구가 필요한 전략에서 additive metadata로 보존할 수 있습니다.
- `previousChunkId`와 `nextChunkId`는 같은 parent section 안에서만 연결됩니다.
- heading 없이 시작하는 문서도 빈 `section` 값과 body-only `parentChunkContent`로 parent context를 가질 수 있습니다.
- `blockIds`, `headingPath`, `page`, `slide`, `sourceRef`, `confidence`는 context expansion에 필요한 provenance입니다.

## Context Expansion 계약

`ChunkContextExpander`는 검색된 child chunk를 더 큰 답변 context로 확장하는 방법을 정의합니다.
이 계약은 retrieval, embedding, vector store 접근, LLM 호출, parser 실행을 수행하지 않습니다.

`Chunk`는 blank content를 허용하지 않으므로 expansion content도 non-blank입니다.
`availableChunks`는 seed chunk 주변의 작은 pre-filtered 후보 목록이어야 하며, 전체 corpus나 unbounded retrieval 결과를 전달하면 안 됩니다.

```java
ChunkContextExpansionRequest request = ChunkContextExpansionRequest.builder(retrievedChunk)
        .availableChunks(candidateChunks)
        .previousWindow(1)
        .nextWindow(1)
        .includeParentContent(true)
        .build();

ChunkContextExpansion expansion = expander.expand(request);
String answerContext = expansion.content();
```

내장 strategy identifier는 `parent-child`, `window`, `heading`, `table`, `custom`, `unknown`입니다.
구체 구현체는 starter 모듈에 둡니다.

## 하위 호환성

- `ChunkingContext.builder(String text)`는 텍스트 API 기준으로 유지됩니다.
- `Chunker.chunk(ChunkingContext)`는 변경 없이 `List<Chunk>`를 반환합니다.
- 기존 text strategy는 normalized document와 context expansion 계약을 몰라도 됩니다.
- 새 metadata field는 additive입니다. `content`, `chunkOrder`, `strategy`, 기존 custom attribute만 읽는 소비자는 변경이 필요 없습니다.
- `NormalizedDocument`, `NormalizedBlock`, `ChunkContextExpander`는 structure-aware indexing과 answer-time context recovery를 위한 opt-in 계약입니다.

## 의존성 경계

이 모듈은 Spring, Spring AI, JDBC, pgvector, web module에 의존하지 않습니다.
전략 구현체와 auto-configuration은 starter module 책임입니다.
이 모듈은 embedding API, vector store, LLM, OCR engine, file parser를 호출하지 않습니다.
