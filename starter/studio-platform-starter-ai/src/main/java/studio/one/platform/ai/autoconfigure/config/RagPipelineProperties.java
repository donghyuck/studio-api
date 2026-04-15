package studio.one.platform.ai.autoconfigure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;

import java.time.Duration;

@ConfigurationProperties(prefix = PropertyKeys.AI.PREFIX + ".pipeline")
public class RagPipelineProperties {

    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private final CacheProperties cache = new CacheProperties();
    private final RetryProperties retry = new RetryProperties();
    private final RetrievalProperties retrieval = new RetrievalProperties();
    private final ObjectScopeProperties objectScope = new ObjectScopeProperties();

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public RetryProperties getRetry() {
        return retry;
    }

    public RetrievalProperties getRetrieval() {
        return retrieval;
    }

    public ObjectScopeProperties getObjectScope() {
        return objectScope;
    }

    public static class CacheProperties {
        private long maximumSize = 1_000;
        private Duration ttl = Duration.ofMinutes(10);

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }

    public static class RetryProperties {
        private int maxAttempts = 3;
        private Duration waitDuration = Duration.ofSeconds(2);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getWaitDuration() {
            return waitDuration;
        }

        public void setWaitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
        }
    }

    public static class RetrievalProperties {
        private double vectorWeight = RagPipelineOptions.DEFAULT_VECTOR_WEIGHT;
        private double lexicalWeight = RagPipelineOptions.DEFAULT_LEXICAL_WEIGHT;
        private double minRelevanceScore = RagPipelineOptions.DEFAULT_MIN_RELEVANCE_SCORE;
        private boolean keywordFallbackEnabled = RagPipelineOptions.DEFAULT_KEYWORD_FALLBACK_ENABLED;
        private boolean semanticFallbackEnabled = RagPipelineOptions.DEFAULT_SEMANTIC_FALLBACK_ENABLED;

        public double getVectorWeight() {
            return vectorWeight;
        }

        public void setVectorWeight(double vectorWeight) {
            this.vectorWeight = vectorWeight;
        }

        public double getLexicalWeight() {
            return lexicalWeight;
        }

        public void setLexicalWeight(double lexicalWeight) {
            this.lexicalWeight = lexicalWeight;
        }

        public double getMinRelevanceScore() {
            return minRelevanceScore;
        }

        public void setMinRelevanceScore(double minRelevanceScore) {
            this.minRelevanceScore = minRelevanceScore;
        }

        public boolean isKeywordFallbackEnabled() {
            return keywordFallbackEnabled;
        }

        public void setKeywordFallbackEnabled(boolean keywordFallbackEnabled) {
            this.keywordFallbackEnabled = keywordFallbackEnabled;
        }

        public boolean isSemanticFallbackEnabled() {
            return semanticFallbackEnabled;
        }

        public void setSemanticFallbackEnabled(boolean semanticFallbackEnabled) {
            this.semanticFallbackEnabled = semanticFallbackEnabled;
        }
    }

    public static class ObjectScopeProperties {
        private int defaultListLimit = RagPipelineOptions.DEFAULT_LIST_LIMIT;
        private int maxListLimit = RagPipelineOptions.DEFAULT_MAX_LIST_LIMIT;

        public int getDefaultListLimit() {
            return defaultListLimit;
        }

        public void setDefaultListLimit(int defaultListLimit) {
            this.defaultListLimit = defaultListLimit;
        }

        public int getMaxListLimit() {
            return maxListLimit;
        }

        public void setMaxListLimit(int maxListLimit) {
            this.maxListLimit = maxListLimit;
        }
    }
}
