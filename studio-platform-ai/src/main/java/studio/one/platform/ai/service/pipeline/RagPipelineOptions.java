package studio.one.platform.ai.service.pipeline;

import studio.one.platform.ai.core.rag.RagSearchRequest;

/**
 * Runtime options for RAG retrieval and object-scoped listing.
 */
public final class RagPipelineOptions {

    private final double vectorWeight;
    private final double lexicalWeight;
    private final double minScore;
    private final double minRelevanceScore;
    private final boolean keywordFallbackEnabled;
    private final boolean semanticFallbackEnabled;
    private final int topK;
    private final int defaultListLimit;
    private final int maxListLimit;

    public RagPipelineOptions(
            double vectorWeight,
            double lexicalWeight,
            double minScore,
            double minRelevanceScore,
            boolean keywordFallbackEnabled,
            boolean semanticFallbackEnabled,
            int topK,
            int defaultListLimit,
            int maxListLimit
    ) {
                if (vectorWeight < 0.0d) {
                    throw new IllegalArgumentException("vectorWeight must be greater than or equal to 0");
                }
                if (lexicalWeight < 0.0d) {
                    throw new IllegalArgumentException("lexicalWeight must be greater than or equal to 0");
                }
                if (vectorWeight + lexicalWeight <= 0.0d) {
                    throw new IllegalArgumentException("vectorWeight and lexicalWeight must have a positive sum");
                }
                if (minScore < 0.0d) {
                    throw new IllegalArgumentException("minScore must be greater than or equal to 0");
                }
                if (minScore > RagSearchRequest.MAX_MIN_SCORE) {
                    throw new IllegalArgumentException("minScore must be less than or equal to " + RagSearchRequest.MAX_MIN_SCORE);
                }
                if (minRelevanceScore < 0.0d) {
                    throw new IllegalArgumentException("minRelevanceScore must be greater than or equal to 0");
                }
                if (topK < 1 || topK > MAX_TOP_K) {
                    throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K);
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
        
        this.vectorWeight = vectorWeight;
        this.lexicalWeight = lexicalWeight;
        this.minScore = minScore;
        this.minRelevanceScore = minRelevanceScore;
        this.keywordFallbackEnabled = keywordFallbackEnabled;
        this.semanticFallbackEnabled = semanticFallbackEnabled;
        this.topK = topK;
        this.defaultListLimit = defaultListLimit;
        this.maxListLimit = maxListLimit;
    }

    public double vectorWeight() {
        return vectorWeight;
    }

    public double lexicalWeight() {
        return lexicalWeight;
    }

    public double minScore() {
        return minScore;
    }

    public double minRelevanceScore() {
        return minRelevanceScore;
    }

    public boolean keywordFallbackEnabled() {
        return keywordFallbackEnabled;
    }

    public boolean semanticFallbackEnabled() {
        return semanticFallbackEnabled;
    }

    public int topK() {
        return topK;
    }

    public int defaultListLimit() {
        return defaultListLimit;
    }

    public int maxListLimit() {
        return maxListLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagPipelineOptions)) {
            return false;
        }
        RagPipelineOptions that = (RagPipelineOptions) o;
        return Double.compare(that.vectorWeight, vectorWeight) == 0
                && Double.compare(that.lexicalWeight, lexicalWeight) == 0
                && Double.compare(that.minScore, minScore) == 0
                && Double.compare(that.minRelevanceScore, minRelevanceScore) == 0
                && keywordFallbackEnabled == that.keywordFallbackEnabled
                && semanticFallbackEnabled == that.semanticFallbackEnabled
                && topK == that.topK
                && defaultListLimit == that.defaultListLimit
                && maxListLimit == that.maxListLimit;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(vectorWeight, lexicalWeight, minScore, minRelevanceScore, keywordFallbackEnabled, semanticFallbackEnabled, topK, defaultListLimit, maxListLimit);
    }

    @Override
    public String toString() {
        return "RagPipelineOptions[" +
                "vectorWeight=" + vectorWeight + ", " +
                "lexicalWeight=" + lexicalWeight + ", " +
                "minScore=" + minScore + ", " +
                "minRelevanceScore=" + minRelevanceScore + ", " +
                "keywordFallbackEnabled=" + keywordFallbackEnabled + ", " +
                "semanticFallbackEnabled=" + semanticFallbackEnabled + ", " +
                "topK=" + topK + ", " +
                "defaultListLimit=" + defaultListLimit + ", " +
                "maxListLimit=" + maxListLimit +
                "]";
    }

    public static final double DEFAULT_VECTOR_WEIGHT = 0.7;
    public static final double DEFAULT_LEXICAL_WEIGHT = 0.3;
    public static final int DEFAULT_TOP_K = 3;
    public static final int MAX_TOP_K = RagSearchRequest.MAX_TOP_K;
    public static final double DEFAULT_MIN_SCORE = 0.15;
    public static final double DEFAULT_MIN_RELEVANCE_SCORE = 0.15;
    public static final boolean DEFAULT_KEYWORD_FALLBACK_ENABLED = true;
    public static final boolean DEFAULT_SEMANTIC_FALLBACK_ENABLED = true;
    public static final int DEFAULT_LIST_LIMIT = 20;
    public static final int DEFAULT_MAX_LIST_LIMIT = 200;

    public static RagPipelineOptions defaults() {
        return new RagPipelineOptions(
                DEFAULT_VECTOR_WEIGHT,
                DEFAULT_LEXICAL_WEIGHT,
                DEFAULT_MIN_SCORE,
                DEFAULT_MIN_RELEVANCE_SCORE,
                DEFAULT_KEYWORD_FALLBACK_ENABLED,
                DEFAULT_SEMANTIC_FALLBACK_ENABLED,
                DEFAULT_TOP_K,
                DEFAULT_LIST_LIMIT,
                DEFAULT_MAX_LIST_LIMIT);
    }

    public RagPipelineOptions(
            double vectorWeight,
            double lexicalWeight,
            double minRelevanceScore,
            boolean keywordFallbackEnabled,
            boolean semanticFallbackEnabled,
            int defaultListLimit,
            int maxListLimit) {
        this(vectorWeight, lexicalWeight, minRelevanceScore, minRelevanceScore,
                keywordFallbackEnabled, semanticFallbackEnabled, DEFAULT_TOP_K, defaultListLimit, maxListLimit);
    }

    public int clampListLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return defaultListLimit;
        }
        return Math.min(requestedLimit, maxListLimit);
    }
}
