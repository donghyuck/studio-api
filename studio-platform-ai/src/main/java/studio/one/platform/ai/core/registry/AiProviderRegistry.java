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
    private final String defaultChatProvider;
    private final String defaultEmbeddingProvider;

    public AiProviderRegistry(String defaultProvider,
            Map<String, ChatPort> chatPorts,
            Map<String, EmbeddingPort> embeddingPorts) {
        this(defaultProvider, defaultProvider, defaultProvider, chatPorts, embeddingPorts);
    }

    public AiProviderRegistry(String defaultProvider,
            String defaultChatProvider,
            String defaultEmbeddingProvider,
            Map<String, ChatPort> chatPorts,
            Map<String, EmbeddingPort> embeddingPorts) {
        this.defaultProvider = normalize(defaultProvider);
        this.defaultChatProvider = normalize(defaultChatProvider);
        this.defaultEmbeddingProvider = normalize(defaultEmbeddingProvider);
        this.chatPorts = Collections.unmodifiableMap(normalizeKeys(chatPorts));
        this.embeddingPorts = Collections.unmodifiableMap(normalizeKeys(embeddingPorts));
    }

    public Map<String, ChatPort> availableChatPorts() {
        return chatPorts;
    }

    public Map<String, EmbeddingPort> availableEmbeddingPorts() {
        return embeddingPorts;
    }

    public ChatPort chatPort(String provider) {
        return lookup(chatPorts, provider, defaultChatProvider);
    }

    public EmbeddingPort embeddingPort(String provider) {
        return lookup(embeddingPorts, provider, defaultEmbeddingProvider);
    }

    public String defaultProvider() {
        return defaultProvider;
    }

    /**
     * Returns the provider used by {@link #chatPort(String)} when the requested provider is {@code null}.
     */
    public String defaultChatProvider() {
        return defaultChatProvider;
    }

    /**
     * Returns the provider used by {@link #embeddingPort(String)} when the requested provider is {@code null}.
     */
    public String defaultEmbeddingProvider() {
        return defaultEmbeddingProvider;
    }

    private <T> T lookup(Map<String, T> ports, String provider, String defaultKey) {
        String key = provider == null ? defaultKey : normalize(provider);
        T port = ports.get(key);
        if (port == null) {
            throw new IllegalArgumentException("Unknown provider: " + key);
        }
        return port;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static <T> Map<String, T> normalizeKeys(Map<String, T> ports) {
        Map<String, T> normalized = new LinkedHashMap<>();
        ports.forEach((key, value) -> normalized.put(normalize(key), value));
        return normalized;
    }
}
