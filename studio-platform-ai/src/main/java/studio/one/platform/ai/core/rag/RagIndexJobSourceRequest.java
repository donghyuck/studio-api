package studio.one.platform.ai.core.rag;

import java.util.List;
import java.util.Map;

public final class RagIndexJobSourceRequest {

    private final Map<String, Object> metadata;
    private final List<String> keywords;
    private final boolean useLlmKeywordExtraction;
    private final String embeddingProfileId;
    private final String embeddingProvider;
    private final String embeddingModel;

    public RagIndexJobSourceRequest(
            Map<String, Object> metadata,
            List<String> keywords,
            boolean useLlmKeywordExtraction,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel
    ) {
                metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
                keywords = keywords == null ? List.of() : List.copyOf(keywords);
                embeddingProfileId = normalize(embeddingProfileId);
                embeddingProvider = normalize(embeddingProvider);
                embeddingModel = normalize(embeddingModel);
        
        this.metadata = metadata;
        this.keywords = keywords;
        this.useLlmKeywordExtraction = useLlmKeywordExtraction;
        this.embeddingProfileId = embeddingProfileId;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public List<String> keywords() {
        return keywords;
    }

    public boolean useLlmKeywordExtraction() {
        return useLlmKeywordExtraction;
    }

    public String embeddingProfileId() {
        return embeddingProfileId;
    }

    public String embeddingProvider() {
        return embeddingProvider;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagIndexJobSourceRequest)) {
            return false;
        }
        RagIndexJobSourceRequest that = (RagIndexJobSourceRequest) o;
        return java.util.Objects.equals(metadata, that.metadata)
                && java.util.Objects.equals(keywords, that.keywords)
                && useLlmKeywordExtraction == that.useLlmKeywordExtraction
                && java.util.Objects.equals(embeddingProfileId, that.embeddingProfileId)
                && java.util.Objects.equals(embeddingProvider, that.embeddingProvider)
                && java.util.Objects.equals(embeddingModel, that.embeddingModel);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(metadata, keywords, useLlmKeywordExtraction, embeddingProfileId, embeddingProvider, embeddingModel);
    }

    @Override
    public String toString() {
        return "RagIndexJobSourceRequest[" +
                "metadata=" + metadata + ", " +
                "keywords=" + keywords + ", " +
                "useLlmKeywordExtraction=" + useLlmKeywordExtraction + ", " +
                "embeddingProfileId=" + embeddingProfileId + ", " +
                "embeddingProvider=" + embeddingProvider + ", " +
                "embeddingModel=" + embeddingModel +
                "]";
    }

    public RagIndexJobSourceRequest(
            Map<String, Object> metadata,
            List<String> keywords,
            boolean useLlmKeywordExtraction) {
        this(metadata, keywords, useLlmKeywordExtraction, null, null, null);
    }

    public static RagIndexJobSourceRequest empty() {
        return new RagIndexJobSourceRequest(Map.of(), List.of(), false);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
