# Studio Platform Starter Chunking

`studio-platform-starter-chunking` provides auto-configured chunking strategies for Studio RAG indexing.

## Responsibility

- Provides pure chunking implementations for text and normalized structured documents.
- Registers chunking beans with `@ConditionalOnMissingBean` so applications can override them.
- Keeps Spring AI, embedding, vector storage, and web endpoint concerns out of this starter.
- Leaves `starter:studio-platform-starter-ai-web` as an HTTP adapter only.

## Supported Strategies

Phase 1 supports:

- `recursive` (default)
- `fixed-size`
- `structure-based`

Planned Phase 2 strategies:

- `semantic` (AI-linked, not in this pure starter)
- `llm-based` (AI-linked, not in this pure starter)

## Configuration

```yaml
studio:
  chunking:
    enabled: true
    strategy: recursive
    max-size: 800
    overlap: 100
```

| Property | Default | Description |
| --- | --- | --- |
| `studio.chunking.enabled` | `true` | Registers default chunking beans when enabled. |
| `studio.chunking.strategy` | `recursive` | Pure chunking strategy. Supported values: `recursive`, `fixed-size`, `structure-based`. |
| `studio.chunking.max-size` | `800` | Maximum chunk size in characters. |
| `studio.chunking.overlap` | `100` | Character overlap carried from the previous chunk. |

Invalid `max-size <= 0`, `overlap < 0`, or `overlap >= max-size` settings fail fast during auto-configuration.

## Override

Applications can override the default behavior by registering custom beans:

- `ChunkingOrchestrator`
- `FixedSizeChunker`
- `RecursiveChunker`
- `StructureBasedChunker`

`DefaultChunkingOrchestrator` receives all `Chunker` beans and executes `FIXED_SIZE`, `RECURSIVE`, and `STRUCTURE_BASED`.

## Recursive Strategy

`RecursiveChunker` splits in this order:

1. blank paragraph
2. newline
3. sentence punctuation
4. whitespace
5. fixed-size fallback

Chunk ids are deterministic:

```text
{sourceDocumentId}-{chunkOrder}
```

`chunkOrder` starts at `0`.

## Structure-Based Strategy

`StructureBasedChunker` consumes `NormalizedDocument` and preserves parser provenance in `ChunkMetadata`.
It keeps heading boundaries as `section` / `headingPath`, packs paragraph-like blocks by configured size, and emits table, OCR text, and image-caption blocks as standalone child chunks.
It also creates deterministic parent section ids and links each child chunk through additive metadata:

- `chunkType`
- `parentChunkId`
- `parentChunkContent`
- `previousChunkId`
- `nextChunkId`
- `blockIds`
- `confidence`

Neighbor links do not cross parent section boundaries. If a document starts without a heading, the first parent context
uses an empty `section` value and body-only `parentChunkContent`.

The strategy does not parse files, run OCR, call embedding APIs, call LLMs, or write vector stores.

When `studio-platform-textract` is available, `TextractNormalizedDocumentAdapter` can convert `ParsedFile` into `NormalizedDocument`:

```java
ParsedFile parsedFile = fileContentExtractionService.parseStructured(...);
NormalizedDocument document = new TextractNormalizedDocumentAdapter()
        .adapt("doc-1", parsedFile);
List<Chunk> chunks = chunkingOrchestrator.chunk(document);
```

The adapter maps:

- `ParsedBlock` to normalized heading, paragraph, list, footnote, OCR, and other logical blocks.
- `ExtractedTable.vectorText()` to table chunks.
- `ExtractedImage.caption()`, `altText()`, or `ocrText()` to image-caption/OCR chunks.
- `ParsedBlock.confidence()`, inferred `headingPath`, and table/image source references into normalized provenance fields.
- image metadata keys such as `order`, `page`, `slide`, `parentBlockId`, `headingPath`, and `confidence` when the
  parser already provides them.

When a parsed table block and an `ExtractedTable` share the same `sourceRef`, the adapter keeps one table block based on
`ExtractedTable.vectorText()` and carries over the parsed table block order/provenance. `ExtractedImage` does not currently
carry an independent order value, so image-caption/OCR chunks are sorted after ordered parsed blocks unless the source
metadata provides a future ordering field.
If a parser returns only `plainText` without structured blocks, the normalized document keeps that text as the
compatibility fallback for text-based chunking.

### Parent-Child Example

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

The default return value remains the child chunk list for compatibility. Parent content is persisted additively in child
metadata so later context-expansion strategies can recover section context without changing the indexing contract.

## Context Expansion

The starter provides pure in-memory `ChunkContextExpander` implementations for expanding a retrieved child chunk after
vector search. They consume only the supplied `seedChunk` and a small pre-filtered `availableChunks` list; they do not
call embedding APIs, vector stores, LLMs, parsers, or OCR engines.

- `WindowChunkContextExpander`: follows `previousChunkId` / `nextChunkId` links up to the requested window.
- `ParentChildChunkContextExpander`: restores `parentChunkContent` when present, otherwise joins siblings with the same
  `parentChunkId`.
- `HeadingChunkContextExpander`: joins chunks with the same `section` / heading context.
- `TableChunkContextExpander`: keeps table chunks as a single retrieval unit and can restore stored parent context.

```java
ChunkContextExpansionRequest request = ChunkContextExpansionRequest.builder(retrievedChunk)
        .availableChunks(candidateChunks)
        .previousWindow(1)
        .nextWindow(1)
        .includeParentContent(true)
        .build();

ChunkContextExpansion expansion = windowChunkContextExpander.expand(request);
```

`availableChunks` should already be scoped by the caller, for example to neighbor chunks from the same document or the
top retrieval candidates for the same parent. Passing an entire corpus defeats the contract and can increase memory use.

## Size Policy

Character size remains the default policy. `ChunkUnit.TOKEN` uses a deterministic estimate based on compacted character length and does not call an external tokenizer.
Structure-based overlap is conservative: heading, table, OCR, and image-caption boundaries are not carried as overlap tails.
