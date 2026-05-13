package studio.one.platform.ai.autoconfigure.config;

import org.springframework.core.env.Environment;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

/**
 * Strategy interface for creating an {@link EmbeddingPort} for a specific provider type.
 */
public interface ProviderEmbeddingPortFactory {

    AiAdapterProperties.ProviderType supportedType();

    EmbeddingPort create(String providerId, AiAdapterProperties.Provider provider, Environment env);
}
