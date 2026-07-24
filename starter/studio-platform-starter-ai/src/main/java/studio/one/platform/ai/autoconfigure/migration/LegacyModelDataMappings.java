package studio.one.platform.ai.autoconfigure.migration;

import java.util.ArrayList;
import java.util.List;

import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.ai.model.embedding.EmbeddingSpaceId;
import studio.one.platform.ai.model.migration.LegacyModelDataMapping;

public final class LegacyModelDataMappings {

    private LegacyModelDataMappings() {
    }

    public static List<LegacyModelDataMapping> fromRegistry(ModelDeploymentRegistry registry) {
        List<LegacyModelDataMapping> mappings = new ArrayList<>();
        for (ModelDeployment deployment : registry.deployments(ModelWorkload.EMBEDDING)) {
            if (deployment.dimension() == null) {
                continue;
            }
            String suffix = "@" + deployment.dimension();
            deployment.definition().aliases().stream()
                    .filter(alias -> alias.endsWith(suffix))
                    .forEach(alias -> mappings.add(mapping(deployment, alias)));
        }
        return List.copyOf(mappings);
    }

    private static LegacyModelDataMapping mapping(ModelDeployment deployment, String legacySpaceId) {
        return new LegacyModelDataMapping(
                legacySpaceId,
                deployment.definition().apiModel(),
                deployment.dimension(),
                deployment.deploymentId(),
                deployment.definition().catalogId(),
                EmbeddingSpaceId.from(deployment.embeddingContract()));
    }
}
