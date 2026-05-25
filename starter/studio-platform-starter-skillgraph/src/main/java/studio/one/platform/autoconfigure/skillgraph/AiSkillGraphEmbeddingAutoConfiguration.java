package studio.one.platform.autoconfigure.skillgraph;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;
import studio.one.platform.skillgraph.infrastructure.embedding.AiSkillEmbeddingPort;

@AutoConfiguration(before = SkillGraphAutoConfiguration.class)
@ConditionalOnClass(EmbeddingPort.class)
@ConditionalOnProperty(prefix = "studio.features.skillgraph", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiSkillGraphEmbeddingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({EmbeddingPort.class, AiProviderRegistry.class})
    @ConditionalOnProperty(prefix = "studio.skillgraph.matching", name = "remote-embedding-enabled", havingValue = "true")
    public SkillEmbeddingPort skillEmbeddingPort(
            EmbeddingPort embeddingPort,
            AiProviderRegistry providerRegistry) {
        return new AiSkillEmbeddingPort(embeddingPort, providerRegistry);
    }
}
