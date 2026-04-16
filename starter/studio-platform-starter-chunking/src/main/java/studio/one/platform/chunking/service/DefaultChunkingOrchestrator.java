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

public class DefaultChunkingOrchestrator implements ChunkingOrchestrator {

    private final ChunkingProperties properties;

    private final Map<ChunkingStrategyType, Chunker> chunkers = new EnumMap<>(ChunkingStrategyType.class);

    public DefaultChunkingOrchestrator(
            ChunkingProperties properties,
            List<Chunker> chunkers) {
        this.properties = properties;
        chunkers.forEach(chunker -> this.chunkers.put(chunker.strategy(), chunker));
    }

    @Override
    public List<Chunk> chunk(ChunkingContext context) {
        ChunkingContext effectiveContext = applyDefaults(context);
        return selectChunker(effectiveContext).chunk(effectiveContext);
    }

    private Chunker selectChunker(ChunkingContext context) {
        ChunkingStrategyType strategy = context.strategy() == null ? properties.strategyType() : context.strategy();
        if (strategy != ChunkingStrategyType.FIXED_SIZE && strategy != ChunkingStrategyType.RECURSIVE) {
            throw new IllegalArgumentException(
                    "Unsupported Phase 1 chunking strategy: " + strategy + ". Supported values are FIXED_SIZE and RECURSIVE.");
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
                context.unit(),
                context.metadata());
    }
}
