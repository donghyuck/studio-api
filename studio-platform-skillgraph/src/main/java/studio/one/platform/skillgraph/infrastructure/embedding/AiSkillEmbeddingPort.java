package studio.one.platform.skillgraph.infrastructure.embedding;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;

@RequiredArgsConstructor
public class AiSkillEmbeddingPort implements SkillEmbeddingPort {

    private final EmbeddingPort embeddingPort;
    private final AiProviderRegistry providerRegistry;

    public AiSkillEmbeddingPort(EmbeddingPort embeddingPort) {
        this(embeddingPort, null);
    }

    @Override
    public List<Double> embedSkill(String text) {
        return embedSkill(text, null, null);
    }

    @Override
    public List<Double> embedSkill(String text, String provider, String model) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<List<Double>> vectors = embedSkills(List.of(text), provider, model);
        if (vectors.isEmpty()) {
            return List.of();
        }
        return vectors.get(0);
    }

    @Override
    public List<List<Double>> embedSkills(List<String> texts, String provider, String model) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<String> inputs = new ArrayList<>();
        List<Integer> inputIndexes = new ArrayList<>();
        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            if (text != null && !text.isBlank()) {
                inputs.add(text);
                inputIndexes.add(index);
            }
        }
        if (inputs.isEmpty()) {
            return List.of();
        }
        EmbeddingPort port = providerRegistry == null
                ? embeddingPort
                : providerRegistry.embeddingPort(provider);
        EmbeddingResponse response = port.embed(new EmbeddingRequest(
                inputs,
                provider,
                model,
                null,
                null));
        List<List<Double>> result = new ArrayList<>(java.util.Collections.nCopies(texts.size(), List.of()));
        for (int index = 0; index < Math.min(inputIndexes.size(), response.vectors().size()); index++) {
            result.set(inputIndexes.get(index), response.vectors().get(index).values());
        }
        return result;
    }
}
