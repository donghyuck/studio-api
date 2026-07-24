package studio.one.platform.ai.model.migration;

public record LegacyModelDataMapping(
        String legacySpaceId,
        String model,
        Integer dimension,
        String deploymentId,
        String catalogId,
        String embeddingSpaceId) {

    public boolean matches(LegacyEmbeddingIdentity identity) {
        return identity != null
                && equals(legacySpaceId, identity.legacySpaceId())
                && equals(model, identity.model())
                && equals(dimension, identity.dimension());
    }

    private static boolean equals(Object first, Object second) {
        return first != null && first.equals(second);
    }
}
