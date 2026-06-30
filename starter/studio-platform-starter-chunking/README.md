# Studio Platform Starter Chunking

`studio-platform-starter-chunking`은 Studio RAG indexing에 사용할 chunking runtime 구현체를 Spring Boot에서 자동 등록합니다.
실제 chunking 구현은 `studio-platform-chunking-runtime`에 두고, starter는 auto-configuration과 설정 metadata만 담당합니다.

## 책임 범위

- `studio-platform-chunking-runtime`의 chunking 구현체를 `@ConditionalOnMissingBean` 기반으로 등록합니다.
- 애플리케이션이 bean을 교체할 수 있도록 auto-configuration 경계만 제공합니다.
- Spring AI, embedding, vector storage, web endpoint 책임을 포함하지 않습니다.
- `starter:studio-platform-starter-ai-web`은 HTTP adapter, AI starter는 embedding/vector/RAG 소비자 역할로 남깁니다.

## 지원 전략

Phase 1 지원 전략:

- `recursive` (default)
- `fixed-size`
- `structure-based`
- `blockify` (opt-in, PoC)
- `knowledge-block` (opt-in)

Phase 2 후보이며 이 starter에는 포함하지 않는 전략:

- `semantic` (AI-linked)
- `llm-based` (AI-linked)

`blockify`는 질문·답변 중심 Knowledge Block을 생성하기 위한 PoC 전략입니다.
기본값은 비활성화이며 `studio.chunking.blockify.enabled=true`를 설정한 경우에만 선택할 수 있습니다.
기본 `BlockifyGenerator`는 외부 LLM을 호출하지 않는 deterministic heuristic 구현입니다.
운영 LLM 연동은 `BlockifyGenerator` bean을 교체해 적용합니다.

`knowledge-block`은 동일한 Blockify/IdeaBlock 생성 파이프라인을 사용하되 운영/평가용 metadata 계약을 분리한 전략입니다.
기본값은 비활성화이며 `studio.chunking.knowledge-block.enabled=true`를 설정한 경우에만 선택할 수 있습니다.
결과 chunk는 `knowledge-block-metadata-v1`, `knowledgeBlockFingerprint`, `knowledgeBlockSourceBlockCoverage`,
`knowledgeBlockDistillation*` alias를 함께 저장하므로 기존 `blockify` PoC 결과와 구분해 운영 정책을 적용할 수 있습니다.

## 설정

```yaml
studio:
  chunking:
    enabled: true
    strategy: recursive
    unit: character
    max-size: 800
    overlap: 100
    tokenizer:
      auto-detect: true
      fallback: approximate
      fail-on-unknown-model: false
      mappings:
        text-embedding-3-small:
          provider: tiktoken
          encoding: cl100k_base
    blockify:
      enabled: false
      max-input-tokens: 2500
      max-output-tokens-per-block: 500
      max-blocks-per-section: 10
      max-sections-per-document: 500
      min-answer-chars: 80
      min-chunk-chars: 150
      max-chunk-chars: 900
      min-evidence-chars: 40
      distillation-enabled: true
      distillation-similarity-threshold: 0.82
      generator-type: heuristic
      prompt-version: blockify-v1
      generator-model: heuristic-blockify-v1
      llm-provider: google-ai-gemini
      llm-model: gemini-2.5-flash
      temperature: 0
      top-p: 1
      pii-masking:
        enabled: true
        required: true
        analyzer-url: http://localhost:5002
        anonymizer-url: http://localhost:5001
        language: ko
        min-score: 0.5
        timeout: 5s
        on-failure: fail
        entity-types: []
    knowledge-block:
      enabled: false
```

