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

## Metadata Rules

- Metadata maps are defensively copied.
- Null keys, blank keys, null values, and blank string values are omitted.
- `chunkOrder` remains the canonical persisted order key for Phase 1 compatibility.
- `ChunkMetadata.order` is intended to map to the same value as `chunkOrder` when persisted by downstream modules.

## Dependency Boundary

This module must not depend on Spring, Spring AI, JDBC, pgvector, or web modules. Strategy implementations and auto-configuration belong in a starter module.
