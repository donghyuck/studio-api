package studio.one.platform.ai.autoconfigure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

/**
 * Strategy interface for creating an {@link EmbeddingPort} for a specific provider type.
 * Implementations are registered conditionally based on provider library presence
 * ({@code @ConditionalOnClass}) and collected at runtime to assemble the provider port map.
 */
public interface ProviderEmbeddingPortFactory {

    AiAdapterProperties.ProviderType supportedType();

    default EmbeddingPort create(String providerId,
                         AiAdapterProperties.Provider provider,
                         Environment env,
                         ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider) {
        return create(provider, env, embeddingModelProvider);
    }

    /**
     * Creates a port for an explicit deployment. Provider properties describe the
     * connection; the catalog-resolved model and dimension are supplied separately.
     */
    default EmbeddingPort createForDeployment(
                         String providerId,
                         AiAdapterProperties.Provider provider,
                         String apiModel,
                         Integer dimension,
                         Environment env,
                         ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider) {
        String configuredModel = provider.getEmbedding().getModel();
        Integer configuredDimension = provider.getEmbedding().getDimension();
        boolean sameModel = configuredModel != null && !configuredModel.isBlank()
                && configuredModel.trim().equals(apiModel);
        boolean sameDimension = configuredDimension == null || configuredDimension.equals(dimension);
        if (sameModel && sameDimension) {
            return create(providerId, provider, env, embeddingModelProvider);
        }
        throw new IllegalStateException("Provider embedding factory " + supportedType()
                + " does not support deployment model override: " + apiModel + "@" + dimension);
    }

    EmbeddingPort create(AiAdapterProperties.Provider provider,
                         Environment env,
                         ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider);
}