| Property | Default | 설명 |
| --- | --- | --- |
| `studio.chunking.enabled` | `true` | 기본 chunking bean 등록 여부입니다. |
| `studio.chunking.strategy` | `recursive` | 기본 순수 chunking 전략입니다. 지원값: `recursive`, `fixed-size`, `structure-based`, `blockify`, `knowledge-block`. |
| `studio.chunking.unit` | `character` | `max-size`와 `overlap` 해석 단위입니다. 지원값: `character`, `token`. |
| `studio.chunking.max-size` | `800` | configured unit 기준 최대 chunk size입니다. |
| `studio.chunking.overlap` | `100` | 이전 chunk에서 이어받는 configured unit 기준 overlap입니다. |
| `studio.chunking.tokenizer.auto-detect` | `true` | embedding provider/model metadata로 tokenizer를 자동 선택합니다. |
| `studio.chunking.tokenizer.fallback` | `approximate` | 정확한 tokenizer가 없을 때 사용할 fallback tokenizer입니다. |
| `studio.chunking.tokenizer.fail-on-unknown-model` | `false` | 알 수 없는 model에서 fallback 대신 실패할지 결정합니다. |
| `studio.chunking.tokenizer.mappings.*` | `{}` | model별 explicit tokenizer mapping입니다. |
| `studio.chunking.blockify.enabled` | `false` | `blockify` 전략 선택 허용 여부입니다. |
| `studio.chunking.knowledge-block.enabled` | `false` | `knowledge-block` 전략 선택 허용 여부입니다. Blockify 생성 엔진을 재사용하지만 `knowledgeBlock*` metadata alias를 저장합니다. |
| `studio.chunking.blockify.max-input-tokens` | `2500` | 섹션별 Blockify 입력 token 상한입니다. |
| `studio.chunking.blockify.max-output-tokens-per-block` | `500` | 생성 block 하나의 출력 token 상한 기준입니다. |
| `studio.chunking.blockify.max-blocks-per-section` | `10` | 섹션 하나에서 생성할 최대 Knowledge Block 수입니다. |
| `studio.chunking.blockify.max-sections-per-document` | `500` | 문서 하나에서 Blockify를 시도할 최대 섹션 수입니다. |
| `studio.chunking.blockify.min-answer-chars` | `80` | 생성 answer의 최소 문자 수입니다. 미달 시 fallback 됩니다. |
| `studio.chunking.blockify.min-chunk-chars` | `150` | 최종 Blockify chunk의 최소 문자 수입니다. 미달 시 fallback 됩니다. |
| `studio.chunking.blockify.max-chunk-chars` | `900` | 최종 Blockify chunk의 최대 문자 수입니다. 초과 시 fallback 됩니다. |
| `studio.chunking.blockify.min-evidence-chars` | `40` | source evidence로 인정할 최소 문자 수입니다. |
| `studio.chunking.blockify.distillation-enabled` | `true` | 동일한 정규화 질문·답변을 가진 IdeaBlock 후보를 stage 저장 전에 중복 제거합니다. |
| `studio.chunking.blockify.distillation-similarity-threshold` | `0.82` | 유사 IdeaBlock merge 후보를 표시하기 위한 lexical similarity 기준입니다. 이 단계에서는 후보 표시만 하고 병합하지 않습니다. |
| `studio.chunking.blockify.document-type` | `auto` | Markdown 내용 기반 문서 유형 자동 분류 설정입니다. `auto`, `policy`, `manual`, `narrative`, `technical`, `table-heavy`, `general`을 지원하며 요청 metadata의 `blockifyDocumentType`이 있으면 요청값이 우선합니다. |
| `studio.chunking.blockify.max-estimated-cost-per-job` | `10.0` | 호출 구현체가 사용할 수 있는 작업 비용 상한입니다. |
| `studio.chunking.blockify.per-job-concurrency` | `2` | 호출 구현체가 사용할 수 있는 작업별 동시성 상한입니다. |
| `studio.chunking.blockify.global-concurrency` | `4` | 호출 구현체가 사용할 수 있는 전역 동시성 상한입니다. |
| `studio.chunking.blockify.generator-type` | `heuristic` | Blockify block 생성 방식입니다. `heuristic` 또는 `llm`을 지원합니다. |
| `studio.chunking.blockify.prompt-version` | `blockify-v1` | metadata와 fingerprint에 기록할 prompt version입니다. |
| `studio.chunking.blockify.generator-model` | `heuristic-blockify-v1` | metadata와 fingerprint에 기록할 generator model입니다. |
| `studio.chunking.blockify.llm-provider` |  | `generator-type=llm`일 때 사용할 기본 provider입니다. 요청의 `blockifyLlmProvider`가 있으면 요청값이 우선합니다. |
| `studio.chunking.blockify.llm-model` |  | `generator-type=llm`일 때 사용할 기본 model입니다. 요청의 `blockifyLlmModel`이 있으면 요청값이 우선합니다. |
| `studio.chunking.blockify.temperature` | `0` | 생성 재현성을 위한 temperature 설정값입니다. |
| `studio.chunking.blockify.top-p` | `1` | 생성 재현성을 위한 top-p 설정값입니다. |
| `studio.chunking.blockify.require-source-evidence` | `true` | 생성 결과가 원문 section에 존재하는 evidence를 가져야 하는지 결정합니다. |
| `studio.chunking.blockify.pii-masking.enabled` | `true` | `generator-type=llm`일 때 외부 LLM 호출 전 Presidio 기반 PII masking을 적용할지 결정합니다. |
| `studio.chunking.blockify.pii-masking.required` | `true` | PII masking 실패 시 pipeline을 실패 처리할지 결정합니다. `false`이면 `on-failure` 정책에 따라 fallback을 허용할 수 있습니다. |
| `studio.chunking.blockify.pii-masking.analyzer-url` | `http://localhost:5002` | Presidio Analyzer base URL입니다. |
| `studio.chunking.blockify.pii-masking.anonymizer-url` | `http://localhost:5001` | Presidio Anonymizer base URL입니다. 현재 구현은 중복 token 방지를 위해 analyzer 결과를 기반으로 앱 내부에서 고유 token을 생성합니다. |
| `studio.chunking.blockify.pii-masking.language` | `ko` | Presidio Analyzer에 전달할 language 값입니다. |
| `studio.chunking.blockify.pii-masking.min-score` | `0.5` | masking 대상으로 인정할 Presidio confidence 하한입니다. |
| `studio.chunking.blockify.pii-masking.timeout` | `5s` | Presidio HTTP 요청 timeout입니다. |
| `studio.chunking.blockify.pii-masking.on-failure` | `fail` | Presidio 실패 시 정책입니다. 지원값: `fail`, `fallback`. |
| `studio.chunking.blockify.pii-masking.entity-types` | `[]` | masking할 Presidio entity type allow-list입니다. 비어 있으면 감지된 모든 entity type을 masking합니다. |

