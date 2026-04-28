package studio.one.platform.ai.autoconfigure.config;

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
 * Registers a {@link ProviderEmbeddingPortFactory} bean for the Ollama provider.
 * Active only when {@code spring-ai-ollama} is on the classpath.
 * Builds the {@code OllamaEmbeddingModel} directly using {@code spring.ai.ollama.*}
 * configuration properties.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.ai.ollama.OllamaEmbeddingModel")
public class OllamaPortFactoryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OllamaPortFactoryConfiguration.class);

    @Bean
    public ProviderEmbeddingPortFactory ollamaEmbeddingPortFactory() {
        return new OllamaEmbeddingPortFactory();
    }

    static final class OllamaEmbeddingPortFactory implements ProviderEmbeddingPortFactory {

        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.OLLAMA;
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
                    "spring.ai.ollama.embedding.options.model",
                    "studio.ai.providers." + providerId + ".embedding.model",
                    provider.getEmbedding().getModel(),
                    log);
            org.springframework.ai.embedding.EmbeddingModel injectedEmbeddingModel =
                    embeddingModelProvider.getIfUnique();
            if (injectedEmbeddingModel != null) {
                return new SpringAiEmbeddingAdapter(injectedEmbeddingModel, model);
            }

            model = requireText(model,
                    "spring.ai.ollama.embedding.options.model must be configured for OLLAMA embedding provider");
            String baseUrl = AiConfigurationMigration.springOrLegacyProviderValue(
                    env,
                    "spring.ai.ollama.base-url",
                    "studio.ai.providers." + providerId + ".base-url",
                    provider.getBaseUrl(),
                    log);
            if (!StringUtils.isNotBlank(baseUrl)) {
                baseUrl = "http://localhost:11434";
            }

            org.springframework.ai.ollama.api.OllamaApi ollamaApi =
                    org.springframework.ai.ollama.api.OllamaApi.builder()
                            .baseUrl(baseUrl)
                            .build();
            org.springframework.ai.ollama.api.OllamaEmbeddingOptions options =
                    org.springframework.ai.ollama.api.OllamaEmbeddingOptions.builder()
                            .model(model)
                            .build();
            org.springframework.ai.ollama.OllamaEmbeddingModel embeddingModel =
                    org.springframework.ai.ollama.OllamaEmbeddingModel.builder()
                            .ollamaApi(ollamaApi)
                            .defaultOptions(options)
                            .build();
            return new SpringAiEmbeddingAdapter(embeddingModel, model);
        }

        private static String requireText(String value, String message) {
            if (!StringUtils.isNotBlank(value)) {
                throw new IllegalStateException(message);
            }
            return value;
        }
    }
}
