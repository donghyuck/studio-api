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

    ChatPort create(AiAdapterProperties.Provider provider,
                    Environment env,
                    ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModelProvider);
}
