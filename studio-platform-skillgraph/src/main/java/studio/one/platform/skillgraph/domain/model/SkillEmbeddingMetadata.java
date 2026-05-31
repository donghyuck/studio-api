package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillEmbeddingMetadata(
        String embeddingProvider,
        String embeddingModel,
        int embeddingDimension,
        Instant createdAt) {
}
