package studio.one.platform.ai.web.controller;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import studio.one.platform.ai.autoconfigure.AiWebRagProperties;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkContextExpander;
import studio.one.platform.chunking.core.ChunkContextExpansion;
import studio.one.platform.chunking.core.ChunkContextExpansionRequest;
import studio.one.platform.chunking.core.ChunkContextExpansionStrategy;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkingStrategyType;

/**
 * Builds bounded RAG context prompts for chat completion requests.
 */
public class RagContextBuilder {

    private static final String NO_CONTEXT_MESSAGE = "참고할 문서가 없습니다. 일반적으로 답변하세요.";
    private static final String HEADER = "다음 문서 내용을 참고해 답변하세요:\n";
    static final String KEY_CHUNK_ID = "chunkId";
    private static final String KEY_DOCUMENT_ID = "documentId";

    private final int maxChunks;
    private final int maxChars;
    private final boolean includeScores;
    private final AiWebRagProperties.ExpansionProperties expansion;
    private final List<ChunkContextExpander> contextExpanders;

    public RagContextBuilder(AiWebRagProperties properties) {
        this(properties, List.of());
    }

    public RagContextBuilder(AiWebRagProperties properties, List<ChunkContextExpander> contextExpanders) {
        this(properties.getContext().getMaxChunks(),
                properties.getContext().getMaxChars(),
                properties.getContext().isIncludeScores(),
                properties.getContext().getExpansion(),
                contextExpanders);
    }

    public RagContextBuilder(int maxChunks, int maxChars, boolean includeScores) {
        this(maxChunks, maxChars, includeScores, new AiWebRagProperties.ExpansionProperties(), List.of());
    }

    public RagContextBuilder(
            int maxChunks,
            int maxChars,
            boolean includeScores,
            List<ChunkContextExpander> contextExpanders) {
        this(maxChunks, maxChars, includeScores, new AiWebRagProperties.ExpansionProperties(), contextExpanders);
    }

    public RagContextBuilder(
            int maxChunks,
            int maxChars,
            boolean includeScores,
            AiWebRagProperties.ExpansionProperties expansion,
            List<ChunkContextExpander> contextExpanders) {
        this.maxChunks = Math.max(0, maxChunks);
        this.maxChars = Math.max(0, maxChars);
        this.includeScores = includeScores;
        this.expansion = expansion == null ? new AiWebRagProperties.ExpansionProperties() : expansion;
        this.contextExpanders = contextExpanders == null ? List.of()
                : contextExpanders.stream().filter(Objects::nonNull).toList();
    }

    public static RagContextBuilder defaults() {
        return new RagContextBuilder(8, 12_000, true);
    }

    public boolean supportsExpansion() {
        return expansion.isEnabled() && !contextExpanders.isEmpty();
    }

    public String build(List<RagSearchResult> results) {
        return build(results, results);
    }

    public String build(List<RagSearchResult> results, List<RagSearchResult> expansionCandidates) {
        return buildWithDiagnostics(results, expansionCandidates).context();
    }

    public BuildResult buildWithDiagnostics(List<RagSearchResult> results, List<RagSearchResult> expansionCandidates) {
        int resultCount = results == null ? 0 : results.size();
        int candidateCount = expansionCandidates == null ? 0 : expansionCandidates.size();
        boolean expansionSupported = supportsExpansion();
        if (results == null || results.isEmpty() || maxChunks == 0 || maxChars == 0) {
            return new BuildResult(NO_CONTEXT_MESSAGE, new Diagnostics(
                    expansionSupported, false, null, 0, 0, candidateCount, resultCount, "no_context"));
        }
        StringBuilder sb = new StringBuilder(HEADER);
        if (sb.length() > maxChars) {
            return new BuildResult(NO_CONTEXT_MESSAGE, new Diagnostics(
                    expansionSupported, false, null, 0, 0, candidateCount, resultCount, "context_limit"));
        }
        int count = Math.min(maxChunks, results.size());
        int expandedHitCount = 0;
        int fallbackHitCount = 0;
        String strategy = null;
        String fallbackReason = expansionSupported ? null : "disabled";
        boolean contextLimitHit = false;
        for (int i = 0; i < count; i++) {
            RagSearchResult original = results.get(i);
            ExpansionAttempt attempt = expandResultWithDiagnostics(original, expansionCandidates);
            String chunk = formatChunk(i + 1, attempt.result());
            if (!appendWithinLimit(sb, chunk)) {
                contextLimitHit = true;
                if (!attempt.expanded()) {
                    fallbackReason = "context_limit";
                    break;
                }
                String fallbackChunk = formatChunk(i + 1, original);
                if (!appendWithinLimit(sb, fallbackChunk)) {
                    fallbackReason = "context_limit";
                    break;
                }
                fallbackHitCount++;
                fallbackReason = "context_limit";
                if (strategy == null) {
                    strategy = attempt.strategy();
                }
                continue;
            }
            if (attempt.expanded()) {
                expandedHitCount++;
                if (strategy == null) {
                    strategy = attempt.strategy();
                }
            } else if (fallbackReason == null) {
                fallbackHitCount++;
                fallbackReason = attempt.fallbackReason();
            } else {
                fallbackHitCount++;
            }
        }
        String context = sb.toString().trim();
        if (HEADER.trim().equals(context)) {
            return new BuildResult(NO_CONTEXT_MESSAGE, new Diagnostics(
                    expansionSupported, false, strategy, 0, fallbackHitCount, candidateCount, resultCount,
                    contextLimitHit ? "context_limit" : "no_context"));
        }
        return new BuildResult(context, new Diagnostics(
                expansionSupported,
                expandedHitCount > 0,
                strategy,
                expandedHitCount,
                fallbackHitCount,
                candidateCount,
                resultCount,
                fallbackReason));
    }

