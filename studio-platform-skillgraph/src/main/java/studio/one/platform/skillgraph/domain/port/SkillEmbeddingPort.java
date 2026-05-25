package studio.one.platform.skillgraph.domain.port;

import java.util.List;

public interface SkillEmbeddingPort {

    List<Double> embedSkill(String text);

    default List<Double> embedSkill(String text, String provider, String model) {
        return embedSkill(text);
    }

    default List<List<Double>> embedSkills(List<String> texts, String provider, String model) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return texts.stream()
                .map(text -> embedSkill(text, provider, model))
                .toList();
    }
}
