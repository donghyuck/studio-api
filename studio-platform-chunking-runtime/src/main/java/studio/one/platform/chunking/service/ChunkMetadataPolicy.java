package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingStrategyType;

final class ChunkMetadataPolicy {

    static final String FALLBACK_NOT_REQUIRED = "NOT_REQUIRED";
    static final String FALLBACK_APPLIED = "APPLIED";
    static final String QUALITY_VALID = "VALID";
    static final String QUALITY_REVIEW_REQUIRED = "REVIEW_REQUIRED";

    private static final String ISSUE_EMPTY_CONTENT = "EMPTY_CONTENT";
    private static final String ISSUE_MAX_SIZE_EXCEEDED = "MAX_SIZE_EXCEEDED";
    private static final String ISSUE_MISSING_PROVENANCE = "MISSING_PROVENANCE";
    private static final String ISSUE_NORMALIZATION_REVIEW_REQUIRED = "NORMALIZATION_REVIEW_REQUIRED";
    private static final String ISSUE_MARKDOWN_REVIEW_REQUIRED = "MARKDOWN_QUALITY_REVIEW_REQUIRED";

    private ChunkMetadataPolicy() {
    }

    static List<Chunk> markCompleted(
            ChunkingContext context,
            List<Chunk> chunks,
            ChunkingStrategyType requestedStrategy,
            ChunkingStrategyType actualStrategy,
            int maxSize,
            int overlap) {
        return enrich(context, chunks, requestedStrategy, actualStrategy, FALLBACK_NOT_REQUIRED, null, null, null,
                maxSize, overlap);
    }

    static List<Chunk> markFallback(
            ChunkingContext context,
            List<Chunk> chunks,
            ChunkingStrategyType requestedStrategy,
            ChunkingStrategyType fallbackFrom,
            ChunkingStrategyType fallbackTo,
            String fallbackReason,
            int maxSize,
            int overlap) {
        return enrich(context, chunks, requestedStrategy, fallbackTo, FALLBACK_APPLIED, fallbackFrom, fallbackTo,
                fallbackReason, maxSize, overlap);
    }

    static boolean hasFatalIssues(List<Chunk> chunks, ChunkUnit unit, int maxSize) {
        if (chunks == null || chunks.isEmpty()) {
            return true;
        }
        return chunks.stream()
                .map(chunk -> qualityIssues(chunk, unit, maxSize))
                .anyMatch(issues -> issues.contains(ISSUE_EMPTY_CONTENT)
                        || issues.contains(ISSUE_MAX_SIZE_EXCEEDED));
    }

