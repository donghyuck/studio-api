package studio.one.platform.ai.autoconfigure.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.env.Environment;

import studio.one.platform.ai.core.registry.AiProviderRegistry;

public final class DefaultAiEmbeddingOptionCatalog implements AiEmbeddingOptionCatalog {

    private static final List<String> DEFAULT_INPUT_TYPES = List.of(
            "TEXT",
            "TABLE_TEXT",
            "IMAGE_CAPTION",
            "OCR_TEXT");

    private final AiProviderRegistry registry;
    private final AiAdapterProperties aiProperties;
    private final RagEmbeddingProperties ragProperties;
    private final Environment environment;

    public DefaultAiEmbeddingOptionCatalog(AiProviderRegistry registry,
            AiAdapterProperties aiProperties,
            RagEmbeddingProperties ragProperties,
            Environment environment) {
        this.registry = registry;
        this.aiProperties = aiProperties;
        this.ragProperties = ragProperties;
        this.environment = environment;
    }

    @Override
    public List<AiEmbeddingOption> options() {
        Map<String, AiEmbeddingOption> options = new LinkedHashMap<>();
        registry.availableEmbeddingPorts().keySet().forEach(providerId -> {
            AiAdapterProperties.Provider provider = provider(providerId);
            options.put(providerKey(providerId), providerOption(providerId, provider));
        });
        ragProperties.getEmbeddingProfiles().forEach((profileId, profile) -> {
            String providerId = normalize(profile.getProvider());
            if (providerId == null) {
                providerId = registry.defaultEmbeddingProvider();
            }
            AiAdapterProperties.Provider provider = provider(providerId);
            options.put(profileKey(profileId), profileOption(profileId, providerId, provider, profile));
        });
        return new ArrayList<>(options.values());
    }

    private AiEmbeddingOption providerOption(String providerId, AiAdapterProperties.Provider provider) {
        return new AiEmbeddingOption(
                null,
                providerId,
                providerType(provider),
                embeddingModel(providerId, provider),
                embeddingDimension(providerId, provider),
                DEFAULT_INPUT_TYPES,
                providerId.equals(registry.defaultEmbeddingProvider()),
                false,
                false,
                "provider",
                Map.of());
    }

    private AiEmbeddingOption profileOption(String profileId,
            String providerId,
            AiAdapterProperties.Provider provider,
            RagEmbeddingProperties.ProfileProperties profile) {
        Integer dimension = profile.getDimension() == null
                ? embeddingDimension(providerId, provider)
                : profile.getDimension();
        return new AiEmbeddingOption(
                normalize(profileId),
                providerId,
                providerType(provider),
                firstText(profile.getModel(), embeddingModel(providerId, provider)),
                dimension,
                inputTypes(profile.getSupportedInputTypes()),
                providerId.equals(registry.defaultEmbeddingProvider()),
                normalize(profileId).equals(normalize(ragProperties.getDefaultEmbeddingProfile())),
                true,
                "rag-profile",
                profile.getMetadata());
    }

    private AiAdapterProperties.Provider provider(String providerId) {
        return aiProperties.getProviders().get(providerId);
    }

    private String providerType(AiAdapterProperties.Provider provider) {
        return provider == null || provider.getType() == null ? null : provider.getType().name();
    }

    private String embeddingModel(String providerId, AiAdapterProperties.Provider provider) {
        return switch (providerType(provider) == null ? "" : providerType(provider)) {
            case "OPENAI" -> firstText(
                    property("spring.ai.openai.embedding.options.model"),
                    property("studio.ai.providers." + providerId + ".embedding.model"),
                    provider == null ? null : provider.getEmbedding().getModel());
            case "GOOGLE_AI_GEMINI" -> firstText(
                    property("spring.ai.google.genai.embedding.text.options.model"),
                    property("studio.ai.providers." + providerId + ".embedding.model"),
                    provider == null ? null : provider.getEmbedding().getModel());
            case "OLLAMA" -> firstText(
                    property("spring.ai.ollama.embedding.options.model"),
                    property("studio.ai.providers." + providerId + ".embedding.model"),
                    provider == null ? null : provider.getEmbedding().getModel());
            default -> firstText(
                    property("studio.ai.providers." + providerId + ".embedding.model"),
                    provider == null ? null : provider.getEmbedding().getModel());
        };
    }

    private Integer embeddingDimension(String providerId, AiAdapterProperties.Provider provider) {
        Integer dimension = integerProperty("studio.ai.providers." + providerId + ".embedding.dimension");
        if (dimension != null) {
            return dimension;
        }
        if ("GOOGLE_AI_GEMINI".equals(providerType(provider))) {
            dimension = integerProperty("spring.ai.google.genai.embedding.text.options.dimensions");
            if (dimension != null) {
                return dimension;
            }
        }
        return provider == null ? null : provider.getEmbedding().getDimension();
    }

    private List<String> inputTypes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return DEFAULT_INPUT_TYPES;
        }
        List<String> normalized = values.stream()
                .map(this::normalizeInputType)
                .filter(value -> value != null)
                .distinct()
                .toList();
        return normalized.isEmpty() ? DEFAULT_INPUT_TYPES : normalized;
    }

    private String normalizeInputType(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private String property(String key) {
        return environment == null ? null : environment.getProperty(key);
    }

    private Integer integerProperty(String key) {
        if (environment == null) {
            return null;
        }
        return environment.getProperty(key, Integer.class);
    }

    private static String firstText(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String providerKey(String providerId) {
        return "provider:" + providerId;
    }

    private static String profileKey(String profileId) {
        return "profile:" + normalize(profileId);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