`max-size <= 0`, `overlap < 0`, `overlap >= max-size` 설정은 auto-configuration 단계에서 fail-fast 됩니다.

Blockify는 Markdown 본문의 heading, 조항 패턴, 표 비율, 코드블록, 대화문, 장문 문단 비율을 분석해
`detectedDocumentType`, `documentTypeConfidence`, `documentTypeSignals`, `blockifyProfile`,
`ideaBlockSchemaVersion`을 chunk metadata에 저장합니다. `policy-v1`은 `articleNo`, `condition`,
`obligation`, `exception` 같은 규정형 field를, `narrative-v1`은 `character`, `event`, `cause`,
`effect`, `location`, `quote` 같은 서사형 field를 `typedFields`와 top-level metadata에 함께 저장합니다.
LLM generator prompt는 `prompts/blockify/common.v1.prompt`와 문서 유형별 `*.v1.prompt` resource로 분리되어 있으며,
classpath resource 교체로 schema/rule 문구를 조정할 수 있습니다.

## Override

애플리케이션은 다음 bean을 직접 등록해 기본 동작을 교체할 수 있습니다.

- `ChunkingOrchestrator`
- `FixedSizeChunker`
- `RecursiveChunker`
- `StructureBasedChunker`
- `BlockifyGenerator`
- `BlockifyChunker`
- `WindowChunkContextExpander`
- `ParentChildChunkContextExpander`
- `HeadingChunkContextExpander`
- `TableChunkContextExpander`

`DefaultChunkingOrchestrator`는 모든 `Chunker` bean을 받아 `FIXED_SIZE`, `RECURSIVE`, `STRUCTURE_BASED`, `BLOCKIFY`를 실행합니다.
`BLOCKIFY`는 `studio.chunking.blockify.enabled=true`일 때만 허용됩니다.
`KNOWLEDGE_BLOCK`은 `studio.chunking.knowledge-block.enabled=true`일 때만 허용됩니다.

## Recursive Strategy

`RecursiveChunker`는 다음 순서로 분할합니다.

1. blank paragraph
2. newline
3. sentence punctuation
4. whitespace
5. fixed-size fallback

chunk id는 deterministic합니다.

```text
{sourceDocumentId}-{chunkOrder}
```

