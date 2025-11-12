package studio.one.platform.ai.core.registry;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Holds the available chat/embedding ports for each configured provider.
 */
public final class AiProviderRegistry {

    private final Map<String, ChatPort> chatPorts;
    private final Map<String, EmbeddingPort> embeddingPorts;
    private final String defaultProvider;

    public AiProviderRegistry(String defaultProvider,
                              Map<String, ChatPort> chatPorts,
                              Map<String, EmbeddingPort> embeddingPorts) {
        this.defaultProvider = defaultProvider.toLowerCase(Locale.ROOT);
        this.chatPorts = Collections.unmodifiableMap(new LinkedHashMap<>(chatPorts));
        this.embeddingPorts = Collections.unmodifiableMap(new LinkedHashMap<>(embeddingPorts));
    }

    public Map<String, ChatPort> availableChatPorts() {
        return chatPorts;
    }

    public Map<String, EmbeddingPort> availableEmbeddingPorts() {
        return embeddingPorts;
    }

    public ChatPort chatPort(String provider) {
        return lookup(chatPorts, provider);
    }

    public EmbeddingPort embeddingPort(String provider) {
        return lookup(embeddingPorts, provider);
    }

    public String defaultProvider() {
        return defaultProvider;
    }

    private <T> T lookup(Map<String, T> ports, String provider) {
        String key = provider == null ? defaultProvider : provider.toLowerCase(Locale.ROOT);
        T port = ports.get(key);
        if (port == null) {
            throw new IllegalArgumentException("Unknown provider: " + key);
        }
        return port;
    }
}
