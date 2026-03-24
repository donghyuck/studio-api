package studio.one.platform.ai.autoconfigure;

import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;

@AutoConfiguration
@EnableConfigurationProperties(AiAdapterProperties.class)
@ConditionalOnProperty(prefix = "studio.ai", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AiSecretPresenceGuard {

    private final AiAdapterProperties properties;
    private final Environment environment;
    private final ObjectProvider<org.springframework.ai.chat.model.ChatModel> springAiChatModelProvider;
    private final ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> springAiEmbeddingModelProvider;

    @PostConstruct
    void validate() {
        String springAiSourceProvider = properties.getSpringAi().isEnabled() ? resolveSpringAiSourceProvider() : null;
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            String providerId = entry.getKey();
            AiAdapterProperties.Provider provider = entry.getValue();
            if (provider == null || !provider.isEnabled() || provider.getType() == null) {
                continue;
            }
            switch (provider.getType()) {
                case OPENAI -> {
                    if (!providerId.equalsIgnoreCase(springAiSourceProvider)) {
                        requireText(provider.getApiKey(),
                                "studio.ai.providers." + providerId + ".api-key must be configured");
                    }
                }
                case GOOGLE_AI_GEMINI -> requireText(provider.getApiKey(),
                        "studio.ai.providers." + providerId + ".api-key must be configured");
                case OLLAMA -> requireText(provider.getBaseUrl(),
                        "studio.ai.providers." + providerId + ".base-url must be configured");
                default -> {
                }
            }
        }
        validateSpringAiAliasSettings();
    }

    private void validateSpringAiAliasSettings() {
        if (!properties.getSpringAi().isEnabled()) {
            return;
        }
        String providerId = resolveSpringAiSourceProvider();
        AiAdapterProperties.Provider provider = properties.getProviders().get(providerId);
        if (provider == null) {
            throw new IllegalStateException("studio.ai.spring-ai.source-provider must reference an existing provider: " + providerId);
        }
        if (provider.getType() != AiAdapterProperties.ProviderType.OPENAI) {
            throw new IllegalStateException("studio.ai.spring-ai.source-provider must reference an OPENAI provider: " + providerId);
        }
        requireText(environment.getProperty("spring.ai.openai.api-key"),
                "spring.ai.openai.api-key must be configured when studio.ai.spring-ai.enabled=true");
        boolean chatEnabled = provider.getChat().isEnabled();
        boolean embeddingEnabled = provider.getEmbedding().isEnabled();
        if (chatEnabled) {
            requireText(environment.getProperty("spring.ai.openai.chat.options.model"),
                    "spring.ai.openai.chat.options.model must be configured when studio.ai.spring-ai.enabled=true");
        }
        if (embeddingEnabled) {
            requireText(environment.getProperty("spring.ai.openai.embedding.options.model"),
                    "spring.ai.openai.embedding.options.model must be configured when studio.ai.spring-ai.enabled=true");
        }
        if (chatEnabled && springAiChatModelProvider.getIfAvailable() == null) {
            throw new IllegalStateException("Spring AI chat model bean is required when studio.ai.spring-ai.enabled=true");
        }
        if (embeddingEnabled && springAiEmbeddingModelProvider.getIfAvailable() == null) {
            throw new IllegalStateException("Spring AI embedding model bean is required when studio.ai.spring-ai.enabled=true");
        }
    }

    private String resolveSpringAiSourceProvider() {
        if (!StringUtils.hasText(properties.getSpringAi().getSourceProvider())) {
            throw new IllegalStateException("studio.ai.spring-ai.source-provider must be configured when studio.ai.spring-ai.enabled=true");
        }
        return properties.getSpringAi().getSourceProvider().toLowerCase();
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }
}