`chunkOrder`는 `0`부터 시작합니다.

## Structure-Based Strategy

`StructureBasedChunker`는 `NormalizedDocument`를 입력으로 받아 parser provenance를 `ChunkMetadata`에 보존합니다.
heading boundary는 `section` / `headingPath`로 유지하고, paragraph-like block은 size 정책에 따라 pack합니다.
table, OCR text, image-caption block은 standalone child chunk로 생성합니다.

각 child chunk는 additive metadata로 parent/neighbor/provenance 정보를 보존합니다.

- `chunkType`
- `parentChunkId`
- `parentChunkContent`
- `previousChunkId`
- `nextChunkId`
- `blockIds`
- `confidence`

neighbor link는 parent section boundary를 넘지 않습니다.
heading 없이 시작하는 문서는 빈 `section` 값과 body-only `parentChunkContent`를 사용합니다.

이 전략은 파일 parsing, OCR 실행, embedding API 호출, LLM 호출, vector store 저장을 하지 않습니다.

## Blockify Strategy

`BlockifyChunker`는 `NormalizedDocument`의 heading/page/slide 기반 block을 source block 후보로 분해해 질문·답변 중심 `IdeaBlock` chunk를 생성합니다.
취업규칙처럼 `제N조` 구조를 가진 문서는 조항 heading 아래의 각 항/본문 block 단위가 우선 후보가 됩니다.
기본 구현은 PoC용 deterministic `HeuristicBlockifyGenerator`를 사용하며 외부 provider를 호출하지 않습니다.
`studio.chunking.blockify.generator-type=llm`을 설정하면 classpath의 AI provider registry를 통해 LLM 기반 생성기를 사용합니다.
LLM 기반 생성기는 기본적으로 Presidio Analyzer를 호출해 원문 block의 PII를 `<PII_n_ENTITY>` token으로 가명화한 뒤 LLM에 전달하고,
생성 결과를 저장하기 전에 token을 원문 값으로 복원합니다.
본문 없는 목차/장/절 제목 section은 chunk를 만들지 않고 건너뜁니다. 본문이 있는 source block에서 생성 결과가 제목만 반복하거나, generic question이거나, evidence가 원문에 없거나, 숫자/날짜/기간/비율이 evidence와 맞지 않거나, 크기 기준을 만족하지 않으면 `structure-based` fallback을 사용합니다.
source block coverage가 95% 미만이면 누락 block은 fallback chunk로 보존합니다.

Blockify chunk metadata는 기존 chunk 저장 계약을 바꾸지 않고 additive field로 보존됩니다.

- `schemaVersion=blockify-metadata-v1`
- `requestedChunkingStrategy`
- `actualChunkingStrategy`
- `chunkType=ideaBlock`
- `fingerprint`, `ideaBlockFingerprint`
- `blockifyFingerprint`
- `ideaBlockName`, `title`
- `criticalQuestion`, `trustedAnswer`
- `question`, `answer` (legacy alias)
- `entityName`, `entityType`
- `keywords`, `tags`
- `sourceEvidence`
- `sourceBlockRange`, `sourceSectionId`
- `validationStatus`
- `fallbackReason`
- `promptVersion`
- `generatorModel`
- `detectedDocumentType`, `blockifyProfile`, `ideaBlockSchemaVersion`
- `typedFields`와 문서 유형별 top-level field

Blockify content는 `제목`, `핵심 질문`, `답변`, `핵심 원문 Evidence`, `키워드`를 포함합니다.
표 섹션, 입력 token 상한 초과, 생성 결과 검증 실패 섹션은 `structure-based` fallback chunk로 대체됩니다.
fallback chunk는 `requestedChunkingStrategy=blockify`, `actualChunkingStrategy=structure-based`, `validationStatus=FALLBACK` metadata를 남깁니다.
`knowledge-block` 전략의 fallback chunk는 `requestedChunkingStrategy=knowledge-block`, `actualChunkingStrategy=structure-based`,
`validationStatus=FALLBACK` metadata를 남기며, IdeaBlock summary/coverage/distillation 값은 `knowledgeBlock*` alias로도 제공됩니다.
주요 fallback reason은 `ANSWER_TOO_SHORT`, `ANSWER_HAS_NO_BODY`, `ANSWER_EQUALS_TITLE`, `HEADING_ONLY`, `EVIDENCE_NOT_FOUND`, `TRUSTED_ANSWER_FACT_MISMATCH`, `GENERIC_QUESTION`, `TABLE_SECTION`, `COVERAGE_GAP`입니다.

