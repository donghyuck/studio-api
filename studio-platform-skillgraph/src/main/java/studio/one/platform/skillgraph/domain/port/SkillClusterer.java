package studio.one.platform.skillgraph.domain.port;

import java.util.List;

import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillProjection;

public interface SkillClusterer {

    SkillClusteringResult cluster(String projectionId, List<SkillProjection> projections);

    record SkillClusteringResult(List<SkillProjection> projections, List<SkillCluster> clusters) {
        public SkillClusteringResult {
            projections = projections == null ? List.of() : List.copyOf(projections);
            clusters = clusters == null ? List.of() : List.copyOf(clusters);
        }
    }
}
