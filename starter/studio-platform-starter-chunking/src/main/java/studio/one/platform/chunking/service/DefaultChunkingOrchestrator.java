package studio.one.platform.chunking.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.autoconfigure.ChunkingProperties;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.Chunker;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.chunking.core.NormalizedDocumentChunker;

public class DefaultChunkingOrchestrator implements ChunkingOrchestrator {

    private final ChunkingProperties properties;

    private final Map<ChunkingStrategyType, Chunker> chunkers = new EnumMap<>(ChunkingStrategyType.class);

    private final TokenBasedChunker tokenBasedChunker;

    public DefaultChunkingOrchestrator(
            ChunkingProperties properties,
            List<Chunker> chunkers) {
        this(properties, chunkers, null);
    }

    public DefaultChunkingOrchestrator(
            ChunkingProperties properties,
            List<Chunker> chunkers,
            TokenBasedChunker tokenBasedChunker) {
        this.properties = properties;
        chunkers.forEach(chunker -> this.chunkers.put(chunker.strategy(), chunker));
        this.tokenBasedChunker = tokenBasedChunker;
    }

    @Override
    public List<Chunk> chunk(ChunkingContext context) {
        ChunkingContext effectiveContext = applyDefaults(context);
        if (effectiveContext.unit() == ChunkUnit.TOKEN && tokenBasedChunker != null) {
            return tokenBasedChunker.chunk(effectiveContext);
        }
        return selectChunker(effectiveContext).chunk(effectiveContext);
    }

    @Override
    public List<Chunk> chunk(NormalizedDocument document) {
        if (document == null || document.chunkableText().isBlank()) {
            return List.of();
        }
        ChunkingContext context = document.toContextBuilder()
                .strategy(ChunkingStrategyType.STRUCTURE_BASED)
                .maxSize(properties.getMaxSize())
                .overlap(properties.getOverlap())
                .unit(properties.unitType())
                .build();
        Chunker chunker = selectChunker(context);
        if (chunker instanceof NormalizedDocumentChunker normalizedDocumentChunker) {
            return normalizedDocumentChunker.chunk(document, context);
        }
        return chunker.chunk(context);
    }

    private Chunker selectChunker(ChunkingContext context) {
        ChunkingStrategyType strategy = context.strategy() == null ? properties.strategyType() : context.strategy();
        if (strategy != ChunkingStrategyType.FIXED_SIZE
                && strategy != ChunkingStrategyType.RECURSIVE
                && strategy != ChunkingStrategyType.STRUCTURE_BASED) {
            throw new IllegalArgumentException(
                    "Unsupported pure chunking strategy: " + strategy
                            + ". Supported values are FIXED_SIZE, RECURSIVE, and STRUCTURE_BASED.");
        }
        Chunker chunker = chunkers.get(strategy);
        if (chunker == null) {
            throw new IllegalStateException("Chunker bean for strategy " + strategy + " is not registered.");
        }
        return chunker;
    }

    private ChunkingContext applyDefaults(ChunkingContext context) {
        return new ChunkingContext(
                context.sourceDocumentId(),
                context.text(),
                context.contentType(),
                context.filename(),
                context.objectType(),
                context.objectId(),
                context.strategy() == null ? properties.strategyType() : context.strategy(),
                properties.effectiveMaxSize(context.maxSize()),
                properties.effectiveOverlap(context.overlap()),
                shouldUseConfiguredDefaults(context) ? properties.unitType() : context.unit(),
                context.metadata());
    }

    private boolean shouldUseConfiguredDefaults(ChunkingContext context) {
        return context.strategy() == null
                && context.maxSize() == ChunkingContext.USE_CONFIGURED_MAX_SIZE
                && context.overlap() == ChunkingContext.USE_CONFIGURED_OVERLAP;
    }
}
