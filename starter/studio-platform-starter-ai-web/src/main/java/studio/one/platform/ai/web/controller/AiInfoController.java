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

    @GetMapping("/providers")
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
                new AiInfoResponse(providerInfos, properties.getDefaultProvider(), vectorInfo, chatInfo)));
    }

    private ProviderInfo mapProvider(String name, AiAdapterProperties.Provider provider) {
        String baseUrl = switch (provider.getType()) {
            case OPENAI -> environment.getProperty("spring.ai.openai.base-url");
            case OLLAMA -> firstNonBlank(environment.getProperty("spring.ai.ollama.base-url"), provider.getBaseUrl());
            case GOOGLE_AI_GEMINI -> provider.getBaseUrl();
        };
        ProviderChannel chat = new ProviderChannel(
                provider.getChat().isEnabled(),
                chatModel(provider));
        ProviderChannel embedding = new ProviderChannel(
                provider.getEmbedding().isEnabled(),
                embeddingModel(provider));
        return new ProviderInfo(name, provider.getType(), chat, embedding, baseUrl);
    }

    private String chatModel(AiAdapterProperties.Provider provider) {
        return switch (provider.getType()) {
            case OPENAI -> firstNonBlank(
                    environment.getProperty("spring.ai.openai.chat.options.model"),
                    provider.getChat().getModel());
            case GOOGLE_AI_GEMINI -> firstNonBlank(
                    environment.getProperty("spring.ai.google.genai.chat.options.model"),
                    provider.getChat().getModel());
            case OLLAMA -> firstNonBlank(
                    environment.getProperty("spring.ai.ollama.chat.options.model"),
                    provider.getChat().getModel());
        };
    }

    private String embeddingModel(AiAdapterProperties.Provider provider) {
        return switch (provider.getType()) {
            case OPENAI -> firstNonBlank(
                    environment.getProperty("spring.ai.openai.embedding.options.model"),
                    provider.getEmbedding().getModel());
            case GOOGLE_AI_GEMINI -> firstNonBlank(
                    environment.getProperty("spring.ai.google.genai.embedding.text.options.model"),
                    provider.getEmbedding().getModel());
            case OLLAMA -> firstNonBlank(
                    environment.getProperty("spring.ai.ollama.embedding.options.model"),
                    provider.getEmbedding().getModel());
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record AiInfoResponse(List<ProviderInfo> providers, String defaultProvider, VectorInfo vector, ChatInfo chat) {}

    public record ProviderInfo(String name,
                               AiAdapterProperties.ProviderType type,
                               ProviderChannel chat,
                               ProviderChannel embedding,
                               String baseUrl) {}

    public record ProviderChannel(boolean enabled, String model) {}

    public record VectorInfo(boolean available, String implementation) {}

    public record ChatInfo(ChatMemoryInfo memory) {}

    public record ChatMemoryInfo(boolean enabled, int maxMessages, long maxConversations, String ttl) {}
}
