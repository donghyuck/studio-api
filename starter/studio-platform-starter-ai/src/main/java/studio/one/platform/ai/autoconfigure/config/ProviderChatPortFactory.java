package studio.one.platform.ai.autoconfigure.config;

import org.springframework.core.env.Environment;
import studio.one.platform.ai.core.chat.ChatPort;

/**
 * Strategy interface for creating a {@link ChatPort} for a specific provider type.
 */
public interface ProviderChatPortFactory {

    AiAdapterProperties.ProviderType supportedType();

    ChatPort create(String providerId, AiAdapterProperties.Provider provider, Environment env);
}
