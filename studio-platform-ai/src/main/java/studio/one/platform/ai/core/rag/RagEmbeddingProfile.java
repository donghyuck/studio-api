package studio.one.platform.ai.core.rag;

import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;

/**
 * Named RAG embedding selection profile.
 */
public record RagEmbeddingProfile(
        String profileId,
        String provider,
        String model,
        Integer dimension,
        List<EmbeddingInputType> supportedInputTypes,
        Map<String, Object> metadata) {

    public RagEmbeddingProfile {
        profileId = normalize(profileId);
        provider = normalize(provider);
        model = normalize(model);
        supportedInputTypes = supportedInputTypes == null || supportedInputTypes.isEmpty()
                ? List.of(
                        EmbeddingInputType.TEXT,
                        EmbeddingInputType.TABLE_TEXT,
                        EmbeddingInputType.IMAGE_CAPTION,
                        EmbeddingInputType.OCR_TEXT)
                : List.copyOf(supportedInputTypes);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean supports(EmbeddingInputType inputType) {
        EmbeddingInputType resolved = inputType == null ? EmbeddingInputType.TEXT : inputType;
        return supportedInputTypes.contains(resolved);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