    private static List<Chunk> enrich(
            ChunkingContext context,
            List<Chunk> chunks,
            ChunkingStrategyType requestedStrategy,
            ChunkingStrategyType actualStrategy,
            String fallbackStatus,
            ChunkingStrategyType fallbackFrom,
            ChunkingStrategyType fallbackTo,
            String fallbackReason,
            int maxSize,
            int overlap) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        ChunkUnit unit = context.unit();
        return chunks.stream()
                .map(chunk -> enrichChunk(context, chunk, requestedStrategy, actualStrategy, fallbackStatus,
                        fallbackFrom, fallbackTo, fallbackReason, unit, maxSize, overlap))
                .toList();
    }

    private static Chunk enrichChunk(
            ChunkingContext context,
            Chunk chunk,
            ChunkingStrategyType requestedStrategy,
            ChunkingStrategyType actualStrategy,
            String fallbackStatus,
            ChunkingStrategyType fallbackFrom,
            ChunkingStrategyType fallbackTo,
            String fallbackReason,
            ChunkUnit unit,
            int maxSize,
            int overlap) {
        List<String> qualityIssues = new ArrayList<>(qualityIssues(chunk, unit, maxSize));
        mergeSourceQualityIssues(qualityIssues, context.metadata());
        Map<String, Object> attributes = new LinkedHashMap<>(context.metadata());
        attributes.putAll(chunk.metadata().attributes());
        attributes.put(ChunkMetadata.KEY_REQUESTED_CHUNKING_STRATEGY, value(requestedStrategy));
        attributes.put(ChunkMetadata.KEY_ACTUAL_CHUNKING_STRATEGY, value(actualStrategy));
        attributes.put(ChunkMetadata.KEY_FALLBACK_STATUS, fallbackStatus);
        putIfPresent(attributes, ChunkMetadata.KEY_FALLBACK_FROM, value(fallbackFrom));
        putIfPresent(attributes, ChunkMetadata.KEY_FALLBACK_TO, value(fallbackTo));
        putIfPresent(attributes, ChunkMetadata.KEY_FALLBACK_REASON, fallbackReason);
        attributes.put(ChunkMetadata.KEY_CHUNK_QUALITY_STATUS,
                qualityIssues.isEmpty() ? QUALITY_VALID : QUALITY_REVIEW_REQUIRED);
        attributes.put(ChunkMetadata.KEY_CHUNK_QUALITY_ISSUES, List.copyOf(qualityIssues));
        attributes.put(ChunkMetadata.KEY_CHUNK_UNIT, unit.value());
        attributes.put(ChunkMetadata.KEY_MAX_SIZE, maxSize);
        attributes.put(ChunkMetadata.KEY_OVERLAP, overlap);

        ChunkMetadata metadata = rebuildMetadata(chunk.metadata(), attributes);
        return Chunk.of(chunk.id(), chunk.content(), metadata);
    }

    private static void mergeSourceQualityIssues(List<String> issues, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        int beforeNormalization = issues.size();
        addIssueValues(issues, metadata.get("normalizationIssues"));
        if (reviewRequired(metadata.get("normalizationStatus")) && issues.size() == beforeNormalization) {
            addIssue(issues, ISSUE_NORMALIZATION_REVIEW_REQUIRED);
        }
        int beforeMarkdown = issues.size();
        addIssueValues(issues, metadata.get("markdownQualityIssues"));
        if (reviewRequired(metadata.get("markdownQualityStatus")) && issues.size() == beforeMarkdown) {
            addIssue(issues, ISSUE_MARKDOWN_REVIEW_REQUIRED);
        }
        addIssueValues(issues, metadata.get(ChunkMetadata.KEY_CHUNK_QUALITY_ISSUES));
    }

    private static void addIssueValues(List<String> issues, Object value) {
        if (value instanceof Collection<?> values) {
            values.forEach(item -> addIssue(issues, item == null ? null : item.toString()));
            return;
        }
        addIssue(issues, value == null ? null : value.toString());
    }

    private static void addIssue(List<String> issues, String issue) {
        if (issue == null || issue.isBlank() || issues.contains(issue.trim())) {
            return;
        }
        issues.add(issue.trim());
    }

    private static boolean reviewRequired(Object value) {
        return value != null && "REVIEW_REQUIRED".equalsIgnoreCase(value.toString().trim());
    }

    private static List<String> qualityIssues(Chunk chunk, ChunkUnit unit, int maxSize) {
        if (chunk == null) {
            return List.of(ISSUE_EMPTY_CONTENT);
        }
        List<String> issues = new ArrayList<>();
        Map<String, Object> metadata = chunk.metadata().toMap();
        if (chunk.content() == null || chunk.content().isBlank()) {
            issues.add(ISSUE_EMPTY_CONTENT);
        }
        if (maxSize > 0 && sizeOf(chunk, unit) > maxSize) {
            issues.add(ISSUE_MAX_SIZE_EXCEEDED);
        }
        if (!hasProvenance(chunk.metadata(), metadata)) {
            issues.add(ISSUE_MISSING_PROVENANCE);
        }
        return List.copyOf(issues);
    }

    private static int sizeOf(Chunk chunk, ChunkUnit unit) {
        if (unit == ChunkUnit.TOKEN) {
            Integer tokenCount = chunk.metadata().tokenCount();
            return tokenCount == null ? ChunkSizing.estimateTokens(chunk.content()) : tokenCount;
        }
        return chunk.content() == null ? 0 : chunk.content().length();
    }

    private static boolean hasProvenance(ChunkMetadata metadata, Map<String, Object> metadataMap) {
        if (metadata.startOffset() != null && metadata.endOffset() != null) {
            return true;
        }
        if (!metadata.blockIds().isEmpty()) {
            return true;
        }
        if (hasText(metadataMap.get(ChunkMetadata.KEY_SOURCE_REF))
                || hasNonEmptyCollection(metadataMap.get(ChunkMetadata.KEY_SOURCE_REFS))
                || metadataMap.containsKey(ChunkMetadata.KEY_PAGE)
                || metadataMap.containsKey(ChunkMetadata.KEY_SLIDE)) {
            return true;
        }
        return metadataMap.containsKey(ChunkMetadata.KEY_CHUNK_TOKEN_START)
                && metadataMap.containsKey(ChunkMetadata.KEY_CHUNK_TOKEN_END);
    }

    private static boolean hasText(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private static boolean hasNonEmptyCollection(Object value) {
        return value instanceof Collection<?> collection && !collection.isEmpty();
    }

    private static void putIfPresent(Map<String, Object> attributes, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        attributes.put(key, value);
    }

    private static String value(ChunkingStrategyType strategy) {
        return strategy == null ? null : strategy.value();
    }

    private static ChunkMetadata rebuildMetadata(ChunkMetadata metadata, Map<String, Object> attributes) {
        return ChunkMetadata.builder(metadata.strategy(), metadata.order())
                .sourceDocumentId(metadata.sourceDocumentId())
                .parentId(metadata.parentId())
                .chunkType(metadata.chunkType())
                .parentChunkId(metadata.parentChunkId())
                .previousChunkId(metadata.previousChunkId())
                .nextChunkId(metadata.nextChunkId())
                .section(metadata.section())
                .objectType(metadata.objectType())
                .objectId(metadata.objectId())
                .startOffset(metadata.startOffset())
                .endOffset(metadata.endOffset())
                .tokenCount(metadata.tokenCount())
                .charCount(metadata.charCount())
                .blockIds(metadata.blockIds())
                .confidence(metadata.confidence())
                .attributes(attributes)
                .build();
    }
}