클라이언트는 Markdown extraction/reindex 요청에서 `blockifyPiiMaskingEnabled`를 전달해 작업 단위로 masking 사용 여부를 지정할 수 있습니다.
값을 생략하면 서버 설정 기본값이 적용되며, 기본 동작은 masking 사용입니다.
민감 문서에서 외부 LLM을 사용할 수 없는 환경은 `blockifyPiiMaskingEnabled=false`를 허용하지 않도록 클라이언트 정책을 별도로 둘 수 있습니다.

동일 원본 비교 PoC는 같은 파일을 별도 Attachment로 등록하고 `structure-based` Projection과 `blockify` Projection을 분리해 실행합니다.
같은 Attachment scope에 두 전략을 반복 실행하면 downstream stage/vector 교체 정책에 의해 결과가 덮일 수 있습니다.

`studio-platform-textract`가 classpath에 있으면 `TextractNormalizedDocumentAdapter`로 `ParsedFile`을 `NormalizedDocument`로 변환할 수 있습니다.
실제 파일 읽기, embedding 생성, vector upsert는 이 starter의 책임이 아니며, `content-embedding-pipeline` 같은 조립 모듈에서 실행합니다.
구조화 chunk metadata가 vector storage에서 어떻게 해석되는지는
[`studio-platform-ai` RAG metadata key reference](../../studio-platform-ai/README.md#rag-metadata-key-reference)를 기준으로 합니다.

`starter:studio-platform-starter-ai-web`의 RAG chunk preview API는 운영 화면에서 같은 `ChunkingOrchestrator`를 호출해
색인 전 text chunk 결과를 확인합니다. preview API도 `studio.chunking.strategy`, `studio.chunking.max-size`,
`studio.chunking.overlap` configured default를 사용하므로 실제 신규 RAG 색인 경로와 같은 chunking 설정을 기준으로 합니다.
다만 preview API는 embedding 생성, vector upsert, attachment parsing을 실행하지 않습니다.

```java
ParsedFile parsedFile = fileContentExtractionService.parseStructured(...);
NormalizedDocument document = new TextractNormalizedDocumentAdapter()
        .adapt("doc-1", parsedFile);
List<Chunk> chunks = chunkingOrchestrator.chunk(document);
```

adapter mapping:

- `ParsedBlock`은 heading, paragraph, list, footnote, OCR 등 normalized block으로 매핑합니다.
- `ExtractedTable.vectorText()`는 table chunk text로 사용합니다.
- `ExtractedImage.caption()`, `altText()`, `ocrText()`는 image-caption/OCR chunk text 후보로 사용합니다.
- `ParsedBlock.confidence()`, inferred `headingPath`, table/image source reference를 normalized provenance field로 전달합니다.
- image metadata의 `order`, `page`, `slide`, `parentBlockId`, `headingPath`, `confidence`는 parser가 제공한 경우 보존합니다.

parsed table block과 `ExtractedTable`이 같은 `sourceRef`를 공유하면 중복 table block을 만들지 않고,
`ExtractedTable.vectorText()` 기반 table block 하나만 유지합니다. 이때 parsed table block의 order/provenance를 넘겨받습니다.
parser가 structured block 없이 `plainText`만 반환하면 normalized document는 해당 text를 text chunking fallback으로 유지합니다.

### Parent-Child 예시

```java
NormalizedDocument document = NormalizedDocument.builder("doc-1")
        .sourceFormat("PDF")
        .blocks(List.of(
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "Install")
                        .id("page[1]/h[0]")
                        .order(0)
                        .headingPath("Install")
                        .blockIds(List.of("page[1]/h[0]"))
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "Install the engine.")
                        .id("page[1]/p[1]")
                        .order(1)
                        .blockIds(List.of("page[1]/p[1]"))
                        .build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "Configure tessdata.")
                        .id("page[1]/p[2]")
                        .order(2)
                        .blockIds(List.of("page[1]/p[2]"))
                        .build()))
        .build();

List<Chunk> chunks = chunkingOrchestrator.chunk(document);
Chunk chunk = chunks.get(0);

chunk.metadata().chunkType();      // CHILD
chunk.metadata().parentChunkId();  // doc-1-parent-0
chunk.metadata().toMap().get("parentChunkContent"); // Install\n\nInstall the engine.\n\nConfigure tessdata.
chunk.metadata().previousChunkId();// null
chunk.metadata().nextChunkId();    // null for a single child
chunk.metadata().blockIds();       // [page[1]/p[1], page[1]/p[2]]
```

기본 반환값은 호환성을 위해 child chunk list입니다.
parent content는 child metadata에 additive로 저장되므로 indexing contract를 바꾸지 않고도 이후 context expansion에서 section context를 복구할 수 있습니다.

### 하위 호환성

- text-only 호출은 계속 `ChunkingContext`와 configured text strategy를 사용합니다.
- `StructureBasedChunker.chunk(ChunkingContext)`는 fallback text chunker로 위임하므로 oversized plain text도 기존 recursive/fixed-size 방식으로 분할됩니다.
- `DefaultChunkingOrchestrator.chunk(NormalizedDocument)`는 opt-in이며 `STRUCTURE_BASED`만 사용합니다.
- parent chunk는 기본적으로 별도 indexing record로 반환되지 않습니다. parent context는 child metadata에 저장됩니다.
- context expander는 chunk retrieval을 직접 수행하지 않습니다. caller가 storage/retrieval layer에서 작은 `availableChunks` 후보 목록을 전달해야 합니다.

## Context Expansion

starter는 vector search 이후 검색된 child chunk를 답변 context로 확장하기 위한 순수 in-memory `ChunkContextExpander` 구현체를 제공합니다.
이 구현체들은 전달받은 `seedChunk`와 작은 pre-filtered `availableChunks` 목록만 소비합니다.
embedding API, vector store, LLM, parser, OCR engine을 호출하지 않습니다.

- `WindowChunkContextExpander`: `previousChunkId` / `nextChunkId` link를 요청된 window만큼 따라갑니다.
- `ParentChildChunkContextExpander`: `parentChunkContent`가 있으면 우선 사용하고, 없으면 같은 `parentChunkId` sibling을 join합니다.
- `HeadingChunkContextExpander`: 같은 `section` / heading context의 chunk를 join합니다.
- `TableChunkContextExpander`: table chunk를 atomic retrieval unit으로 유지하고 저장된 parent context를 복구할 수 있습니다.

```java
ChunkContextExpansionRequest request = ChunkContextExpansionRequest.builder(retrievedChunk)
        .availableChunks(candidateChunks)
        .previousWindow(1)
        .nextWindow(1)
        .includeParentContent(true)
        .build();

ChunkContextExpansion expansion = windowChunkContextExpander.expand(request);
```

`availableChunks`는 caller가 이미 범위를 좁힌 후보여야 합니다.
예를 들어 같은 문서의 neighbor chunk, 같은 parent의 sibling chunk, 또는 같은 heading section에서 검색된 상위 후보만 전달합니다.
전체 corpus를 전달하면 계약 의도에 맞지 않고 메모리 사용량이 증가할 수 있습니다.

권장 routing:

| Retrieved chunk | Recommended expander |
| --- | --- |
| neighbor link가 있는 paragraph/list child | `WindowChunkContextExpander` |
| `parentChunkContent`가 있는 child | `ParentChildChunkContextExpander` |
| 같은 heading section의 복수 hit | `HeadingChunkContextExpander` |
| table chunk | `TableChunkContextExpander` |

## Size Policy

기본 size 정책은 character 기준입니다. `studio.chunking.unit=token`을 설정하면 `DefaultChunkingOrchestrator`가
tokenizer-aware chunker를 사용해 token 기준 `max-size`와 `overlap`을 적용합니다.
OpenAI embedding/chat model은 `jtokkit` 기반 tiktoken-compatible adapter로 계산하고, 알 수 없는 model은
`ApproximateTokenizer`를 사용하며 `tokenizerFallbackUsed`, `tokenizerWarnings` metadata를 남깁니다.
structure-based overlap은 보수적으로 동작하며 heading, table, OCR, image-caption boundary는 overlap tail로 넘기지 않습니다.
