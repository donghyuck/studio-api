package studio.one.platform.ai.autoconfigure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import studio.one.platform.ai.core.chat.ChatPort;

/**
 * Strategy interface for creating a {@link ChatPort} for a specific provider type.
 * Implementations are registered conditionally based on provider library presence
 * ({@code @ConditionalOnClass}) and collected at runtime to assemble the provider port map.
 */
public interface ProviderChatPortFactory {

    AiAdapterProperties.ProviderType supportedType();

    default ChatPort create(String providerId,
                    AiAdapterProperties.Provider provider,
                    Environment env,
                    ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider) {
        return create(provider, env, chatModelProvider);
    }

    /**
     * Creates a port for an explicit deployment. Provider properties describe the
     * connection; the catalog-resolved model is supplied separately.
     */
    default ChatPort createForDeployment(
                    String providerId,
                    AiAdapterProperties.Provider provider,
                    String apiModel,
                    Environment env,
                    ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider) {
        String configuredModel = provider.getChat().getModel();
        if (configuredModel != null && !configuredModel.isBlank()
                && configuredModel.trim().equals(apiModel)) {
            return create(providerId, provider, env, chatModelProvider);
        }
        throw new IllegalStateException("Provider chat factory " + supportedType()
                + " does not support deployment model override: " + apiModel);
    }

    ChatPort create(AiAdapterProperties.Provider provider,
                    Environment env,
                    ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider);
}
