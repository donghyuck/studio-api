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

Planned Phase 2 strategies:

- `structure-based`
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
| `studio.chunking.strategy` | `recursive` | Phase 1 strategy. Supported values: `recursive`, `fixed-size`. |
| `studio.chunking.max-size` | `800` | Maximum chunk size in characters. |
| `studio.chunking.overlap` | `100` | Character overlap carried from the previous chunk. |

Invalid `max-size <= 0`, `overlap < 0`, or `overlap >= max-size` settings fail fast during auto-configuration.

## Override

Applications can override the default behavior by registering custom beans:

- `ChunkingOrchestrator`
- `FixedSizeChunker`
- `RecursiveChunker`

`DefaultChunkingOrchestrator` receives all `Chunker` beans, but Phase 1 only executes `FIXED_SIZE` and `RECURSIVE`.

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
