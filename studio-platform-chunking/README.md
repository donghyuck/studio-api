# Studio Platform Chunking

`studio-platform-chunking` provides provider-neutral chunking contracts for AI/RAG indexing.

## Responsibilities

- Define immutable chunking request/result models.
- Define strategy-neutral chunking extension points.
- Keep chunking contracts independent from Spring, Spring AI, JDBC, and vector-store implementations.

## Core Types

- `Chunker`: strategy implementation contract.
- `ChunkingOrchestrator`: strategy selection and composition contract.
- `ChunkingContext`: immutable input context for chunk generation.
- `Chunk`: immutable chunk content with metadata.
- `ChunkMetadata`: standard metadata for vector indexing.
- `ChunkingStrategyType`: supported strategy identifiers.
- `ChunkUnit`: size unit for character-based or token-based chunking.
- `NormalizedDocument`: parser-neutral structured input for structure-aware chunking.
- `NormalizedBlock`: parser-neutral logical block with source provenance.
- `NormalizedDocumentChunker`: chunker extension for normalized documents.

## Metadata Rules

- Metadata maps are defensively copied.
- Null keys, blank keys, null values, and blank string values are omitted.
- `chunkOrder` remains the canonical persisted order key for Phase 1 compatibility.
- `ChunkMetadata.order` is intended to map to the same value as `chunkOrder` when persisted by downstream modules.
- Structured provenance keys are standardized for downstream consumers:
  - `sourceRef`, `sourceRefs`, `blockType`, `page`, `slide`
  - `parentBlockId`, `headingPath`, `sourceFormat`
  - `tokenEstimate`, `chunkUnit`, `maxSize`, `overlap`

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
                NormalizedBlock.builder(NormalizedBlockType.HEADING, "Install").order(0).build(),
                NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "Install the engine.").order(1).build()))
        .build();
```

## Dependency Boundary

This module must not depend on Spring, Spring AI, JDBC, pgvector, or web modules. Strategy implementations and auto-configuration belong in a starter module.
This module also does not call embedding APIs, vector stores, LLMs, OCR engines, or file parsers.
