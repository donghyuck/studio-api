package studio.one.platform.ai.autoconfigure.config;

import java.util.List;
import java.util.Map;

public record AiEmbeddingOption(
        String profileId,
        String provider,
        String providerType,
        String model,
        Integer dimension,
        List<String> supportedInputTypes,
        boolean defaultProvider,
        boolean defaultProfile,
        boolean profile,
        String source,
        Map<String, Object> metadata,
        String modelId,
        String displayName,
        String embeddingSpaceId,
        List<String> aliases,
        String deploymentId,
        String catalogId,
        String effectiveStatus,
        String statusReason) {

    public AiEmbeddingOption {
        supportedInputTypes = supportedInputTypes == null ? List.of() : List.copyOf(supportedInputTypes);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public AiEmbeddingOption(
            String profileId,
            String provider,
            String providerType,
            String model,
            Integer dimension,
            List<String> supportedInputTypes,
            boolean defaultProvider,
            boolean defaultProfile,
            boolean profile,
            String source,
            Map<String, Object> metadata,
            String modelId,
            String displayName,
            String embeddingSpaceId,
            List<String> aliases) {
        this(profileId, provider, providerType, model, dimension, supportedInputTypes,
                defaultProvider, defaultProfile, profile, source, metadata,
                modelId, displayName, embeddingSpaceId, aliases, null, null, null, null);
    }

    public AiEmbeddingOption(
            String profileId,
            String provider,
            String providerType,
            String model,
            Integer dimension,
            List<String> supportedInputTypes,
            boolean defaultProvider,
            boolean defaultProfile,
            boolean profile,
            String source,
            Map<String, Object> metadata) {
        this(profileId, provider, providerType, model, dimension, supportedInputTypes,
                defaultProvider, defaultProfile, profile, source, metadata,
                profileId, model, profileId, List.of(), null, null, null, null);
    }
}
