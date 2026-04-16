package studio.one.platform.ai.autoconfigure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.ai.service.pipeline.RagPipelineDiagnosticsOptions;
import studio.one.platform.ai.service.pipeline.RagKeywordOptions;
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
    private final CleanerProperties cleaner = new CleanerProperties();
    private final DiagnosticsProperties diagnostics = new DiagnosticsProperties();
    private final KeywordsProperties keywords = new KeywordsProperties();

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

    public CleanerProperties getCleaner() {
        return cleaner;
    }

    public DiagnosticsProperties getDiagnostics() {
        return diagnostics;
    }

    public KeywordsProperties getKeywords() {
        return keywords;
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
        private final QueryExpansionProperties queryExpansion = new QueryExpansionProperties();

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

        public QueryExpansionProperties getQueryExpansion() {
            return queryExpansion;
        }
    }

    public static class QueryExpansionProperties {
        private boolean enabled = RagKeywordOptions.DEFAULT_QUERY_EXPANSION_ENABLED;
        private int maxKeywords = RagKeywordOptions.DEFAULT_QUERY_EXPANSION_MAX_KEYWORDS;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxKeywords() {
            return maxKeywords;
        }

        public void setMaxKeywords(int maxKeywords) {
            this.maxKeywords = maxKeywords;
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

    public static class CleanerProperties {
        private boolean enabled = false;
        private String prompt = "rag-cleaner";
        private int maxInputChars = 20_000;
        private boolean failOpen = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public int getMaxInputChars() {
            return maxInputChars;
        }

        public void setMaxInputChars(int maxInputChars) {
            this.maxInputChars = maxInputChars;
        }

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }
    }

    public static class DiagnosticsProperties {
        private boolean enabled = RagPipelineDiagnosticsOptions.DEFAULT_ENABLED;
        private boolean logResults = RagPipelineDiagnosticsOptions.DEFAULT_LOG_RESULTS;
        private int maxSnippetChars = RagPipelineDiagnosticsOptions.DEFAULT_MAX_SNIPPET_CHARS;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogResults() {
            return logResults;
        }

        public void setLogResults(boolean logResults) {
            this.logResults = logResults;
        }

        public int getMaxSnippetChars() {
            return maxSnippetChars;
        }

        public void setMaxSnippetChars(int maxSnippetChars) {
            this.maxSnippetChars = maxSnippetChars;
        }
    }

    public static class KeywordsProperties {
        private String scope = RagKeywordOptions.DEFAULT_SCOPE.name().toLowerCase();
        private int maxInputChars = RagKeywordOptions.DEFAULT_MAX_INPUT_CHARS;

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public int getMaxInputChars() {
            return maxInputChars;
        }

        public void setMaxInputChars(int maxInputChars) {
            this.maxInputChars = maxInputChars;
        }
    }
}
