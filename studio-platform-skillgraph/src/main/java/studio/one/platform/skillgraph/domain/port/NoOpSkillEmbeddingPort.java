package studio.one.platform.skillgraph.domain.port;

import java.util.List;

public class NoOpSkillEmbeddingPort implements SkillEmbeddingPort {

    @Override
    public List<Double> embedSkill(String text) {
        return List.of();
    }
}
