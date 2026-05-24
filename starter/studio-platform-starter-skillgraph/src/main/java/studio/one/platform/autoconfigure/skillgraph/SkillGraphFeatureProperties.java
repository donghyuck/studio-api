package studio.one.platform.autoconfigure.skillgraph;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "studio.features.skillgraph")
public class SkillGraphFeatureProperties {

    private boolean enabled = true;
    private Web web = new Web();

    @Getter
    @Setter
    public static class Web {
        private boolean enabled = false;
        private String extractionBasePath = "/api/mgmt/skillgraph/extraction-jobs";
        private String candidateBasePath = "/api/mgmt/skillgraph/candidates";
        private String dictionaryBasePath = "/api/mgmt/skillgraph/dictionary";
        private String visualizationBasePath = "/api/mgmt/skillgraph/visualization";
        private String taxonomyBasePath = "/api/mgmt/skillgraph/categories";
        private String graphBasePath = "/api/mgmt/skillgraph/relations";
        private String mappingBasePath = "/api/mgmt/skillgraph/mappings";
        private String recommendationBasePath = "/api/mgmt/skillgraph/recommendations";
        private String importJobsBasePath = "/api/mgmt/skillgraph/datasets";
    }
}
