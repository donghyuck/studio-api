package studio.one.platform.ai.service.pipeline;

/**
 * Runtime options for RAG retrieval and object-scoped listing.
 */
public record RagPipelineOptions(
        double vectorWeight,
        double lexicalWeight,
        double minRelevanceScore,
        boolean keywordFallbackEnabled,
        boolean semanticFallbackEnabled,
        int defaultListLimit,
        int maxListLimit) {

    public static final double DEFAULT_VECTOR_WEIGHT = 0.7;
    public static final double DEFAULT_LEXICAL_WEIGHT = 0.3;
    public static final double DEFAULT_MIN_RELEVANCE_SCORE = 0.15;
    public static final boolean DEFAULT_KEYWORD_FALLBACK_ENABLED = true;
    public static final boolean DEFAULT_SEMANTIC_FALLBACK_ENABLED = true;
    public static final int DEFAULT_LIST_LIMIT = 20;
    public static final int DEFAULT_MAX_LIST_LIMIT = 200;

    public RagPipelineOptions {
        if (vectorWeight < 0.0d) {
            throw new IllegalArgumentException("vectorWeight must be greater than or equal to 0");
        }
        if (lexicalWeight < 0.0d) {
            throw new IllegalArgumentException("lexicalWeight must be greater than or equal to 0");
        }
        if (vectorWeight + lexicalWeight <= 0.0d) {
            throw new IllegalArgumentException("vectorWeight and lexicalWeight must have a positive sum");
        }
        if (minRelevanceScore < 0.0d) {
            throw new IllegalArgumentException("minRelevanceScore must be greater than or equal to 0");
        }
        if (defaultListLimit < 1) {
            throw new IllegalArgumentException("defaultListLimit must be greater than 0");
        }
        if (maxListLimit < 1) {
            throw new IllegalArgumentException("maxListLimit must be greater than 0");
        }
        if (defaultListLimit > maxListLimit) {
            throw new IllegalArgumentException("defaultListLimit must be less than or equal to maxListLimit");
        }
    }

    public static RagPipelineOptions defaults() {
        return new RagPipelineOptions(
                DEFAULT_VECTOR_WEIGHT,
                DEFAULT_LEXICAL_WEIGHT,
                DEFAULT_MIN_RELEVANCE_SCORE,
                DEFAULT_KEYWORD_FALLBACK_ENABLED,
                DEFAULT_SEMANTIC_FALLBACK_ENABLED,
                DEFAULT_LIST_LIMIT,
                DEFAULT_MAX_LIST_LIMIT);
    }

    public int clampListLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return defaultListLimit;
        }
        return Math.min(requestedLimit, maxListLimit);
    }
}
