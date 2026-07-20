package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkContextExpander;
import studio.one.platform.chunking.core.ChunkContextExpansion;
import studio.one.platform.chunking.core.ChunkContextExpansionRequest;
import studio.one.platform.chunking.core.ChunkContextExpansionStrategy;
import studio.one.platform.chunking.core.ChunkMetadata;

public class ParentChildChunkContextExpander implements ChunkContextExpander {

    private static final int MAX_PROBLEM_CONTEXT_CHUNKS = 6;

    @Override
    public ChunkContextExpansionStrategy strategy() {
        return ChunkContextExpansionStrategy.PARENT_CHILD;
    }

    @Override
    public ChunkContextExpansion expand(ChunkContextExpansionRequest request) {
        Chunk seed = request.seedChunk();
        String parentChunkId = seed.metadata().parentChunkId();
        List<Chunk> contextChunks = ChunkContextExpansionSupport.sameParent(parentChunkId,
                ChunkContextExpansionSupport.withSeed(seed, request.availableChunks()));
        if (contextChunks.isEmpty()) {
            contextChunks = List.of(seed);
        }
        Map<String, Object> metadata = ChunkContextExpansionSupport.metadata(
                ChunkMetadata.KEY_PARENT_CHUNK_ID,
                parentChunkId);
        String parentContent = request.includeParentContent()
                ? ChunkContextExpansionSupport.parentContent(seed)
                : "";
        if (!parentContent.isBlank() && !sameContent(parentContent, seed.content())) {
            return new ChunkContextExpansion(seed, contextChunks, parentContent, strategy(), metadata);
        }
        List<Chunk> problemContext = problemContext(seed, request.availableChunks());
        if (!problemContext.isEmpty()) {
            Map<String, Object> problemMetadata = new java.util.LinkedHashMap<>(metadata);
            problemMetadata.put("problemContextFallback", true);
            problemMetadata.put("problemContextChunkCount", problemContext.size());
            return ChunkContextExpansion.of(seed, problemContext, strategy(), problemMetadata);
        }
        return ChunkContextExpansion.of(seed, contextChunks, strategy(), metadata);
    }

    private List<Chunk> problemContext(Chunk seed, List<Chunk> availableChunks) {
        if (!questionLike(seed.content())) {
            return List.of();
        }
        Integer page = page(seed);
        if (page == null) {
            return List.of();
        }
        List<Chunk> pageChunks = new ArrayList<>(ChunkContextExpansionSupport.withSeed(seed, availableChunks).stream()
                .filter(chunk -> Objects.equals(page, page(chunk)))
                .filter(chunk -> chunk.metadata().order() <= seed.metadata().order())
                .sorted(Comparator.comparingInt(chunk -> chunk.metadata().order()))
                .toList());
        int seedIndex = pageChunks.indexOf(seed);
        if (seedIndex <= 0) {
            return List.of();
        }
        int lowerBound = Math.max(0, seedIndex - MAX_PROBLEM_CONTEXT_CHUNKS + 1);
        int anchor = -1;
        for (int index = seedIndex - 1; index >= lowerBound; index--) {
            if (conditionLike(pageChunks.get(index).content())) {
                anchor = index;
                break;
            }
        }
        return anchor < 0 ? List.of() : List.copyOf(pageChunks.subList(anchor, seedIndex + 1));
    }

    private boolean questionLike(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String value = content.replaceAll("\\s+", "");
        return value.contains("구하시오")
                || value.contains("구하세요")
                || value.contains("계산하시오")
                || value.matches("(?i).*(find|calculate|solve).*");
    }

    private boolean conditionLike(String content) {
        if (content == null || !content.contains("=")) {
            return false;
        }
        long operators = content.chars().filter(ch -> ch == '+' || ch == '-' || ch == '^' || ch == '=').count();
        return operators >= 2;
    }

    private Integer page(Chunk chunk) {
        Object value = chunk.metadata().toMap().get(ChunkMetadata.KEY_PAGE);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean sameContent(String left, String right) {
        return left == null ? right == null : left.strip().equals(right == null ? "" : right.strip());
    }

}
