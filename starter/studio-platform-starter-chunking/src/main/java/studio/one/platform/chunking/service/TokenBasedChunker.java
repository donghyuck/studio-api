package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;
import studio.one.platform.chunking.core.ResolvedTokenizer;
import studio.one.platform.chunking.core.TokenizerResolver;

public class TokenBasedChunker {

    private static final int APPROXIMATE_CHARS_PER_TOKEN = 4;

    private final int defaultMaxSize;
    private final int defaultOverlap;
    private final TokenizerResolver tokenizerResolver;

    public TokenBasedChunker(int defaultMaxSize, int defaultOverlap, TokenizerResolver tokenizerResolver) {
        if (defaultMaxSize <= 0) {
            throw new IllegalArgumentException("defaultMaxSize must be positive");
        }
        if (defaultOverlap < 0) {
            throw new IllegalArgumentException("defaultOverlap cannot be negative");
        }
        this.defaultMaxSize = defaultMaxSize;
        this.defaultOverlap = Math.min(defaultOverlap, defaultMaxSize - 1);
        this.tokenizerResolver = tokenizerResolver;
    }

    public List<Chunk> chunk(ChunkingContext context) {
        String text = normalize(context.text());
        if (text.isBlank()) {
            return List.of();
        }
        int maxSize = ChunkSizing.effectiveMaxSize(context.maxSize(), defaultMaxSize);
        int overlap = ChunkSizing.effectiveOverlap(context.overlap(), defaultOverlap, maxSize);
        ResolvedTokenizer resolvedTokenizer = tokenizerResolver.resolve(context.metadata());
        if (resolvedTokenizer.fallbackUsed() && "approximate".equals(resolvedTokenizer.provider())) {
            return approximateChunks(context, text, maxSize, overlap, resolvedTokenizer);
        }
        return tokenChunks(context, text, maxSize, overlap, resolvedTokenizer);
    }

    private List<Chunk> tokenChunks(
            ChunkingContext context,
            String text,
            int maxSize,
            int overlap,
            ResolvedTokenizer resolvedTokenizer) {
        List<Integer> tokens = resolvedTokenizer.tokenizer().encode(text);
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int order = 0;
        while (start < tokens.size()) {
            int end = Math.min(start + maxSize, tokens.size());
            String content = resolvedTokenizer.tokenizer().decode(tokens.subList(start, end)).trim();
            if (!content.isBlank()) {
                chunks.add(chunk(context, content, order++, start, end, tokens.size(), maxSize, overlap,
                        resolvedTokenizer));
            }
            if (end == tokens.size()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return linkNeighbors(context, chunks);
    }

    private List<Chunk> approximateChunks(
            ChunkingContext context,
            String text,
            int maxSize,
            int overlap,
            ResolvedTokenizer resolvedTokenizer) {
        int charMaxSize = Math.max(1, maxSize * APPROXIMATE_CHARS_PER_TOKEN);
        int charOverlap = Math.min(Math.max(0, overlap * APPROXIMATE_CHARS_PER_TOKEN), charMaxSize - 1);
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int order = 0;
        int totalTokens = resolvedTokenizer.tokenizer().countTokens(text);
        while (start < text.length()) {
            int end = Math.min(start + charMaxSize, text.length());
            String content = text.substring(start, end).trim();
            if (!content.isBlank()) {
                int tokenStart = Math.max(0, start / APPROXIMATE_CHARS_PER_TOKEN);
                int tokenEnd = Math.min(totalTokens, tokenStart + resolvedTokenizer.tokenizer().countTokens(content));
                chunks.add(chunk(context, content, order++, tokenStart, tokenEnd, totalTokens, maxSize, overlap,
                        resolvedTokenizer));
            }
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - charOverlap, start + 1);
        }
        return linkNeighbors(context, chunks);
    }

    private Chunk chunk(
            ChunkingContext context,
            String content,
            int order,
            int tokenStart,
            int tokenEnd,
            int totalTokens,
            int maxSize,
            int overlap,
            ResolvedTokenizer resolvedTokenizer) {
        String id = chunkId(context.sourceDocumentId(), order);
        int chunkTokenCount = Math.max(0, tokenEnd - tokenStart);
        Map<String, Object> attributes = new LinkedHashMap<>(context.metadata());
        attributes.put(ChunkMetadata.KEY_CHUNK_UNIT, ChunkUnit.TOKEN.value());
        attributes.put(ChunkMetadata.KEY_MAX_SIZE, maxSize);
        attributes.put(ChunkMetadata.KEY_OVERLAP, overlap);
        attributes.put(ChunkMetadata.KEY_ORIGINAL_TOKEN_COUNT, totalTokens);
        attributes.put(ChunkMetadata.KEY_INDEXED_TOKEN_COUNT, totalTokens);
        attributes.put(ChunkMetadata.KEY_CHUNK_TOKEN_COUNT, chunkTokenCount);
        attributes.put(ChunkMetadata.KEY_CHUNK_TOKEN_START, tokenStart);
        attributes.put(ChunkMetadata.KEY_CHUNK_TOKEN_END, tokenEnd);
        attributes.put(ChunkMetadata.KEY_TOKENIZER_PROVIDER, resolvedTokenizer.provider());
        attributes.put(ChunkMetadata.KEY_TOKENIZER_ENCODING, resolvedTokenizer.encoding());
        attributes.put(ChunkMetadata.KEY_TOKENIZER_MODEL, resolvedTokenizer.tokenizerModel());
        attributes.put(ChunkMetadata.KEY_TOKENIZER_SELECTION_SOURCE, resolvedTokenizer.selectionSource());
        attributes.put(ChunkMetadata.KEY_TOKENIZER_CONFIDENCE, resolvedTokenizer.confidence());
        attributes.put(ChunkMetadata.KEY_TOKENIZER_FALLBACK_USED, resolvedTokenizer.fallbackUsed());
        attributes.put(ChunkMetadata.KEY_TOKENIZER_WARNINGS, resolvedTokenizer.warnings());
        ChunkMetadata metadata = ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, order)
                .sourceDocumentId(context.sourceDocumentId())
                .chunkType(ChunkType.CHILD)
                .objectType(context.objectType())
                .objectId(context.objectId())
                .tokenCount(chunkTokenCount)
                .charCount(content.length())
                .attributes(attributes)
                .build();
        return Chunk.of(id, content, metadata);
    }

    private List<Chunk> linkNeighbors(ChunkingContext context, List<Chunk> chunks) {
        List<Chunk> linked = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            ChunkMetadata metadata = ChunkMetadata.builder(chunk.metadata().strategy(), chunk.metadata().order())
                    .sourceDocumentId(chunk.metadata().sourceDocumentId())
                    .chunkType(chunk.metadata().chunkType())
                    .previousChunkId(i == 0 ? null : chunkId(context.sourceDocumentId(), i - 1))
                    .nextChunkId(i == chunks.size() - 1 ? null : chunkId(context.sourceDocumentId(), i + 1))
                    .objectType(chunk.metadata().objectType())
                    .objectId(chunk.metadata().objectId())
                    .tokenCount(chunk.metadata().tokenCount())
                    .charCount(chunk.metadata().charCount())
                    .attributes(chunk.metadata().attributes())
                    .build();
            linked.add(Chunk.of(chunk.id(), chunk.content(), metadata));
        }
        return linked;
    }

    private String chunkId(String sourceDocumentId, int order) {
        String prefix = sourceDocumentId == null || sourceDocumentId.isBlank() ? "document" : sourceDocumentId;
        return prefix + "-" + order;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
