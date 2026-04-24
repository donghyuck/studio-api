# Studio Platform Chunking

`studio-platform-chunking` provides provider-neutral chunking contracts for AI/RAG indexing.

## Responsibilities

- Define immutable chunking request/result models.
- Define strategy-neutral chunking extension points.
- Model search-oriented child chunks and expansion-oriented parent relationships.
- Keep chunking contracts independent from Spring, Spring AI, JDBC, and vector-store implementations.

## Core Types

- `Chunker`: strategy implementation contract.
- `ChunkingOrchestrator`: strategy selection and composition contract.
- `ChunkingContext`: immutable input context for chunk generation.
- `Chunk`: immutable chunk content with metadata.
- `ChunkMetadata`: standard metadata for vector indexing.
- `ChunkType`: logical chunk role such as `child`, `parent`, `table`, `ocr`.
- `ChunkingStrategyType`: supported strategy identifiers.
- `ChunkUnit`: size unit for character-based or token-based chunking.
- `NormalizedDocument`: parser-neutral structured input for structure-aware chunking.
- `NormalizedBlock`: parser-neutral logical block with source provenance.
- `NormalizedDocumentChunker`: chunker extension for normalized documents.
- `ChunkContextExpander`: contract for expanding a retrieved child chunk into answer context.
- `ChunkContextExpansionRequest` / `ChunkContextExpansion`: request/result models for context expansion.

## Metadata Rules

- Metadata maps are defensively copied.
- Null keys, blank keys, null values, and blank string values are omitted.
- `chunkOrder` remains the canonical persisted order key for Phase 1 compatibility.
- `ChunkMetadata.order` is intended to map to the same value as `chunkOrder` when persisted by downstream modules.
- Structured provenance keys are standardized for downstream consumers:
  - `sourceRef`, `sourceRefs`, `blockType`, `page`, `slide`
  - `parentBlockId`, `headingPath`, `sourceFormat`, `blockIds`, `confidence`
  - `tokenEstimate`, `chunkUnit`, `maxSize`, `overlap`
- Parent-child relationship keys are additive and do not replace legacy keys:
  - `chunkType`, `parentChunkId`, `parentChunkContent`, `previousChunkId`, `nextChunkId`
- `parentId` remains for legacy compatibility and is not redefined as `parentChunkId`.

## Structured Input

`NormalizedDocument` and `NormalizedBlock` allow chunking to consume structured extraction results without depending on a parser implementation.
The text-based API remains the compatibility baseline:

```java
ChunkingContext context = ChunkingContext.builder("plain text")
        .sourceDocumentId("doc-1")
        .build();
```

Structured chunking should use normalized blocks:

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

## Parent-Child Model

The compatibility return type remains `List<Chunk>`, and the default output remains search-oriented child chunks.
Parent section chunks are modeled through additive metadata links so downstream indexing can keep indexing child chunks
without schema breaks.

- Child chunks are the default retrieval unit.
- Parent chunks are modeled by deterministic `parentChunkId` values.
- Parent chunk content can be persisted additively as `parentChunkContent` when a strategy needs parent context recovery.
- `previousChunkId` and `nextChunkId` link adjacent child chunks within the same parent section only.
- Documents that start without a heading still receive a parent context with an empty `section` value and body-only
  `parentChunkContent`.
- `blockIds`, `headingPath`, `page`, `slide`, `sourceRef`, and `confidence` preserve provenance needed for later
  context expansion.

## Context Expansion Contract

`ChunkContextExpander` defines how a retrieved child chunk can be expanded into a larger answer context. The contract is
implementation-neutral and does not perform retrieval, embedding, vector store access, LLM calls, or parser work.

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

Built-in strategy identifiers are `parent-child`, `window`, `heading`, `table`, `custom`, and `unknown`.
Concrete expansion implementations live in starter modules.

## Dependency Boundary

This module must not depend on Spring, Spring AI, JDBC, pgvector, or web modules. Strategy implementations and auto-configuration belong in a starter module.
This module also does not call embedding APIs, vector stores, LLMs, OCR engines, or file parsers.
