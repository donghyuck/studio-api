package studio.one.platform.ai.autoconfigure;

import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.autoconfigure.config.AiConfigurationMigration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiAdapterProperties.class)
@Conditional(AiFeatureCondition.class)
@RequiredArgsConstructor
@Slf4j
public class AiSecretPresenceGuard {

    private final AiAdapterProperties properties;
    private final Environment environment;

    @PostConstruct
    void validate() {
        validateDefaultProviderSelection();
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            String providerId = entry.getKey();
            AiAdapterProperties.Provider provider = entry.getValue();
            if (provider == null || !provider.isEnabled() || provider.getType() == null) {
                continue;
            }
            switch (provider.getType()) {
                case OPENAI:
                    validateOpenAiProvider(providerId, provider);
                    break;
                case GOOGLE_AI_GEMINI:
                    validateGoogleProvider(providerId, provider);
                    break;
                case OLLAMA:
                    validateOllamaProvider(providerId, provider);
                    break;
                default:
                    break;
            }
        }
    }

    private void validateDefaultProviderSelection() {
        AiConfigurationMigration.RoutingDefaults routing =
                AiConfigurationMigration.resolveRouting(properties, environment, log);
        if (!routing.legacyDefaultProviderConfigured()
                && (!StringUtils.hasText(routing.defaultChatProvider())
                || !StringUtils.hasText(routing.defaultEmbeddingProvider()))) {
            throw new IllegalStateException("studio.ai.routing.default-chat-provider and "
                    + "studio.ai.routing.default-embedding-provider must be configured unless legacy "
                    + "studio.ai.default-provider is configured");
        }
    }

    private void validateOpenAiProvider(String providerId, AiAdapterProperties.Provider provider) {
        if (provider.getChat().isEnabled()) {
            requireProviderValue(providerId, "api-key", provider.getApiKey(), "spring.ai.openai.api-key",
                    "studio.ai.providers." + providerId + ".api-key must be configured for OPENAI chat provider");
            requireProviderValue(providerId, "chat.model", provider.getChat().getModel(),
                    "spring.ai.openai.chat.options.model",
                    "studio.ai.providers." + providerId + ".chat.model must be configured for OPENAI chat provider");
        }
        if (provider.getEmbedding().isEnabled()) {
            requireProviderValue(providerId, "api-key", provider.getApiKey(), "spring.ai.openai.api-key",
                    "studio.ai.providers." + providerId + ".api-key must be configured for OPENAI embedding provider");
            requireProviderValue(providerId, "embedding.model", provider.getEmbedding().getModel(),
                    "spring.ai.openai.embedding.options.model",
                    "studio.ai.providers." + providerId + ".embedding.model must be configured for OPENAI embedding provider");
        }
    }

    private void validateOllamaProvider(String providerId, AiAdapterProperties.Provider provider) {
        if (provider.getChat().isEnabled()) {
            requireProviderValue(providerId, "chat.model", provider.getChat().getModel(),
                    "spring.ai.ollama.chat.options.model",
                    "studio.ai.providers." + providerId + ".chat.model must be configured for OLLAMA chat provider");
        }
        if (provider.getEmbedding().isEnabled()) {
            requireProviderValue(providerId, "embedding.model", provider.getEmbedding().getModel(),
                    "spring.ai.ollama.embedding.options.model",
                    "studio.ai.providers." + providerId + ".embedding.model must be configured for OLLAMA embedding provider");
        }
    }

    private void validateGoogleProvider(String providerId, AiAdapterProperties.Provider provider) {
        if (provider.getChat().isEnabled()) {
            requireProviderValue(providerId, "api-key", provider.getApiKey(),
                    "spring.ai.google.genai.chat.api-key",
                    "studio.ai.providers." + providerId + ".api-key must be configured for GOOGLE_AI_GEMINI chat provider");
            requireProviderValue(providerId, "chat.model", provider.getChat().getModel(),
                    "spring.ai.google.genai.chat.options.model",
                    "studio.ai.providers." + providerId + ".chat.model must be configured for GOOGLE_AI_GEMINI chat provider");
        }
        if (provider.getEmbedding().isEnabled()) {
            requireProviderValue(providerId, "api-key", provider.getApiKey(),
                    "spring.ai.google.genai.embedding.api-key",
                    "studio.ai.providers." + providerId + ".api-key must be configured for GOOGLE_AI_GEMINI embedding provider");
            requireProviderValue(providerId, "embedding.model", provider.getEmbedding().getModel(),
                    "spring.ai.google.genai.embedding.text.options.model",
                    "studio.ai.providers." + providerId + ".embedding.model must be configured for GOOGLE_AI_GEMINI embedding provider");
        }
    }

    private void requireProviderValue(
            String providerId,
            String studioLeaf,
            String studioValue,
            String springKey,
            String message) {
        String value = AiConfigurationMigration.studioOrSpringProviderValue(
                environment,
                "studio.ai.providers." + providerId + "." + studioLeaf,
                studioValue,
                springKey,
                log);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }
}
