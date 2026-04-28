package studio.one.platform.ai.autoconfigure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.ai.service.pipeline.RagPipelineDiagnosticsOptions;
import studio.one.platform.ai.service.pipeline.RagKeywordOptions;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;

import java.time.Duration;

@ConfigurationProperties(prefix = PropertyKeys.AI.PREFIX + ".rag")
public class RagPipelineProperties implements EnvironmentAware, InitializingBean {

    static final String LEGACY_CHUNK_SIZE_PROPERTY = AiConfigurationMigration.LEGACY_RAG_PREFIX + ".chunk-size";
    static final String LEGACY_CHUNK_OVERLAP_PROPERTY = AiConfigurationMigration.LEGACY_RAG_PREFIX + ".chunk-overlap";

    private static final Logger log = LoggerFactory.getLogger(RagPipelineProperties.class);

    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private final CacheProperties cache = new CacheProperties();
    private final RetryProperties retry = new RetryProperties();
    private final RetrievalProperties retrieval = new RetrievalProperties();
    private final ObjectScopeProperties objectScope = new ObjectScopeProperties();
    private final CleanerProperties cleaner = new CleanerProperties();
    private final DiagnosticsProperties diagnostics = new DiagnosticsProperties();
    private final KeywordsProperties keywords = new KeywordsProperties();
    private final JobProperties jobs = new JobProperties();
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        AiConfigurationMigration.applyRagPipelineFallback(environment, this, log);
    }

    /**
     * Deprecated legacy fallback chunk size. This property is only used when no
     * {@code ChunkingOrchestrator} bean is available and starter-ai creates the
     * deprecated {@code TextChunker} fallback.
     *
     * @deprecated Use {@code studio.chunking.max-size} from
     *             {@code starter:studio-platform-starter-chunking} for new RAG
     *             chunking.
     */
    @Deprecated(since = "2.x", forRemoval = false)
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Binds the deprecated legacy fallback chunk size property. Kept non-deprecated
     * so Spring Boot configuration binding does not report the setter itself as a
     * deprecated invocation path.
     */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * Deprecated legacy fallback chunk overlap. This property is only used when no
     * {@code ChunkingOrchestrator} bean is available and starter-ai creates the
     * deprecated {@code TextChunker} fallback.
     *
     * @deprecated Use {@code studio.chunking.overlap} from
     *             {@code starter:studio-platform-starter-chunking} for new RAG
     *             chunking.
     */
    @Deprecated(since = "2.x", forRemoval = false)
    public int getChunkOverlap() {
        return chunkOverlap;
    }

    /**
     * Binds the deprecated legacy fallback chunk overlap property. Kept
     * non-deprecated so Spring Boot configuration binding does not report the
     * setter itself as a deprecated invocation path.
     */
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

    public JobProperties getJobs() {
        return jobs;
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

    public static class JobProperties {
        private String repository = "memory";

        public String getRepository() {
            return repository;
        }

        public void setRepository(String repository) {
            this.repository = repository;
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
