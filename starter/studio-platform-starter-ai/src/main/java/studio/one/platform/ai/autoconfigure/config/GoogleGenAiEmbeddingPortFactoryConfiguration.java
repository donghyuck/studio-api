package studio.one.platform.ai.autoconfigure.config;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

/**
 * Registers a {@link ProviderEmbeddingPortFactory} bean for the Google GenAI embedding provider.
 * Active only when {@code spring-ai-google-genai-embedding} is on the classpath.
 * Builds the {@code GoogleGenAiTextEmbeddingModel} using {@code spring.ai.google.genai.embedding.*}
 * configuration properties.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel")
public class GoogleGenAiEmbeddingPortFactoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiEmbeddingPortFactoryConfiguration.class);

    @Bean
    public ProviderEmbeddingPortFactory googleGenAiEmbeddingPortFactory() {
        return new GoogleGenAiEmbeddingPortFactory();
    }

    static final class GoogleGenAiEmbeddingPortFactory implements ProviderEmbeddingPortFactory {

        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.GOOGLE_AI_GEMINI;
        }

        @Override
        public EmbeddingPort create(AiAdapterProperties.Provider provider,
                                    Environment env,
                                    ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider) {
            return create("<id>", provider, env, embeddingModelProvider);
        }

        @Override
        public EmbeddingPort create(String providerId,
                                    AiAdapterProperties.Provider provider,
                                    Environment env,
                                    ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider) {
            String model = AiConfigurationMigration.springOrLegacyProviderValue(
                    env,
                    "spring.ai.google.genai.embedding.text.options.model",
                    "studio.ai.providers." + providerId + ".embedding.model",
                    provider.getEmbedding().getModel(),
                    log);
            String apiKey = requireText(
                    AiConfigurationMigration.springOrLegacyProviderValue(
                            env,
                            "spring.ai.google.genai.embedding.api-key",
                            "studio.ai.providers." + providerId + ".api-key",
                            provider.getApiKey(),
                            log),
                    "spring.ai.google.genai.embedding.api-key must be configured for GOOGLE_AI_GEMINI embedding provider");
            model = requireText(model,
                    "spring.ai.google.genai.embedding.text.options.model must be configured for GOOGLE_AI_GEMINI embedding provider");
            Integer dimensions = env.getProperty(
                    "spring.ai.google.genai.embedding.text.options.dimensions",
                    Integer.class);

            org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails connectionDetails =
                    org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails.builder()
                            .apiKey(apiKey)
                            .projectId(env.getProperty("spring.ai.google.genai.embedding.project-id"))
                            .location(env.getProperty("spring.ai.google.genai.embedding.location"))
                            .build();

            AiAdapterProperties.GoogleEmbeddingOptions googleOptions = provider.getGoogleEmbedding();
            org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.Builder optionsBuilder =
                    org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.builder()
                            .model(model)
                            .taskType(parseTaskType(googleOptions.getTaskType()));
            if (dimensions != null) {
                optionsBuilder.dimensions(dimensions);
            }
            org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions options = optionsBuilder.build();

            org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel embeddingModel =
                    new org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel(connectionDetails, options);
            return new SpringAiEmbeddingAdapter(embeddingModel, model);
        }

        private static String requireText(String value, String message) {
            if (!StringUtils.isNotBlank(value)) {
                throw new IllegalStateException(message);
            }
            return value;
        }

        private static org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType parseTaskType(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
        }
    }
}
