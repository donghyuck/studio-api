package studio.one.platform.ai.model.migration;

public record LegacyEmbeddingIdentity(
        String profileId,
        String provider,
        String model,
        Integer dimension,
        String legacySpaceId,
        long rowCount) {
}
