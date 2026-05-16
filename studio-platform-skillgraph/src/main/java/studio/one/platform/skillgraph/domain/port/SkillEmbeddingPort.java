package studio.one.platform.skillgraph.domain.port;

import java.util.List;

public interface SkillEmbeddingPort {

    List<Double> embedSkill(String text);
}
