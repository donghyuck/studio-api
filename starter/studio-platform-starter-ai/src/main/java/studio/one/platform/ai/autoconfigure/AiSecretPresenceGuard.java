package studio.one.platform.ai.autoconfigure;

import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.autoconfigure.config.AiConfigurationMigration;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;

@AutoConfiguration
@EnableConfigurationProperties(AiAdapterProperties.class)
@Conditional(AiFeatureCondition.class)
@RequiredArgsConstructor
@Slf4j
public class AiSecretPresenceGuard {

    private final AiAdapterProperties properties;
    private final Environment environment;
    private final ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider;
    private final ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider;

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
                case OLLAMA -> validateOllamaProvider(providerId, provider);
                default -> {
                }
            }
        }
    }

    private void validateDefaultProviderSelection() {
        AiConfigurationMigration.RoutingDefaults routing =
                AiConfigurationMigration.resolveRouting(properties, environment, log);
        if (!StringUtils.hasText(routing.defaultProvider())
                && (!StringUtils.hasText(routing.defaultChatProvider())
                || !StringUtils.hasText(routing.defaultEmbeddingProvider()))) {
            throw new IllegalStateException("studio.ai.routing.default-chat-provider and " +
                    "studio.ai.routing.default-embedding-provider must be configured unless legacy " +
                    "studio.ai.default-provider is configured");
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
        if (provider.getChat().isEnabled()) {
            requireText(environment.getProperty("spring.ai.openai.api-key"),
                    "spring.ai.openai.api-key must be configured for OPENAI provider");
            requireText(environment.getProperty("spring.ai.openai.chat.options.model"),
                    "spring.ai.openai.chat.options.model must be configured for OPENAI provider");
        }
        if (provider.getEmbedding().isEnabled()) {
            requireText(environment.getProperty("spring.ai.openai.api-key"),
                    "spring.ai.openai.api-key must be configured for OPENAI provider");
            requireText(environment.getProperty("spring.ai.openai.embedding.options.model"),
                    "spring.ai.openai.embedding.options.model must be configured for OPENAI provider");
        }
    }

    private void validateOllamaProvider(String providerId, AiAdapterProperties.Provider provider) {
        if (provider.getEmbedding().isEnabled() && !hasUnique(embeddingModelProvider)) {
            requireText(AiConfigurationMigration.springOrLegacyProviderValue(
                            environment,
                            "spring.ai.ollama.embedding.options.model",
                            "studio.ai.providers." + providerId + ".embedding.model",
                            provider.getEmbedding().getModel(),
                            log),
                    "spring.ai.ollama.embedding.options.model must be configured for OLLAMA embedding provider");
        }
    }

    private void validateGoogleProvider(String providerId, AiAdapterProperties.Provider provider) {
        if (provider.getEmbedding().isEnabled() && !hasUnique(embeddingModelProvider)) {
            requireText(AiConfigurationMigration.springOrLegacyProviderValue(
                            environment,
                            "spring.ai.google.genai.embedding.api-key",
                            "studio.ai.providers." + providerId + ".api-key",
                            provider.getApiKey(),
                            log),
                    "spring.ai.google.genai.embedding.api-key must be configured for GOOGLE_AI_GEMINI embedding provider");
            requireText(AiConfigurationMigration.springOrLegacyProviderValue(
                            environment,
                            "spring.ai.google.genai.embedding.text.options.model",
                            "studio.ai.providers." + providerId + ".embedding.model",
                            provider.getEmbedding().getModel(),
                            log),
                    "spring.ai.google.genai.embedding.text.options.model must be configured for GOOGLE_AI_GEMINI embedding provider");
        }
        if (provider.getChat().isEnabled() && !hasUnique(chatModelProvider)) {
            requireText(AiConfigurationMigration.springOrLegacyProviderValue(
                            environment,
                            "spring.ai.google.genai.chat.api-key",
                            "studio.ai.providers." + providerId + ".api-key",
                            provider.getApiKey(),
                            log),
                    "spring.ai.google.genai.chat.api-key must be configured for GOOGLE_AI_GEMINI chat provider");
            requireText(AiConfigurationMigration.springOrLegacyProviderValue(
                            environment,
                            "spring.ai.google.genai.chat.options.model",
                            "studio.ai.providers." + providerId + ".chat.model",
                            provider.getChat().getModel(),
                            log),
                    "spring.ai.google.genai.chat.options.model must be configured for GOOGLE_AI_GEMINI chat provider");
        }
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }

    private static boolean hasUnique(ObjectProvider<?> provider) {
        return provider.getIfUnique() != null;
    }
}
