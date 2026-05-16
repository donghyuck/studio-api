package studio.one.platform.skillgraph.infrastructure.embedding;

import java.util.List;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;

@RequiredArgsConstructor
public class AiSkillEmbeddingPort implements SkillEmbeddingPort {

    private final EmbeddingPort embeddingPort;

    @Override
    public List<Double> embedSkill(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        EmbeddingResponse response = embeddingPort.embedAll(List.of(text));
        if (response.vectors().isEmpty()) {
            return List.of();
        }
        return response.vectors().get(0).values();
    }
}