    private boolean appendWithinLimit(StringBuilder sb, String chunk) {
        if (sb.length() + chunk.length() > maxChars) {
            return false;
        }
        sb.append(chunk);
        return true;
    }

    private String formatChunk(int index, RagSearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(index).append("] docId=").append(result.documentId());
        if (includeScores) {
            sb.append(" score=").append(String.format("%.3f", result.score()));
        }
        sb.append("\n").append(result.content()).append("\n\n");
        return sb.toString();
    }

    private ExpansionAttempt expandResultWithDiagnostics(RagSearchResult result, List<RagSearchResult> expansionCandidates) {
        if (result == null || !supportsExpansion()) {
            return new ExpansionAttempt(result, false, null, "disabled");
        }
        Optional<Chunk> seed = toChunk(result);
        if (seed.isEmpty() || !hasObjectScope(seed.get())) {
            return new ExpansionAttempt(result, false, null, "missing_object_scope");
        }
        List<Chunk> availableChunks = availableChunks(seed.get(), expansionCandidates);
        if (availableChunks.isEmpty()) {
            return new ExpansionAttempt(result, false, null, "no_candidates");
        }
        Optional<ChunkContextExpander> expander = selectExpander(seed.get());
        if (expander.isEmpty()) {
            return new ExpansionAttempt(result, false, null, "no_expander");
        }
        String strategy = expander.get().strategy().name().toLowerCase(java.util.Locale.ROOT);
        ChunkContextExpansionRequest request = ChunkContextExpansionRequest.builder(seed.get())
                .availableChunks(availableChunks)
                .previousWindow(expansion.getPreviousWindow())
                .nextWindow(expansion.getNextWindow())
                .includeParentContent(expansion.isIncludeParentContent())
                .build();
        try {
            ChunkContextExpansion expansion = expander.get().expand(request);
            return new ExpansionAttempt(
                    new RagSearchResult(result.documentId(), expansion.content(), result.metadata(), result.score()),
                    true,
                    strategy,
                    null);
        } catch (RuntimeException ignored) {
            return new ExpansionAttempt(result, false, strategy, "expander_failed");
        }
    }

