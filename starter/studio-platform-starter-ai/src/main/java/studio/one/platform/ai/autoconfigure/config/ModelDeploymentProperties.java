package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.ai.model.ModelWorkload;

@ConfigurationProperties(prefix = "studio.ai")
@Getter
public final class ModelDeploymentProperties {

    private final Map<String, Deployment> modelDeployments = new LinkedHashMap<>();
    private final Routing routing = new Routing();

    @Getter
    @Setter
    public static final class Deployment {
        private String providerRef;
        private String modelRef;
        private ModelWorkload workload;
        private Integer dimension;
        private boolean enabled = true;
        private String normalizationPolicy = "provider-default";
        private String indexTaskType = "provider-default";
        private String queryTaskType = "provider-default";
        private String inputTransformId = "text";
        private String inputTransformVersion = "1";
        private Map<String, String> semanticOptions = Map.of();
    }

    @Getter
    @Setter
    public static final class Routing {
        private String defaultChatDeployment;
        private String defaultEmbeddingDeployment;
    }
}
