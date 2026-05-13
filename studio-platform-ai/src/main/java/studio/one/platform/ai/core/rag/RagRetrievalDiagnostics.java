package studio.one.platform.ai.core.rag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safe diagnostics for a RAG retrieval attempt.
 */
public final class RagRetrievalDiagnostics {

    private final Strategy strategy;
    private final int initialResultCount;
    private final int finalResultCount;
    private final double minScore;
    private final double vectorWeight;
    private final double lexicalWeight;
    private final String objectType;
    private final String objectId;
    private final int topK;
    private final Integer requestedTopK;
    private final Double requestedMinScore;
    private final int beforeMinScoreCount;
    private final int afterMinScoreCount;

    public RagRetrievalDiagnostics(
            Strategy strategy,
            int initialResultCount,
            int finalResultCount,
            double minScore,
            double vectorWeight,
            double lexicalWeight,
            String objectType,
            String objectId,
            int topK,
            Integer requestedTopK,
            Double requestedMinScore,
            int beforeMinScoreCount,
            int afterMinScoreCount
    ) {
                strategy = strategy == null ? Strategy.NONE : strategy;
        
        this.strategy = strategy;
        this.initialResultCount = initialResultCount;
        this.finalResultCount = finalResultCount;
        this.minScore = minScore;
        this.vectorWeight = vectorWeight;
        this.lexicalWeight = lexicalWeight;
        this.objectType = objectType;
        this.objectId = objectId;
        this.topK = topK;
        this.requestedTopK = requestedTopK;
        this.requestedMinScore = requestedMinScore;
        this.beforeMinScoreCount = beforeMinScoreCount;
        this.afterMinScoreCount = afterMinScoreCount;
    }

    public Strategy strategy() {
        return strategy;
    }

    public int initialResultCount() {
        return initialResultCount;
    }

    public int finalResultCount() {
        return finalResultCount;
    }

    public double minScore() {
        return minScore;
    }

    public double vectorWeight() {
        return vectorWeight;
    }

    public double lexicalWeight() {
        return lexicalWeight;
    }

    public String objectType() {
        return objectType;
    }

    public String objectId() {
        return objectId;
    }

    public int topK() {
        return topK;
    }

    public Integer requestedTopK() {
        return requestedTopK;
    }

    public Double requestedMinScore() {
        return requestedMinScore;
    }

    public int beforeMinScoreCount() {
        return beforeMinScoreCount;
    }

    public int afterMinScoreCount() {
        return afterMinScoreCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RagRetrievalDiagnostics)) {
            return false;
        }
        RagRetrievalDiagnostics that = (RagRetrievalDiagnostics) o;
        return java.util.Objects.equals(strategy, that.strategy)
                && initialResultCount == that.initialResultCount
                && finalResultCount == that.finalResultCount
                && Double.compare(that.minScore, minScore) == 0
                && Double.compare(that.vectorWeight, vectorWeight) == 0
                && Double.compare(that.lexicalWeight, lexicalWeight) == 0
                && java.util.Objects.equals(objectType, that.objectType)
                && java.util.Objects.equals(objectId, that.objectId)
                && topK == that.topK
                && java.util.Objects.equals(requestedTopK, that.requestedTopK)
                && java.util.Objects.equals(requestedMinScore, that.requestedMinScore)
                && beforeMinScoreCount == that.beforeMinScoreCount
                && afterMinScoreCount == that.afterMinScoreCount;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(strategy, initialResultCount, finalResultCount, minScore, vectorWeight, lexicalWeight, objectType, objectId, topK, requestedTopK, requestedMinScore, beforeMinScoreCount, afterMinScoreCount);
    }

    @Override
    public String toString() {
        return "RagRetrievalDiagnostics[" +
                "strategy=" + strategy + ", " +
                "initialResultCount=" + initialResultCount + ", " +
                "finalResultCount=" + finalResultCount + ", " +
                "minScore=" + minScore + ", " +
                "vectorWeight=" + vectorWeight + ", " +
                "lexicalWeight=" + lexicalWeight + ", " +
                "objectType=" + objectType + ", " +
                "objectId=" + objectId + ", " +
                "topK=" + topK + ", " +
                "requestedTopK=" + requestedTopK + ", " +
                "requestedMinScore=" + requestedMinScore + ", " +
                "beforeMinScoreCount=" + beforeMinScoreCount + ", " +
                "afterMinScoreCount=" + afterMinScoreCount +
                "]";
    }

    public RagRetrievalDiagnostics(
            Strategy strategy,
            int initialResultCount,
            int finalResultCount,
            double minScore,
            double vectorWeight,
            double lexicalWeight,
            String objectType,
            String objectId,
            int topK) {
        this(strategy, initialResultCount, finalResultCount, minScore, vectorWeight, lexicalWeight,
                objectType, objectId, topK, topK, minScore, finalResultCount, finalResultCount);
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("strategy", strategy.value());
        metadata.put("initialResultCount", initialResultCount);
        metadata.put("finalResultCount", finalResultCount);
        metadata.put("minScore", minScore);
        metadata.put("vectorWeight", vectorWeight);
        metadata.put("lexicalWeight", lexicalWeight);
        metadata.put("objectType", objectType);
        metadata.put("objectId", objectId);
        metadata.put("topK", topK);
        metadata.put("requestedTopK", requestedTopK);
        metadata.put("effectiveTopK", topK);
        metadata.put("requestedMinScore", requestedMinScore);
        metadata.put("effectiveMinScore", minScore);
        metadata.put("beforeMinScoreCount", beforeMinScoreCount);
        metadata.put("afterMinScoreCount", afterMinScoreCount);
        return metadata;
    }

    public enum Strategy {
        HYBRID("hybrid"),
        KEYWORD_ENRICHED_HYBRID("keyword_enriched_hybrid"),
        SEMANTIC("semantic"),
        NONE("none");

        private final String value;

        Strategy(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
