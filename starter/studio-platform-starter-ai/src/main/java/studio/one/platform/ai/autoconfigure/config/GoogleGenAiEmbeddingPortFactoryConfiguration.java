package studio.one.platform.ai.autoconfigure.config;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import studio.one.platform.ai.autoconfigure.adapter.GoogleGenAiEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.model.embedding.EmbeddingSpaceContract;

/**
 * Registers a {@link ProviderEmbeddingPortFactory} bean for the Google GenAI embedding provider.
 * Active only when {@code spring-ai-google-genai-embedding} is on the classpath.
 * Uses Spring AI connection details and the Google GenAI SDK request contract.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails")
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
            String model = provider.getEmbedding().isModelOverride()
                    ? provider.getEmbedding().getModel()
                    : AiConfigurationMigration.springOrLegacyProviderValue(
                            env,
                            "spring.ai.google.genai.embedding.text.options.model",
                            "studio.ai.providers." + providerId + ".embedding.model",
                            provider.getEmbedding().getModel(),
                            log);
            Integer dimensions = provider.getEmbedding().isModelOverride()
                    && provider.getEmbedding().getDimension() != null
                            ? provider.getEmbedding().getDimension()
                            : env.getProperty("spring.ai.google.genai.embedding.text.options.dimensions", Integer.class);
            return createForDeployment(providerId, provider, model, dimensions, env, embeddingModelProvider);
        }

        @Override
        public EmbeddingPort createForDeployment(
                                    String providerId,
                                    AiAdapterProperties.Provider provider,
                                    String apiModel,
                                    Integer dimension,
                                    Environment env,
                                    ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider) {
            return createForDeployment(
                    providerId, provider, apiModel, dimension, null, env, embeddingModelProvider);
        }

        @Override
        public EmbeddingPort createForDeployment(
                                    String providerId,
                                    AiAdapterProperties.Provider provider,
                                    String apiModel,
                                    Integer dimension,
                                    EmbeddingSpaceContract embeddingContract,
                                    Environment env,
                                    ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider) {
            String model = requireText(apiModel,
                    "Catalog-resolved model must be configured for GOOGLE_AI_GEMINI embedding deployment");
            String apiKey = requireText(
                    AiConfigurationMigration.springOrLegacyProviderValue(
                            env,
                            "spring.ai.google.genai.embedding.api-key",
                            "studio.ai.providers." + providerId + ".api-key",
                            provider.getApiKey(),
                            log),
                    "spring.ai.google.genai.embedding.api-key must be configured for GOOGLE_AI_GEMINI embedding provider");
            Integer dimensions = dimension;

            org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails connectionDetails =
                    org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails.builder()
                            .apiKey(apiKey)
                            .projectId(env.getProperty("spring.ai.google.genai.embedding.project-id"))
                            .location(env.getProperty("spring.ai.google.genai.embedding.location"))
                            .build();

            boolean taskTypeSupported = !"gemini-embedding-2".equalsIgnoreCase(model);
            String indexTaskType = embeddingContract == null ? null : embeddingContract.indexTaskType();
            String queryTaskType = embeddingContract == null ? null : embeddingContract.queryTaskType();
            if (!taskTypeSupported
                    && (isExplicitTaskType(indexTaskType) || isExplicitTaskType(queryTaskType))) {
                throw new IllegalStateException(
                        "gemini-embedding-2 does not support index-task-type or query-task-type");
            }
            return new GoogleGenAiEmbeddingAdapter(
                    connectionDetails,
                    model,
                    dimensions,
                    indexTaskType,
                    queryTaskType,
                    provider.getGoogleEmbedding().getTaskType(),
                    taskTypeSupported);
        }

        private static String requireText(String value, String message) {
            if (!StringUtils.isNotBlank(value)) {
                throw new IllegalStateException(message);
            }
            return value;
        }

        private static boolean isExplicitTaskType(String value) {
            return value != null && !value.isBlank() && !"provider-default".equalsIgnoreCase(value.trim());
        }
    }
}
