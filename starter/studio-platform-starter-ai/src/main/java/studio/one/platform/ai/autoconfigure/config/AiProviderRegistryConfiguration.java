package studio.one.platform.ai.autoconfigure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.registry.AiProviderRegistry;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiAdapterProperties.class)
public class AiProviderRegistryConfiguration {

    @Bean
    public AiProviderRegistry aiProviderRegistry(AiAdapterProperties properties,
                                                 Map<String, ChatPort> chatPorts,
                                                 Map<String, EmbeddingPort> embeddingPorts) {
        return new AiProviderRegistry(properties.getDefaultProvider(), chatPorts, embeddingPorts);
    }

    @Bean
    public ChatPort defaultChatPort(AiProviderRegistry registry) {
        return registry.chatPort(null);
    }

    @Bean
    public EmbeddingPort defaultEmbeddingPort(AiProviderRegistry registry) {
        return registry.embeddingPort(null);
    }
}
