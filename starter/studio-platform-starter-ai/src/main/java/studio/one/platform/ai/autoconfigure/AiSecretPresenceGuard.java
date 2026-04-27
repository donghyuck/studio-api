package studio.one.platform.ai.autoconfigure;

import java.util.Map;

import jakarta.annotation.PostConstruct;

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
                case GOOGLE_AI_GEMINI -> validateGoogleProvider(providerId, provider);
                case OLLAMA -> validateOllamaProvider(provider);
                default -> {
                }
            }
        }
    }

    private void validateDefaultProviderSelection() {
        if (!StringUtils.hasText(properties.getDefaultProvider())
                && (!StringUtils.hasText(properties.getDefaultChatProvider())
                || !StringUtils.hasText(properties.getDefaultEmbeddingProvider()))) {
            throw new IllegalStateException("studio.ai.default-provider must be configured unless both " +
                    "studio.ai.default-chat-provider and studio.ai.default-embedding-provider are configured");
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
        if (provider.getChat().isEnabled()) {
            requireText(environment.getProperty("spring.ai.openai.chat.options.model"),
                    "spring.ai.openai.chat.options.model must be configured for OPENAI provider");
        }
        if (provider.getEmbedding().isEnabled()) {
            requireText(environment.getProperty("spring.ai.openai.embedding.options.model"),
                    "spring.ai.openai.embedding.options.model must be configured for OPENAI provider");
        }
    }

    private void validateOllamaProvider(AiAdapterProperties.Provider provider) {
        if (provider.getEmbedding().isEnabled()) {
            requireText(environment.getProperty("spring.ai.ollama.embedding.options.model"),
                    "spring.ai.ollama.embedding.options.model must be configured for OLLAMA embedding provider");
        }
    }

    private void validateGoogleProvider(String providerId, AiAdapterProperties.Provider provider) {
        if (provider.getEmbedding().isEnabled()) {
            requireText(environment.getProperty("spring.ai.google.genai.embedding.api-key"),
                    "spring.ai.google.genai.embedding.api-key must be configured for GOOGLE_AI_GEMINI embedding provider");
            requireText(environment.getProperty("spring.ai.google.genai.embedding.text.options.model"),
                    "spring.ai.google.genai.embedding.text.options.model must be configured for GOOGLE_AI_GEMINI embedding provider");
        }
        if (provider.getChat().isEnabled()) {
            requireText(firstNonBlank(environment.getProperty("spring.ai.google.genai.chat.api-key"), provider.getApiKey()),
                    "spring.ai.google.genai.chat.api-key must be configured for GOOGLE_AI_GEMINI chat provider");
            requireText(firstNonBlank(
                            environment.getProperty("spring.ai.google.genai.chat.options.model"),
                            provider.getChat().getModel()),
                    "spring.ai.google.genai.chat.options.model must be configured for GOOGLE_AI_GEMINI chat provider");
        }
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }
}
