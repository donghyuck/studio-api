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

    EmbeddingPort create(AiAdapterProperties.Provider provider,
                         Environment env,
                         ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider);
}