    private List<Chunk> availableChunks(Chunk seed, List<RagSearchResult> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of(seed);
        }
        Map<String, Chunk> chunks = new LinkedHashMap<>();
        candidates.stream()
                .filter(Objects::nonNull)
                .map(this::toChunk)
                .flatMap(Optional::stream)
                .filter(candidate -> sameObjectScope(seed, candidate))
                .sorted(Comparator.comparingInt(chunk -> chunk.metadata().order()))
                .forEach(chunk -> chunks.putIfAbsent(chunk.id(), chunk));
        chunks.putIfAbsent(seed.id(), seed);
        return List.copyOf(chunks.values());
    }

    private Optional<ChunkContextExpander> selectExpander(Chunk seed) {
        ChunkContextExpansionStrategy preferred = preferredStrategy(seed);
        return contextExpanders.stream()
                .filter(expander -> expander.strategy() == preferred)
                .findFirst()
                // Fall back to WINDOW because it preserves the seed chunk and only
                // adds explicitly linked neighbors when a specialized expander is absent.
                .or(() -> contextExpanders.stream()
                        .filter(expander -> expander.strategy() == ChunkContextExpansionStrategy.WINDOW)
                        .findFirst());
    }

    private ChunkContextExpansionStrategy preferredStrategy(Chunk seed) {
        if (seed.metadata().chunkType() == ChunkType.TABLE) {
            return ChunkContextExpansionStrategy.TABLE;
        }
        if (hasText(seed.metadata().parentChunkId())
                || seed.metadata().toMap().containsKey(ChunkMetadata.KEY_PARENT_CHUNK_CONTENT)) {
            return ChunkContextExpansionStrategy.PARENT_CHILD;
        }
        if (hasText(seed.metadata().section())) {
            return ChunkContextExpansionStrategy.HEADING;
        }
        return ChunkContextExpansionStrategy.WINDOW;
    }

    private Optional<Chunk> toChunk(RagSearchResult result) {
        if (result == null || !hasText(result.content())) {
            return Optional.empty();
        }
        Map<String, Object> metadata = result.metadata();
        String chunkId = text(metadata.get(KEY_CHUNK_ID));
        if (!hasText(chunkId)) {
            chunkId = result.documentId();
        }
        if (!hasText(chunkId)) {
            return Optional.empty();
        }
        int order = intValue(metadata.get(ChunkMetadata.KEY_CHUNK_ORDER), 0);
        ChunkMetadata chunkMetadata = ChunkMetadata.builder(strategy(metadata), order)
                .sourceDocumentId(firstText(metadata, ChunkMetadata.KEY_SOURCE_DOCUMENT_ID, KEY_DOCUMENT_ID))
                .parentId(text(metadata.get(ChunkMetadata.KEY_PARENT_ID)))
                .chunkType(chunkType(metadata))
                .parentChunkId(text(metadata.get(ChunkMetadata.KEY_PARENT_CHUNK_ID)))
                .previousChunkId(text(metadata.get(ChunkMetadata.KEY_PREVIOUS_CHUNK_ID)))
                .nextChunkId(text(metadata.get(ChunkMetadata.KEY_NEXT_CHUNK_ID)))
                .section(firstText(metadata, ChunkMetadata.KEY_SECTION, ChunkMetadata.KEY_HEADING_PATH))
                .objectType(text(metadata.get(ChunkMetadata.KEY_OBJECT_TYPE)))
                .objectId(text(metadata.get(ChunkMetadata.KEY_OBJECT_ID)))
                .charCount(result.content().length())
                .attributes(metadata)
                .build();
        return Optional.of(Chunk.of(chunkId, result.content(), chunkMetadata));
    }

    private ChunkType chunkType(Map<String, Object> metadata) {
        try {
            return ChunkType.from(text(metadata.get(ChunkMetadata.KEY_CHUNK_TYPE)));
        } catch (IllegalArgumentException ignored) {
            return ChunkType.CHILD;
        }
    }

    private ChunkingStrategyType strategy(Map<String, Object> metadata) {
        try {
            return ChunkingStrategyType.from(text(metadata.get(ChunkMetadata.KEY_STRATEGY)));
        } catch (IllegalArgumentException ignored) {
            return ChunkingStrategyType.RECURSIVE;
        }
    }

    private boolean hasObjectScope(Chunk chunk) {
        return hasText(chunk.metadata().objectType()) && hasText(chunk.metadata().objectId());
    }

    private boolean sameObjectScope(Chunk left, Chunk right) {
        return Objects.equals(left.metadata().objectType(), right.metadata().objectType())
                && Objects.equals(left.metadata().objectId(), right.metadata().objectId());
    }

    private String firstText(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            String value = text(metadata.get(key));
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value instanceof String stringValue) {
            try {
                return Math.max(0, Integer.parseInt(stringValue.trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String text(Object value) {
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record BuildResult(String context, Diagnostics diagnostics) {
    }

    public record Diagnostics(
            boolean expansionSupported,
            boolean applied,
            String strategy,
            int expandedHitCount,
            int fallbackHitCount,
            int candidateCount,
            int resultCount,
            String fallbackReason) {

        public Map<String, Object> toMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("expansionSupported", expansionSupported);
            metadata.put("applied", applied);
            put(metadata, "strategy", strategy);
            metadata.put("expandedHitCount", expandedHitCount);
            metadata.put("fallbackHitCount", fallbackHitCount);
            metadata.put("candidateCount", candidateCount);
            metadata.put("resultCount", resultCount);
            put(metadata, "fallbackReason", fallbackReason);
            return Map.copyOf(metadata);
        }

        private static void put(Map<String, Object> metadata, String key, Object value) {
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                metadata.put(key, value);
            }
        }
    }

    private record ExpansionAttempt(
            RagSearchResult result,
            boolean expanded,
            String strategy,
            String fallbackReason) {
    }
}
