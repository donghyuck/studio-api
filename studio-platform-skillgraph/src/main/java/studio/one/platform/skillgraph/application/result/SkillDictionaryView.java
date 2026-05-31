package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillEmbeddingMetadata;

public record SkillDictionaryView(
        String skillId,
        String name,
        String normalizedName,
        String categoryId,
        String categoryName,
        String status,
        boolean embedded,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        java.util.List<SkillEmbeddingMetadata> embeddings,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillDictionaryView from(SkillDictionary skill) {
        return from(skill, null, java.util.List.of());
    }

    public static SkillDictionaryView from(SkillDictionary skill, String categoryName) {
        return from(skill, categoryName, java.util.List.of());
    }

    public static SkillDictionaryView from(
            SkillDictionary skill,
            String categoryName,
            SkillEmbeddingMetadata embeddingMetadata) {
        return from(skill, categoryName, embeddingMetadata == null ? java.util.List.of() : java.util.List.of(embeddingMetadata));
    }

    public static SkillDictionaryView from(
            SkillDictionary skill,
            String categoryName,
            java.util.List<SkillEmbeddingMetadata> embeddings) {
        SkillEmbeddingMetadata embeddingMetadata = embeddings == null || embeddings.isEmpty() ? null : embeddings.get(0);
        return new SkillDictionaryView(skill.skillId(), skill.name(), skill.normalizedName(),
                skill.categoryId(), categoryName, skill.status(), skill.embedded() || embeddingMetadata != null,
                embeddingMetadata == null ? null : embeddingMetadata.embeddingProvider(),
                embeddingMetadata == null ? null : embeddingMetadata.embeddingModel(),
                embeddingMetadata == null ? null : embeddingMetadata.embeddingDimension(),
                embeddings == null ? java.util.List.of() : java.util.List.copyOf(embeddings),
                skill.createdAt(), skill.updatedAt());
    }
}
