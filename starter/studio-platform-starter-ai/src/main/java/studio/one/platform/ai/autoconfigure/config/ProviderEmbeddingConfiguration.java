package studio.one.platform.ai.autoconfigure.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiAdapterProperties.class)
@Slf4j
public class ProviderEmbeddingConfiguration {

    @Bean(name = "providerEmbeddingPorts")
    public Map<String, EmbeddingPort> embeddingPorts(
            AiAdapterProperties properties,
            Environment environment,
            ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider,
            List<ProviderEmbeddingPortFactory> factories) {

        Map<AiAdapterProperties.ProviderType, ProviderEmbeddingPortFactory> factoryMap = factories.stream()
                .collect(Collectors.toMap(ProviderEmbeddingPortFactory::supportedType, f -> f));

        Map<String, EmbeddingPort> ports = new LinkedHashMap<>();
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            AiAdapterProperties.Provider provider = entry.getValue();
            log.debug("checking <{}> : provider - {}, embedding - {}",
                    entry.getKey(), provider.isEnabled(), provider.getEmbedding().isEnabled());

            if (!provider.isEnabled() || !provider.getEmbedding().isEnabled()) {
                continue;
            }
            ProviderEmbeddingPortFactory factory = factoryMap.get(provider.getType());
            if (factory == null) {
                log.debug("No embedding port factory available for provider type {}, skipping <{}>",
                        provider.getType(), entry.getKey());
                continue;
            }
            ports.put(entry.getKey(), factory.create(provider, environment, springAiEmbeddingModelProvider));
        }
        return ports;
    }
}
