# Studio Platform Chunking Runtime

`studio-platform-chunking`의 계약을 구현하는 청킹 runtime 모듈이다. recursive, fixed, token,
structure-based, blockify 전략과 tokenizer, 정규화 adapter, context expander를 제공한다.
Spring Bean 조립은 `studio-platform-starter-chunking`이 담당한다.

전체 RAG 흐름은 [AI/RAG 아키텍처 가이드](../docs/ai-rag/README.md)를 먼저 참고한다.

## 책임 범위

이 모듈이 담당하는 것:

- `DefaultChunkingOrchestrator`
- character/token 기반 chunk 구현
- normalized document 기반 구조 청킹
- Blockify 생성·검증·PII masking adapter
- parent/neighbor/table/heading context expansion
- textract 결과를 normalized document로 변환

이 모듈이 담당하지 않는 것:

- 파일 추출
- embedding과 vector 저장
- RAG retrieval와 답변 생성
- HTTP API
- 문서 의미 metadata schema

## 구현 전략

| 구현 | 입력 | 목적 |
|---|---|---|
| `RecursiveChunker` | text | separator 계층을 사용하는 기본 character 청킹 |
| `FixedSizeChunker` | text | 명시적인 고정 길이 청킹 |
| `TokenBasedChunker` | text | tokenizer 기준 size/overlap |
| `StructureBasedChunker` | `NormalizedDocument` | heading/table/list 등 문서 구조 보존 |
| `BlockifyChunker` | `NormalizedDocument` | 검색·질의에 적합한 지식 block 생성 |
| `KnowledgeBlockChunker` | `NormalizedDocument` | Blockify 기반 knowledge block |

기본 runtime 설정은 `recursive`, `character`, `max-size=800`, `overlap=100`이다.
Blockify는 별도 opt-in이며 기본 활성화되지 않는다.

## Orchestrator

`DefaultChunkingOrchestrator`는 요청 strategy/unit과 `ChunkingProperties`를 해석해 적절한 구현을 선택한다.
호출자는 concrete chunker 대신 `ChunkingOrchestrator` 계약에 의존한다.

구조화 입력이 필요한 전략에 text만 전달했거나 구조화 전략을 실행할 수 없는 경우의 fallback은
[Starter Chunking README](../starter/studio-platform-starter-chunking/README.md)에 정의된 현재 정책을 따른다.
새 fallback 체인을 암묵적으로 추가하지 않는다.

## Tokenizer

| 타입 | 역할 |
|---|---|
| `DefaultTokenizerResolver` | provider/model과 설정 mapping으로 tokenizer 선택 |
| `TiktokenTokenizerAdapter` | 지원 encoding/model의 token 계산 |
| `ApproximateTokenizer` | 설정된 fallback 정책에 따른 근사 계산 |

알 수 없는 model을 무조건 특정 tokenizer로 추정하지 않는다.
`studio.chunking.tokenizer.fail-on-unknown-model`과 fallback 설정에 따라 처리한다.

## Blockify

Blockify runtime은 다음 구성요소를 분리한다.

- `BlockifyDocumentTypeClassifier`: Blockify 전용 문서 유형 감지
- `BlockifyProfileResolver`: 유형별 prompt/profile 선택
- `HeuristicBlockifyGenerator`: provider 호출 없는 기본 generator
- `LlmBlockifyGenerator`: 등록된 chat adapter를 사용하는 generator
- `PiiMaskingBlockifyGenerator`: 외부 생성 전 PII masking wrapper
- `BlockifyTypedFieldValidator`: 결과 field와 source evidence 검증

Blockify 문서 유형은 `DocumentSemanticType`과 같지 않다. Blockify 결과는 source evidence를 보존해야
하며 생성된 텍스트가 원문 근거를 대체하지 않는다.

## Context expansion

| 구현 | 확장 범위 |
|---|---|
| `WindowChunkContextExpander` | 이전/다음 chunk |
| `ParentChildChunkContextExpander` | parent/child |
| `TableChunkContextExpander` | 같은 table 또는 주변 table context |
| `HeadingChunkContextExpander` | heading path 기반 context |

expander는 검색 후보를 보강하지만 최종 prompt 한도를 우회하지 않는다. `RagContextBuilder`가 확장 결과를
다시 패킹하고 실제 포함된 span만 `PackedEvidenceSet`에 남긴다.

## 사용

일반 소비 애플리케이션은 runtime 모듈을 직접 추가하기보다 starter를 사용한다.

```kotlin
implementation(project(":starter:studio-platform-starter-chunking"))
```

직접 조립이 필요한 라이브러리 테스트나 커스텀 runtime에서만 다음 의존성을 사용한다.

```kotlin
implementation(project(":studio-platform-chunking-runtime"))
```

## 관련 문서

- [Chunking 계약](../studio-platform-chunking/README.md)
- [Chunking 자동 구성과 전략 설정](../starter/studio-platform-starter-chunking/README.md)
- [RAG 색인](../docs/ai-rag/indexing.md)
- [근거 기반 RAG Chat](../docs/ai-rag/grounded-chat.md)
