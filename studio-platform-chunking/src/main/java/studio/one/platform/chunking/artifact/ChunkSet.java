package studio.one.platform.chunking.artifact;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record ChunkSet(
        String chunkSetId,
        String objectType,
        String objectId,
        String documentId,
        String sourceRevisionId,
        String sourceContentHash,
        String strategy,
        String strategyHash,
        String chunkUnit,
        Integer maxSize,
        Integer overlap,
        ChunkSetStatus status,
        ChunkSetQualityStatus qualityStatus,
        List<String> qualityIssues,
        Map<String, Object> metadata,
        List<ChunkSetItem> items,
        Instant createdAt,
        Instant updatedAt) {

    public ChunkSet {
        chunkSetId = normalizeRequired(chunkSetId, "chunkSetId");
        objectType = normalizeRequired(objectType, "objectType");
        objectId = normalizeRequired(objectId, "objectId");
        documentId = normalizeRequired(documentId, "documentId");
        sourceRevisionId = normalizeRequired(sourceRevisionId, "sourceRevisionId");
        sourceContentHash = normalizeRequired(sourceContentHash, "sourceContentHash");
        strategy = normalizeRequired(strategy, "strategy");
        strategyHash = normalizeRequired(strategyHash, "strategyHash");
        chunkUnit = normalize(chunkUnit);
        if (maxSize != null && maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be greater than zero");
        }
        if (overlap != null && overlap < 0) {
            throw new IllegalArgumentException("overlap must not be negative");
        }
        status = status == null ? ChunkSetStatus.READY : status;
        qualityStatus = qualityStatus == null ? ChunkSetQualityStatus.VALID : qualityStatus;
        qualityIssues = qualityIssues == null ? List.of() : qualityIssues.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        items = items == null ? List.of() : items.stream()
                .sorted(Comparator.comparingInt(ChunkSetItem::chunkIndex))
                .toList();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        validateIndexes(items);
    }

    public boolean indexEligible() {
        return status == ChunkSetStatus.READY
                && !Boolean.FALSE.equals(metadata.get("ragIndexEligible"));
    }

    private static void validateIndexes(List<ChunkSetItem> items) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).chunkIndex() != index) {
                throw new IllegalArgumentException("ChunkSet item indexes must be contiguous from zero");
            }
        }
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
