# Studio Platform Starter Chunking

`studio-platform-starter-chunking` provides auto-configured chunking strategies for Studio RAG indexing.

## Responsibility

- Provides Phase 1 text-only chunking implementations.
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
It keeps heading boundaries as `section` / `headingPath`, packs paragraph-like blocks by configured size, and emits table, OCR text, and image-caption blocks as standalone chunks.

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

When a parsed table block and an `ExtractedTable` share the same `sourceRef`, the adapter keeps one table block based on
`ExtractedTable.vectorText()` and carries over the parsed table block order/provenance. `ExtractedImage` does not currently
carry an independent order value, so image-caption/OCR chunks are sorted after ordered parsed blocks unless the source
metadata provides a future ordering field.

## Size Policy

Character size remains the default policy. `ChunkUnit.TOKEN` uses a deterministic estimate based on compacted character length and does not call an external tokenizer.
Structure-based overlap is conservative: heading, table, OCR, and image-caption boundaries are not carried as overlap tails.
