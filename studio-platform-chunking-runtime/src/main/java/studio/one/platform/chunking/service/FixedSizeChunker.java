package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.List;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.Chunker;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;

public class FixedSizeChunker implements Chunker {

    private final int defaultMaxSize;

    private final int defaultOverlap;

    public FixedSizeChunker(int defaultMaxSize, int defaultOverlap) {
        if (defaultMaxSize <= 0) {
            throw new IllegalArgumentException("defaultMaxSize must be positive");
        }
        if (defaultOverlap < 0) {
            throw new IllegalArgumentException("defaultOverlap cannot be negative");
        }
        this.defaultMaxSize = defaultMaxSize;
        this.defaultOverlap = Math.min(defaultOverlap, defaultMaxSize - 1);
    }

    @Override
    public ChunkingStrategyType strategy() {
        return ChunkingStrategyType.FIXED_SIZE;
    }

    @Override
    public List<Chunk> chunk(ChunkingContext context) {
        String text = normalize(context.text());
        if (text.isBlank()) {
            return List.of();
        }

        int maxSize = effectiveMaxSize(context.maxSize());
        int overlap = effectiveOverlap(context.overlap(), maxSize);
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int order = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxSize, text.length());
            String content = text.substring(start, end).trim();
            if (!content.isBlank()) {
                int currentOrder = order++;
                chunks.add(chunk(context, content, currentOrder, start, end,
                        currentOrder == 0 ? null : chunkId(context.sourceDocumentId(), currentOrder - 1),
                        end == text.length() ? null : chunkId(context.sourceDocumentId(), currentOrder + 1)));
            }
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }

        return chunks;
    }

    private Chunk chunk(
            ChunkingContext context,
            String content,
            int order,
            int startOffset,
            int endOffset,
            String previousChunkId,
            String nextChunkId) {
        String id = chunkId(context.sourceDocumentId(), order);
        ChunkMetadata metadata = ChunkMetadata.builder(strategy(), order)
                .sourceDocumentId(context.sourceDocumentId())
                .chunkType(ChunkType.CHILD)
                .previousChunkId(previousChunkId)
                .nextChunkId(nextChunkId)
                .objectType(context.objectType())
                .objectId(context.objectId())
                .startOffset(startOffset)
                .endOffset(endOffset)
                .charCount(content.length())
                .build();
        return Chunk.of(id, content, metadata);
    }

    private String chunkId(String sourceDocumentId, int order) {
        String prefix = sourceDocumentId == null || sourceDocumentId.isBlank() ? "document" : sourceDocumentId;
        return prefix + "-" + order;
    }

    private int effectiveMaxSize(int requestedMaxSize) {
        return requestedMaxSize <= 0 ? defaultMaxSize : requestedMaxSize;
    }

    private int effectiveOverlap(int requestedOverlap, int maxSize) {
        int overlap = requestedOverlap < 0 ? defaultOverlap : requestedOverlap;
        return Math.min(overlap, maxSize - 1);
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
