package studio.one.platform.ai.web.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final Environment environment;
    @Nullable
    private final VectorStorePort vectorStorePort;

    public AiInfoController(AiAdapterProperties properties, Environment environment, @Nullable VectorStorePort vectorStorePort) {
        this.properties = properties;
        this.environment = environment;
        this.vectorStorePort = vectorStorePort;
    }

    @GetMapping("/providers")
    @PreAuthorize("@endpointAuthz.can('services:ai_chat','read') || @endpointAuthz.can('services:ai_embedding','read')")
    public ResponseEntity<ApiResponse<AiInfoResponse>> providers() {
        List<ProviderInfo> providerInfos = new ArrayList<>();
        for (Map.Entry<String, AiAdapterProperties.Provider> entry : properties.getProviders().entrySet()) {
            providerInfos.add(mapProvider(entry.getKey(), entry.getValue()));
        }
        addSpringAiAliasProviderInfo(providerInfos);
        VectorInfo vectorInfo = new VectorInfo(
                vectorStorePort != null,
                vectorStorePort == null ? null : vectorStorePort.getClass().getSimpleName());
        return ResponseEntity.ok(ApiResponse.ok(new AiInfoResponse(providerInfos, properties.effectiveDefaultProvider(), vectorInfo)));
    }

    private void addSpringAiAliasProviderInfo(List<ProviderInfo> providerInfos) {
        String alias = properties.springAiAliasOrNull();
        if (alias == null || properties.getProviders().containsKey(alias)) {
            return;
        }
        String sourceProviderId = properties.getSpringAi().getSourceProvider();
        if (sourceProviderId == null) {
            return;
        }
        AiAdapterProperties.Provider sourceProvider = properties.getProviders().get(sourceProviderId);
        if (sourceProvider == null || sourceProvider.getType() != AiAdapterProperties.ProviderType.OPENAI) {
            return;
        }
        ProviderChannel chat = new ProviderChannel(
                sourceProvider.getChat().isEnabled(),
                sourceProvider.getChat().isEnabled() ? environment.getProperty("spring.ai.openai.chat.options.model") : null);
        ProviderChannel embedding = new ProviderChannel(
                sourceProvider.getEmbedding().isEnabled(),
                sourceProvider.getEmbedding().isEnabled() ? environment.getProperty("spring.ai.openai.embedding.options.model") : null);
        providerInfos.add(new ProviderInfo(
                alias,
                sourceProvider.getType(),
                chat,
                embedding,
                environment.getProperty("spring.ai.openai.base-url")));
    }

    private ProviderInfo mapProvider(String name, AiAdapterProperties.Provider provider) {
        String baseUrl = provider.getBaseUrl();
        ProviderChannel chat = new ProviderChannel(provider.getChat().isEnabled(), provider.getChat().getModel());
        ProviderChannel embedding = new ProviderChannel(provider.getEmbedding().isEnabled(), provider.getEmbedding().getModel());
        return new ProviderInfo(name, provider.getType(), chat, embedding, baseUrl);
    }

    public record AiInfoResponse(List<ProviderInfo> providers, String defaultProvider, VectorInfo vector) {}

    public record ProviderInfo(String name,
                               AiAdapterProperties.ProviderType type,
                               ProviderChannel chat,
                               ProviderChannel embedding,
                               String baseUrl) {}

    public record ProviderChannel(boolean enabled, String model) {}

    public record VectorInfo(boolean available, String implementation) {}
}
