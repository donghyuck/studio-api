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
        validateDefaultProviderSelection();
        validateOpenAiProviderMultiplicity();
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            String providerId = entry.getKey();
            AiAdapterProperties.Provider provider = entry.getValue();
            if (provider == null || !provider.isEnabled() || provider.getType() == null) {
                continue;
            }
            switch (provider.getType()) {
                case OPENAI -> validateOpenAiProvider(provider);
                case GOOGLE_AI_GEMINI -> requireText(provider.getApiKey(),
                        "studio.ai.providers." + providerId + ".api-key must be configured");
                case OLLAMA -> requireText(provider.getBaseUrl(),
                        "studio.ai.providers." + providerId + ".base-url must be configured");
                default -> {
                }
            }
        }
    }

    private void validateDefaultProviderSelection() {
        if (!StringUtils.hasText(properties.getDefaultProvider())) {
            throw new IllegalStateException("studio.ai.default-provider must be configured");
        }
    }

    private void validateOpenAiProviderMultiplicity() {
        long enabledOpenAiProviders = properties.getProviders().values().stream()
                .filter(provider -> provider != null
                        && provider.isEnabled()
                        && provider.getType() == AiAdapterProperties.ProviderType.OPENAI)
                .count();
        if (enabledOpenAiProviders > 1) {
            throw new IllegalStateException("Exactly one enabled OPENAI provider is supported");
        }
    }

    private void validateOpenAiProvider(AiAdapterProperties.Provider provider) {
        requireText(environment.getProperty("spring.ai.openai.api-key"),
                "spring.ai.openai.api-key must be configured for OPENAI provider");
        boolean chatEnabled = provider.getChat().isEnabled();
        boolean embeddingEnabled = provider.getEmbedding().isEnabled();
        if (chatEnabled) {
            requireText(environment.getProperty("spring.ai.openai.chat.options.model"),
                    "spring.ai.openai.chat.options.model must be configured for OPENAI provider");
        }
        if (embeddingEnabled) {
            requireText(environment.getProperty("spring.ai.openai.embedding.options.model"),
                    "spring.ai.openai.embedding.options.model must be configured for OPENAI provider");
        }
        if (chatEnabled && springAiChatModelProvider.getIfAvailable() == null) {
            throw new IllegalStateException("Spring AI chat model bean is required for OPENAI provider");
        }
        if (embeddingEnabled && springAiEmbeddingModelProvider.getIfAvailable() == null) {
            throw new IllegalStateException("Spring AI embedding model bean is required for OPENAI provider");
        }
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }
}
