package studio.one.platform.ai.web.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.ai.autoconfigure.AiWebChatProperties;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.autoconfigure.config.AiConfigurationMigration;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exposes the configured AI providers, selected models, and vector store state.
 */
@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/info")
@ConditionalOnProperty(prefix = PropertyKeys.AI.Endpoints.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
public class AiInfoController {

    private final AiAdapterProperties properties;
    private final AiWebChatProperties chatProperties;
    private final Environment environment;
    @Nullable
    private final VectorStorePort vectorStorePort;

    public AiInfoController(
            AiAdapterProperties properties,
            AiWebChatProperties chatProperties,
            Environment environment,
            @Nullable VectorStorePort vectorStorePort) {
        this.properties = properties;
        this.chatProperties = chatProperties;
        this.environment = environment;
        this.vectorStorePort = vectorStorePort;
    }

    @GetMapping({"", "/", "/providers", "/providers/"})
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','read') || @endpointAuthz.can('services:ai_embedding','read')")
    public ResponseEntity<ApiResponse<AiInfoResponse>> providers() {
        List<ProviderInfo> providerInfos = new ArrayList<>();
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            AiAdapterProperties.Provider provider = entry.getValue();
            if (provider == null || provider.getType() == null) {
                continue;
            }
            providerInfos.add(mapProvider(entry.getKey(), provider));
        }
        VectorInfo vectorInfo = new VectorInfo(
                vectorStorePort != null,
                vectorStorePort == null ? null : vectorStorePort.getClass().getSimpleName());
        ChatInfo chatInfo = new ChatInfo(new ChatMemoryInfo(
                chatProperties.getMemory().isEnabled(),
                chatProperties.getMemory().getMaxMessages(),
                chatProperties.getMemory().getMaxConversations(),
                chatProperties.getMemory().getTtl().toString()));
        return ResponseEntity.ok(ApiResponse.ok(
                new AiInfoResponse(
                        providerInfos,
                        defaultProvider(),
                        defaultChatProvider(),
                        defaultEmbeddingProvider(),
                        vectorInfo,
                        chatInfo)));
    }

    private ProviderInfo mapProvider(String name, AiAdapterProperties.Provider provider) {
        String baseUrl;
        switch (provider.getType()) {
            case OPENAI:
                baseUrl = firstNonBlank(provider.getBaseUrl(), environment.getProperty("spring.ai.openai.base-url"));
                break;
            case OLLAMA:
                baseUrl = firstNonBlank(provider.getBaseUrl(), environment.getProperty("spring.ai.ollama.base-url"));
                break;
            case GOOGLE_AI_GEMINI:
                baseUrl = firstNonBlank(provider.getBaseUrl(), environment.getProperty("spring.ai.google.genai.chat.base-url"));
                break;
            default:
                baseUrl = null;
                break;
        }
        ProviderChannel chat = new ProviderChannel(
                provider.getChat().isEnabled(),
                chatModel(provider));
        ProviderChannel embedding = new ProviderChannel(
                provider.getEmbedding().isEnabled(),
                embeddingModel(provider));
        return new ProviderInfo(name, provider.getType(), chat, embedding, baseUrl);
    }

    private String chatModel(AiAdapterProperties.Provider provider) {
        switch (provider.getType()) {
            case OPENAI:
                return firstNonBlank(provider.getChat().getModel(), environment.getProperty("spring.ai.openai.chat.options.model"));
            case GOOGLE_AI_GEMINI:
                return firstNonBlank(provider.getChat().getModel(), environment.getProperty("spring.ai.google.genai.chat.options.model"));
            case OLLAMA:
                return firstNonBlank(provider.getChat().getModel(), environment.getProperty("spring.ai.ollama.chat.options.model"));
            default:
                return null;
        }
    }

    private String embeddingModel(AiAdapterProperties.Provider provider) {
        switch (provider.getType()) {
            case OPENAI:
                return firstNonBlank(provider.getEmbedding().getModel(), environment.getProperty("spring.ai.openai.embedding.options.model"));
            case GOOGLE_AI_GEMINI:
                return firstNonBlank(provider.getEmbedding().getModel(), environment.getProperty("spring.ai.google.genai.embedding.text.options.model"));
            case OLLAMA:
                return firstNonBlank(provider.getEmbedding().getModel(), environment.getProperty("spring.ai.ollama.embedding.options.model"));
            default:
                return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String defaultChatProvider() {
        return AiConfigurationMigration.resolveRouting(properties, environment, null).defaultChatProvider();
    }

    private String defaultEmbeddingProvider() {
        return AiConfigurationMigration.resolveRouting(properties, environment, null).defaultEmbeddingProvider();
    }

    private String defaultProvider() {
        return AiConfigurationMigration.resolveRouting(properties, environment, null).defaultProvider();
    }

    public static final class AiInfoResponse {
        private final List<ProviderInfo> providers;
        private final String defaultProvider;
        private final String defaultChatProvider;
        private final String defaultEmbeddingProvider;
        private final VectorInfo vector;
        private final ChatInfo chat;

        public AiInfoResponse(List<ProviderInfo> providers,
                              String defaultProvider,
                              String defaultChatProvider,
                              String defaultEmbeddingProvider,
                              VectorInfo vector,
                              ChatInfo chat) {
            this.providers = providers;
            this.defaultProvider = defaultProvider;
            this.defaultChatProvider = defaultChatProvider;
            this.defaultEmbeddingProvider = defaultEmbeddingProvider;
            this.vector = vector;
            this.chat = chat;
        }

        public AiInfoResponse(List<ProviderInfo> providers, String defaultProvider, VectorInfo vector, ChatInfo chat) {
            this(providers, defaultProvider, defaultProvider, defaultProvider, vector, chat);
        }

        public List<ProviderInfo> getProviders() {
            return providers;
        }

        public String getDefaultProvider() {
            return defaultProvider;
        }

        public String getDefaultChatProvider() {
            return defaultChatProvider;
        }

        public String getDefaultEmbeddingProvider() {
            return defaultEmbeddingProvider;
        }

        public VectorInfo getVector() {
            return vector;
        }

        public ChatInfo getChat() {
            return chat;
        }
    }

    public static final class ProviderInfo {
        private final String name;
        private final AiAdapterProperties.ProviderType type;
        private final ProviderChannel chat;
        private final ProviderChannel embedding;
        private final String baseUrl;

        public ProviderInfo(String name,
                            AiAdapterProperties.ProviderType type,
                            ProviderChannel chat,
                            ProviderChannel embedding,
                            String baseUrl) {
            this.name = name;
            this.type = type;
            this.chat = chat;
            this.embedding = embedding;
            this.baseUrl = baseUrl;
        }

        public String getName() {
            return name;
        }

        public AiAdapterProperties.ProviderType getType() {
            return type;
        }

        public ProviderChannel getChat() {
            return chat;
        }

        public ProviderChannel getEmbedding() {
            return embedding;
        }

        public String getBaseUrl() {
            return baseUrl;
        }
    }

    public static final class ProviderChannel {
        private final boolean enabled;
        private final String model;

        public ProviderChannel(boolean enabled, String model) {
            this.enabled = enabled;
            this.model = model;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getModel() {
            return model;
        }
    }

    public static final class VectorInfo {
        private final boolean available;
        private final String implementation;

        public VectorInfo(boolean available, String implementation) {
            this.available = available;
            this.implementation = implementation;
        }

        public boolean isAvailable() {
            return available;
        }

        public String getImplementation() {
            return implementation;
        }
    }

    public static final class ChatInfo {
        private final ChatMemoryInfo memory;

        public ChatInfo(ChatMemoryInfo memory) {
            this.memory = memory;
        }

        public ChatMemoryInfo getMemory() {
            return memory;
        }
    }

    public static final class ChatMemoryInfo {
        private final boolean enabled;
        private final int maxMessages;
        private final long maxConversations;
        private final String ttl;

        public ChatMemoryInfo(boolean enabled, int maxMessages, long maxConversations, String ttl) {
            this.enabled = enabled;
            this.maxMessages = maxMessages;
            this.maxConversations = maxConversations;
            this.ttl = ttl;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getMaxMessages() {
            return maxMessages;
        }

        public long getMaxConversations() {
            return maxConversations;
        }

        public String getTtl() {
            return ttl;
        }
    }
}
