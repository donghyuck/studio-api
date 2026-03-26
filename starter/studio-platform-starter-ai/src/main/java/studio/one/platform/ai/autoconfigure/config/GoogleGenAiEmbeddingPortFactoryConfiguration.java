package studio.one.platform.ai.autoconfigure.config;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
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
            String apiKey = requireText(env.getProperty("spring.ai.google.genai.embedding.api-key"),
                    "spring.ai.google.genai.embedding.api-key must be configured for GOOGLE_AI_GEMINI embedding provider");
            String model = requireText(env.getProperty("spring.ai.google.genai.embedding.text.options.model"),
                    "spring.ai.google.genai.embedding.text.options.model must be configured for GOOGLE_AI_GEMINI embedding provider");

            org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails connectionDetails =
                    org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails.builder()
                            .apiKey(apiKey)
                            .projectId(env.getProperty("spring.ai.google.genai.embedding.project-id"))
                            .location(env.getProperty("spring.ai.google.genai.embedding.location"))
                            .build();

            AiAdapterProperties.GoogleEmbeddingOptions googleOptions = provider.getGoogleEmbedding();
            org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions options =
                    org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.builder()
                            .model(model)
                            .taskType(parseTaskType(googleOptions.getTaskType()))
                            .build();

            org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel embeddingModel =
                    new org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel(connectionDetails, options);
            return new SpringAiEmbeddingAdapter(embeddingModel);
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
