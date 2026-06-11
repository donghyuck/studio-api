package studio.one.platform.ai.autoconfigure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import studio.one.platform.ai.core.registry.AiProviderRegistry;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ AiAdapterProperties.class, RagEmbeddingProperties.class })
public class AiEmbeddingOptionCatalogConfiguration {

    @Bean
    @ConditionalOnBean(AiProviderRegistry.class)
    @ConditionalOnMissingBean(AiEmbeddingOptionCatalog.class)
    AiEmbeddingOptionCatalog aiEmbeddingOptionCatalog(AiProviderRegistry registry,
            AiAdapterProperties aiProperties,
            RagEmbeddingProperties ragProperties,
            Environment environment) {
        return new DefaultAiEmbeddingOptionCatalog(registry, aiProperties, ragProperties, environment);
    }
}
