package studio.one.platform.ai.core.rag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safe diagnostics for a RAG retrieval attempt.
 */
public record RagRetrievalDiagnostics(
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
        int afterMinScoreCount) {

    public RagRetrievalDiagnostics {
        strategy = strategy == null ? Strategy.NONE : strategy;
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
