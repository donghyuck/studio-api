package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;
import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillDictionaryView;
import studio.one.platform.skillgraph.domain.model.SkillEmbeddingMetadata;

public record SkillDictionaryDto(
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
        List<SkillEmbeddingMetadata> embeddings,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillDictionaryDto from(SkillDictionaryView view) {
        return new SkillDictionaryDto(view.skillId(), view.name(), view.normalizedName(),
                view.categoryId(), view.categoryName(), view.status(), view.embedded(),
                view.embeddingProvider(), view.embeddingModel(), view.embeddingDimension(), view.embeddings(),
                view.createdAt(), view.updatedAt());
    }
}
