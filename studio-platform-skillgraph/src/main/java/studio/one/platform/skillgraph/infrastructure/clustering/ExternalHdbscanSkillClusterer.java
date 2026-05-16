package studio.one.platform.skillgraph.infrastructure.clustering;

import java.util.List;

import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.port.SkillClusterer;

public interface ExternalHdbscanSkillClusterer extends SkillClusterer {

    @Override
    SkillClusteringResult cluster(String projectionId, List<SkillProjection> projections);
}
