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
        Map<String, Object> metadata) {

    public AiEmbeddingOption {
        supportedInputTypes = supportedInputTypes == null ? List.of() : List.copyOf(supportedInputTypes);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
