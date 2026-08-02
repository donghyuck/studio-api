package studio.one.platform.ai.core.rag.indexed;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ResolvedIndexedRagSource(
        String sourceType,
        String sourceId,
        String revisionId,
        String objectType,
        String objectId,
        String contentHash,
        String embeddingDeploymentId,
        String embeddingSpaceId,
        Set<String> partitionIds,
        Map<String, Object> metadata) {

    public ResolvedIndexedRagSource {
        sourceType = requireText(sourceType, "sourceType");
        sourceId = requireText(sourceId, "sourceId");
        revisionId = requireText(revisionId, "revisionId");
        objectType = requireText(objectType, "objectType");
        objectId = requireText(objectId, "objectId");
        contentHash = requireText(contentHash, "contentHash");
        embeddingDeploymentId = requireText(embeddingDeploymentId, "embeddingDeploymentId");
        partitionIds = partitionIds == null ? Set.of() : Set.copyOf(partitionIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public ResolvedIndexedRagSource(
            String sourceType,
            String sourceId,
            String revisionId,
            String objectType,
            String objectId,
            String contentHash,
            String embeddingDeploymentId,
            String embeddingSpaceId,
            Map<String, Object> metadata) {
        this(
                sourceType,
                sourceId,
                revisionId,
                objectType,
                objectId,
                contentHash,
                embeddingDeploymentId,
                embeddingSpaceId,
                Set.of(),
                metadata);
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
