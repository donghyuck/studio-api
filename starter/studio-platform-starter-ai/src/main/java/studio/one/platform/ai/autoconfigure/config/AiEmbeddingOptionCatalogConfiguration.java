package studio.one.platform.ai.autoconfigure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.model.ModelDeploymentRegistry;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ AiAdapterProperties.class, RagEmbeddingProperties.class })
public class AiEmbeddingOptionCatalogConfiguration {

    @Bean
    @ConditionalOnBean(AiProviderRegistry.class)
    @ConditionalOnMissingBean(AiEmbeddingOptionCatalog.class)
    AiEmbeddingOptionCatalog aiEmbeddingOptionCatalog(
            AiProviderRegistry registry,
            ModelDeploymentRegistry deploymentRegistry,
            AiAdapterProperties aiProperties,
            RagEmbeddingProperties ragProperties,
            Environment environment) {
        return new DefaultAiEmbeddingOptionCatalog(
                registry, deploymentRegistry, aiProperties, ragProperties, environment);
    }
}
