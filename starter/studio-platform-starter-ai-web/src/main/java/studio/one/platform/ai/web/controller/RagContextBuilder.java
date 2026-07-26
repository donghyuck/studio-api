package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
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

    private static final String NO_CONTEXT_MESSAGE = "제공된 문서에서 확인할 수 없습니다.";
    private static final String HEADER = "다음 문서 내용을 참고해 답변하세요:\n";
    static final String KEY_CHUNK_ID = "chunkId";
    private static final String KEY_DOCUMENT_ID = "documentId";
    private final int maxChunks;
    private final int maxChars;
    private final int maxChunkChars;
    private final boolean includeScores;
    private final AiWebRagProperties.ExpansionProperties expansion;
    private final List<ChunkContextExpander> contextExpanders;

    public RagContextBuilder(AiWebRagProperties properties) {
        this(properties, List.of());
    }

    public RagContextBuilder(AiWebRagProperties properties, List<ChunkContextExpander> contextExpanders) {
        this(properties.getContext().getMaxChunks(),
                properties.getContext().getMaxChars(),
                properties.getContext().getMaxChunkChars(),
                properties.getContext().isIncludeScores(),
                properties.getContext().getExpansion(),
                contextExpanders);
    }

    public RagContextBuilder(int maxChunks, int maxChars, boolean includeScores) {
        this(maxChunks, maxChars, 2_000, includeScores, new AiWebRagProperties.ExpansionProperties(), List.of());
    }

    public RagContextBuilder(
            int maxChunks,
            int maxChars,
            boolean includeScores,
            List<ChunkContextExpander> contextExpanders) {
        this(maxChunks, maxChars, 2_000, includeScores, new AiWebRagProperties.ExpansionProperties(),
                contextExpanders);
    }

    public RagContextBuilder(
            int maxChunks,
            int maxChars,
            boolean includeScores,
            AiWebRagProperties.ExpansionProperties expansion,
            List<ChunkContextExpander> contextExpanders) {
        this(maxChunks, maxChars, 2_000, includeScores, expansion, contextExpanders);
    }

    public RagContextBuilder(
            int maxChunks,
            int maxChars,
            int maxChunkChars,
            boolean includeScores,
            AiWebRagProperties.ExpansionProperties expansion,
            List<ChunkContextExpander> contextExpanders) {
        this.maxChunks = Math.max(0, maxChunks);
        this.maxChars = Math.max(0, maxChars);
        this.maxChunkChars = Math.max(1, maxChunkChars);
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
                    expansionSupported, false, null, 0, 0, candidateCount, resultCount, 0, 0, 0,
                    maxChunks, maxChars, 0, "no_context"));
        }
        StringBuilder sb = new StringBuilder(HEADER);
        if (sb.length() > maxChars) {
            return new BuildResult(NO_CONTEXT_MESSAGE, new Diagnostics(
                    expansionSupported, false, null, 0, 0, candidateCount, resultCount, 0, 0, resultCount,
                    maxChunks, maxChars, 0, "context_limit"));
        }
        int count = Math.min(maxChunks, results.size());
        int expandedHitCount = 0;
        int fallbackHitCount = 0;
        int compressedHitCount = 0;
        int skippedHitCount = Math.max(0, resultCount - count);
        String strategy = null;
        String fallbackReason = expansionSupported ? null : "disabled";
        boolean contextLimitHit = false;
        List<RagSearchResult> usedResults = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RagSearchResult original = results.get(i);
            ExpansionAttempt attempt = expandResultWithDiagnostics(original, expansionCandidates);
            int promptIndex = usedResults.size() + 1;
            PackedChunk chunk = packChunk(promptIndex, attempt.result());
            if (!appendWithinLimit(sb, chunk.text())) {
                contextLimitHit = true;
                if (!attempt.expanded()) {
                    Optional<PackedChunk> fitted = packChunkToRemainingBudget(
                            promptIndex, original, maxChars - sb.length());
                    if (fitted.isPresent() && appendWithinLimit(sb, fitted.get().text())) {
                        usedResults.add(fitted.get().result());
                        if (fitted.get().compressed()) {
                            compressedHitCount++;
                        }
                        fallbackReason = "context_limit";
                        continue;
                    }
                    skippedHitCount++;
                    fallbackReason = "context_limit";
                    continue;
                }
                PackedChunk fallbackChunk = packChunk(promptIndex, original);
                if (!appendWithinLimit(sb, fallbackChunk.text())) {
                    Optional<PackedChunk> fitted = packChunkToRemainingBudget(
                            promptIndex, original, maxChars - sb.length());
                    if (fitted.isEmpty() || !appendWithinLimit(sb, fitted.get().text())) {
                        skippedHitCount++;
                        fallbackReason = "context_limit";
                        continue;
                    }
                    usedResults.add(fitted.get().result());
                    if (fitted.get().compressed()) {
                        compressedHitCount++;
                    }
                } else {
                    usedResults.add(fallbackChunk.result());
                    if (fallbackChunk.compressed()) {
                        compressedHitCount++;
                    }
                }
                fallbackHitCount++;
                fallbackReason = "context_limit";
                if (strategy == null) {
                    strategy = attempt.strategy();
                }
                continue;
            }
            usedResults.add(chunk.result());
            if (chunk.compressed()) {
                compressedHitCount++;
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
                    0, compressedHitCount, skippedHitCount, maxChunks, maxChars, 0,
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
                usedResults.size(),
                compressedHitCount,
                skippedHitCount,
                maxChunks,
                maxChars,
                context.length(),
                fallbackReason),
                usedResults);
    }

    private boolean appendWithinLimit(StringBuilder sb, String chunk) {
        if (sb.length() + chunk.length() > maxChars) {
            return false;
        }
        sb.append(chunk);
        return true;
    }

    private PackedChunk packChunk(int index, RagSearchResult result) {
        String packedContent = excerpt(result.content(), maxChunkChars);
        boolean compressed = !packedContent.equals(result.content());
        RagSearchResult packedResult = compressed
                ? packedResult(result, packedContent, true)
                : result;
        return new PackedChunk(formatChunk(index, packedResult), packedResult, compressed);
    }

    private Optional<PackedChunk> packChunkToRemainingBudget(int index, RagSearchResult result, int remainingChars) {
        if (remainingChars <= 0) {
            return Optional.empty();
        }
        int overhead = formatChunk(index,
                new RagSearchResult(result.documentId(), "", result.metadata(), result.score()))
                .length();
        int contentBudget = remainingChars - overhead;
        if (contentBudget <= 0) {
            return Optional.empty();
        }
        String packedContent = excerpt(result.content(), Math.min(maxChunkChars, contentBudget));
        RagSearchResult packedResult = packedResult(
                result,
                packedContent,
                !packedContent.equals(result.content()));
        String packedText = formatChunk(index, packedResult);
        if (packedText.length() > remainingChars) {
            return Optional.empty();
        }
        return Optional.of(new PackedChunk(packedText, packedResult, !packedContent.equals(result.content())));
    }

    private RagSearchResult packedResult(RagSearchResult source, String packedContent, boolean truncated) {
        Map<String, Object> metadata = new LinkedHashMap<>(
                source.metadata() == null ? Map.of() : source.metadata());
        metadata.put("truncated", truncated);
        metadata.putIfAbsent("startOffset", 0);
        metadata.put("endOffset", packedContent.length());
        return new RagSearchResult(source.documentId(), packedContent, Map.copyOf(metadata), source.score());
    }

    private String formatChunk(int index, RagSearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(index).append("]");
        int headerLength = sb.length();
        appendSourceMetadata(sb, result.metadata());
        sb.append(sb.length() > headerLength ? "\n내용:\n" : "\n")
                .append(result.content())
                .append("\n\n");
        return sb.toString();
    }

    private void appendSourceMetadata(StringBuilder target, Map<String, Object> metadata) {
        Map<String, Object> values = metadata == null ? Map.of() : metadata;
        appendMetadataLine(target, "원본 파일", firstText(values,
                "sourceFileName", "filename", "fileName", "name", "sourceName"));
        appendMetadataLine(target, "문서 제목", firstText(values, "docTitle", "documentTitle"));
        Integer page = firstInteger(values, ChunkMetadata.KEY_PAGE, "pageNumber", "pageFrom");
        if (page != null) {
            appendMetadataLine(target, "페이지", page.toString());
        }
        appendMetadataLine(target, "섹션", firstText(values,
                ChunkMetadata.KEY_SECTION, ChunkMetadata.KEY_HEADING_PATH, "heading"));
        appendMetadataLine(target, "원문 위치", firstText(values,
                ChunkMetadata.KEY_SOURCE_REF, "sourceRef"));
    }

    private void appendMetadataLine(StringBuilder target, String label, String value) {
        String normalized = singleLine(value);
        if (normalized != null) {
            target.append("\n").append(label).append(": ").append(normalized);
        }
    }

    private Integer firstInteger(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                try {
                    return Integer.valueOf(value.toString().trim());
                } catch (NumberFormatException ignored) {
                    // Try the next provenance key.
                }
            }
        }
        return null;
    }

    private String singleLine(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC)
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }

    private String excerpt(String content, int limit) {
        if (content == null || content.length() <= limit) {
            return content == null ? "" : content;
        }
        return content.substring(0, limit);
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
            ChunkContextExpansion expanded = expander.get().expand(request);
            Map<String, Object> expandedMetadata = new LinkedHashMap<>(result.metadata());
            List<Map<String, Object>> sourceSpans = expandedSourceSpans(
                    expanded.content(), seed.get(), expansionCandidates);
            if (!sourceSpans.isEmpty()) {
                expandedMetadata.put("sourceSpans", sourceSpans);
            }
            return new ExpansionAttempt(
                    new RagSearchResult(result.documentId(), expanded.content(), expandedMetadata, result.score()),
                    true,
                    strategy,
                    null);
        } catch (RuntimeException ignored) {
            return new ExpansionAttempt(result, false, strategy, "expander_failed");
        }
    }

    private List<Map<String, Object>> expandedSourceSpans(
            String expandedContent,
            Chunk seed,
            List<RagSearchResult> candidates) {
        if (!hasText(expandedContent)) {
            return List.of();
        }
        Map<String, RagSearchResult> scoped = new LinkedHashMap<>();
        if (candidates != null) {
            candidates.stream()
                    .filter(Objects::nonNull)
                    .filter(candidate -> toChunk(candidate)
                            .filter(chunk -> sameObjectScope(seed, chunk))
                            .isPresent())
                    .forEach(candidate -> scoped.putIfAbsent(
                            firstText(candidate.metadata(), KEY_CHUNK_ID) == null
                                    ? candidate.documentId()
                                    : firstText(candidate.metadata(), KEY_CHUNK_ID),
                            candidate));
        }
        scoped.putIfAbsent(seed.id(), new RagSearchResult(
                seed.id(), seed.content(), seed.metadata().attributes(), 0.0d));
        List<Map<String, Object>> spans = new ArrayList<>();
        scoped.values().stream()
                .sorted(Comparator.comparingInt(candidate ->
                        intValue(candidate.metadata().get(ChunkMetadata.KEY_CHUNK_ORDER), 0)))
                .forEach(candidate -> {
                    String exactText = candidate.content();
                    int offset = expandedContent.indexOf(exactText);
                    if (offset < 0) {
                        return;
                    }
                    Map<String, Object> span = new LinkedHashMap<>();
                    span.put("exactText", exactText);
                    span.put("chunkId", firstText(candidate.metadata(), KEY_CHUNK_ID) == null
                            ? candidate.documentId()
                            : firstText(candidate.metadata(), KEY_CHUNK_ID));
                    putIfPresent(span, "sourceRef", firstText(candidate.metadata(),
                            ChunkMetadata.KEY_SOURCE_REF, "sourceRef"));
                    putIfPresent(span, "page", firstInteger(candidate.metadata(),
                            ChunkMetadata.KEY_PAGE, "pageNumber"));
                    putIfPresent(span, "slide", firstInteger(candidate.metadata(), "slide", "slideNumber"));
                    putIfPresent(span, "section", firstText(candidate.metadata(),
                            ChunkMetadata.KEY_SECTION, ChunkMetadata.KEY_HEADING_PATH, "heading"));
                    span.put("startOffset", offset);
                    span.put("endOffset", offset + exactText.length());
                    span.put("truncated", false);
                    Object blockIds = candidate.metadata().get("blockIds");
                    if (blockIds != null) {
                        span.put("blockIds", blockIds);
                    }
                    spans.add(Map.copyOf(span));
                });
        return List.copyOf(spans);
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String textValue) || !textValue.isBlank())) {
            target.put(key, value);
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
            String value = singleLine(Objects.toString(metadata.get(key), null));
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

    public record BuildResult(
            String context,
            Diagnostics diagnostics,
            List<RagSearchResult> usedResults,
            PackedEvidenceSet evidenceSet) {

        public BuildResult(String context, Diagnostics diagnostics) {
            this(context, diagnostics, List.of(), PackedEvidenceSet.empty(
                    context,
                    diagnostics == null ? Map.of() : diagnostics.toMetadata()));
        }

        public BuildResult(String context, Diagnostics diagnostics, List<RagSearchResult> usedResults) {
            this(context, diagnostics, usedResults, PackedEvidenceSet.from(
                    context,
                    usedResults,
                    diagnostics == null ? Map.of() : diagnostics.toMetadata()));
        }

        public BuildResult {
            usedResults = usedResults == null ? List.of() : List.copyOf(usedResults);
            evidenceSet = evidenceSet == null
                    ? PackedEvidenceSet.from(
                            context,
                            usedResults,
                            diagnostics == null ? Map.of() : diagnostics.toMetadata())
                    : evidenceSet;
        }
    }

    public record Diagnostics(
            boolean expansionSupported,
            boolean applied,
            String strategy,
            int expandedHitCount,
            int fallbackHitCount,
            int candidateCount,
            int resultCount,
            int includedCount,
            int compressedHitCount,
            int skippedHitCount,
            int maxChunks,
            int maxChars,
            int contextCharCount,
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
            metadata.put("includedCount", includedCount);
            metadata.put("compressedHitCount", compressedHitCount);
            metadata.put("skippedHitCount", skippedHitCount);
            metadata.put("maxChunks", maxChunks);
            metadata.put("maxChars", maxChars);
            metadata.put("contextCharCount", contextCharCount);
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

    private record PackedChunk(
            String text,
            RagSearchResult result,
            boolean compressed) {
    }
}
