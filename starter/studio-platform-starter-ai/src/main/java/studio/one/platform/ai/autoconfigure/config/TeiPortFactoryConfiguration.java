package studio.one.platform.ai.autoconfigure.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import studio.one.platform.ai.autoconfigure.adapter.TeiEmbeddingAdapter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

/**
 * Registers a provider factory for Hugging Face Text Embeddings Inference servers.
 */
@Configuration(proxyBeanMethods = false)
public class TeiPortFactoryConfiguration {

    @Bean
    public ProviderEmbeddingPortFactory teiEmbeddingPortFactory() {
        return new TeiEmbeddingPortFactory();
    }

    static final class TeiEmbeddingPortFactory implements ProviderEmbeddingPortFactory {

        @Override
        public AiAdapterProperties.ProviderType supportedType() {
            return AiAdapterProperties.ProviderType.TEI;
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
            String baseUrl = provider.getBaseUrl();
            baseUrl = requireText(baseUrl, "studio.ai.providers." + providerId
                    + ".base-url must be configured for TEI embedding provider");
            String model = provider.getEmbedding().getModel();
            return new TeiEmbeddingAdapter(baseUrl, model);
        }

        private static String requireText(String value, String message) {
            if (!StringUtils.isNotBlank(value)) {
                throw new IllegalStateException(message);
            }
            return value;
        }
    }
}
