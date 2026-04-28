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
import studio.one.platform.ai.core.chat.ChatPort;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiAdapterProperties.class)
@Slf4j
public class ProviderChatConfiguration {

    @Bean(name = "providerChatPorts")
    public Map<String, ChatPort> chatPorts(
            AiAdapterProperties properties,
            Environment environment,
            ObjectProvider<org.springframework.ai.chat.model.ChatModel> springAiChatModelProvider,
            List<ProviderChatPortFactory> factories) {

        Map<AiAdapterProperties.ProviderType, ProviderChatPortFactory> factoryMap = factories.stream()
                .collect(Collectors.toMap(ProviderChatPortFactory::supportedType, f -> f));

        Map<String, ChatPort> ports = new LinkedHashMap<>();
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            AiAdapterProperties.Provider provider = entry.getValue();
            log.debug("checking <{}> : provider - {}, chat - {}",
                    entry.getKey(), provider.isEnabled(), provider.getChat().isEnabled());

            if (!provider.isEnabled() || !provider.getChat().isEnabled()) {
                continue;
            }
            ProviderChatPortFactory factory = factoryMap.get(provider.getType());
            if (factory == null) {
                log.debug("No chat port factory available for provider type {}, skipping <{}>",
                        provider.getType(), entry.getKey());
                continue;
            }
            ports.put(entry.getKey(), factory.create(entry.getKey(), provider, environment, springAiChatModelProvider));
        }
        return ports;
    }
}
