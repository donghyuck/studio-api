package studio.one.platform.ai.autoconfigure.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.env.Environment;

import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.model.ModelDeployment;
import studio.one.platform.ai.model.ModelDeploymentRegistry;
import studio.one.platform.ai.model.ModelWorkload;
import studio.one.platform.ai.model.embedding.EmbeddingSpaceId;

public final class DefaultAiEmbeddingOptionCatalog implements AiEmbeddingOptionCatalog {

    private static final List<String> DEFAULT_INPUT_TYPES = List.of(
            "TEXT",
            "TABLE_TEXT",
            "IMAGE_CAPTION",
            "OCR_TEXT");

    private final AiProviderRegistry registry;
    private final ModelDeploymentRegistry deploymentRegistry;
    private final AiAdapterProperties aiProperties;
    private final RagEmbeddingProperties ragProperties;
    private final Environment environment;

    public DefaultAiEmbeddingOptionCatalog(AiProviderRegistry registry,
            AiAdapterProperties aiProperties,
            RagEmbeddingProperties ragProperties,
            Environment environment) {
        this(registry, null, aiProperties, ragProperties, environment);
    }

    public DefaultAiEmbeddingOptionCatalog(
            AiProviderRegistry registry,
            ModelDeploymentRegistry deploymentRegistry,
            AiAdapterProperties aiProperties,
            RagEmbeddingProperties ragProperties,
            Environment environment) {
        this.registry = registry;
        this.deploymentRegistry = deploymentRegistry;
        this.aiProperties = aiProperties;
        this.ragProperties = ragProperties;
        this.environment = environment;
    }

    @Override
    public List<AiEmbeddingOption> options() {
        Map<String, AiEmbeddingOption> options = new LinkedHashMap<>();
        List<String> profileSignatures = new ArrayList<>();
        ragProperties.getEmbeddingProfiles().forEach((profileId, profile) -> {
            if (deploymentProfile(profile)) {
                return;
            }
            String providerId = normalize(profile.getProvider());
            if (providerId == null) {
                providerId = registry.defaultEmbeddingProvider();
            }
            AiAdapterProperties.Provider provider = provider(providerId);
            AiEmbeddingOption option = profileOption(profileId, providerId, provider, profile);
            options.put(profileKey(profileId), option);
            profileSignatures.add(optionSignature(providerId, option.model(), option.dimension()));
        });
        List<ModelDeployment> deployments = deploymentRegistry == null
                ? List.of() : deploymentRegistry.deployments(ModelWorkload.EMBEDDING);
        if (deployments.isEmpty()) {
            registry.availableEmbeddingPorts().keySet().forEach(providerId -> {
                AiAdapterProperties.Provider provider = provider(providerId);
                AiEmbeddingOption option = providerOption(providerId, provider);
                if (!profileSignatures.contains(optionSignature(option))) {
                    options.put(providerKey(providerId), option);
                }
            });
        } else {
            deployments.forEach(deployment -> options.put(
                    "deployment:" + deployment.deploymentId(), deploymentOption(deployment)));
        }
        return new ArrayList<>(options.values());
    }

    private boolean deploymentProfile(RagEmbeddingProperties.ProfileProperties profile) {
        Object deploymentId = profile.getMetadata().get("deploymentId");
        return deploymentRegistry != null && deploymentId != null
                && deploymentRegistry.find(deploymentId.toString()).isPresent();
    }

    private AiEmbeddingOption deploymentOption(ModelDeployment deployment) {
        AiAdapterProperties.Provider provider = provider(deployment.providerRef());
        boolean defaultDeployment = deploymentRegistry.defaultDeployment(ModelWorkload.EMBEDDING)
                .map(value -> value.deploymentId().equals(deployment.deploymentId()))
                .orElse(false);
        List<String> aliases = deployment.definition().aliases().stream().sorted().toList();
        String spaceId = EmbeddingSpaceId.from(deployment.embeddingContract());
        return new AiEmbeddingOption(
                null,
                deployment.providerRef(),
                providerType(provider),
                deployment.definition().apiModel(),
                deployment.dimension(),
                DEFAULT_INPUT_TYPES,
                defaultDeployment,
                false,
                false,
                "deployment",
                Map.of(
                        "deploymentId", deployment.deploymentId(),
                        "catalogId", deployment.definition().catalogId(),
                        "effectiveStatus", "EFFECTIVE"),
                deployment.definition().catalogId(),
                firstText(deployment.definition().displayName(), deployment.definition().apiModel()),
                spaceId,
                aliases,
                deployment.deploymentId(),
                deployment.definition().catalogId(),
                "EFFECTIVE",
                "Configured deployment; provider discovery has not been performed");
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
                Map.of(),
                null,
                embeddingModel(providerId, provider),
                null,
                List.of());
    }

    private AiEmbeddingOption profileOption(String profileId,
            String providerId,
            AiAdapterProperties.Provider provider,
            RagEmbeddingProperties.ProfileProperties profile) {
        Integer dimension = profile.getDimension() == null
                ? embeddingDimension(providerId, provider)
                : profile.getDimension();
        Map<String, Object> metadata = profile.getMetadata();
        String canonicalProvider = firstText(text(metadata.get("providerId")), providerId);
        String embeddingSpaceId = firstText(text(metadata.get("embeddingSpaceId")), profileId);
        String displayName = firstText(profile.getDisplayName(), profile.getModel(), embeddingModel(providerId, provider));
        return new AiEmbeddingOption(
                normalize(profileId),
                canonicalProvider,
                providerType(provider),
                firstText(profile.getModel(), embeddingModel(providerId, provider)),
                dimension,
                inputTypes(profile.getSupportedInputTypes()),
                providerId.equals(registry.defaultEmbeddingProvider()),
                normalize(profileId).equals(normalize(ragProperties.getDefaultEmbeddingProfile())),
                true,
                "rag-profile",
                metadata,
                normalize(profileId),
                displayName,
                embeddingSpaceId,
                profile.getAliases());
    }

    private AiAdapterProperties.Provider provider(String providerId) {
        return aiProperties.getProviders().get(providerId);
    }

    private String providerType(AiAdapterProperties.Provider provider) {
        return provider == null || provider.getType() == null ? null : provider.getType().name();
    }

    private String embeddingModel(String providerId, AiAdapterProperties.Provider provider) {
        if (provider != null && provider.getEmbedding().isModelOverride()
                && normalize(provider.getEmbedding().getModel()) != null) {
            return normalize(provider.getEmbedding().getModel());
        }
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

    private static String optionSignature(AiEmbeddingOption option) {
        return optionSignature(option.provider(), option.model(), option.dimension());
    }

    private static String optionSignature(String provider, String model, Integer dimension) {
        return normalize(provider) + "|" + normalize(model) + "|" + dimension;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
