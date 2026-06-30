package studio.one.platform.chunking.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import studio.one.platform.chunking.core.ChunkUnit;
import studio.one.platform.chunking.core.ChunkingStrategyType;

@ConfigurationProperties(prefix = "studio.chunking")
public class ChunkingProperties {

    private boolean enabled = true;

    private String strategy = "recursive";

    private int maxSize = 800;

    private int overlap = 100;

    private String unit = "character";

    private final TokenizerProperties tokenizer = new TokenizerProperties();

    private final BlockifyProperties blockify = new BlockifyProperties();

    private final KnowledgeBlockProperties knowledgeBlock = new KnowledgeBlockProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("studio.chunking.max-size must be greater than zero");
        }
        this.maxSize = maxSize;
    }

    public int getOverlap() {
        return overlap;
    }

    public void setOverlap(int overlap) {
        if (overlap < 0) {
            throw new IllegalArgumentException("studio.chunking.overlap must not be negative");
        }
        this.overlap = overlap;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public TokenizerProperties getTokenizer() {
        return tokenizer;
    }

    public BlockifyProperties getBlockify() {
        return blockify;
    }

    public KnowledgeBlockProperties getKnowledgeBlock() {
        return knowledgeBlock;
    }

    public ChunkingStrategyType strategyType() {
        return ChunkingStrategyType.from(strategy);
    }

    public ChunkUnit unitType() {
        return ChunkUnit.from(unit);
    }

    public void validate() {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("studio.chunking.max-size must be greater than zero");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("studio.chunking.overlap must not be negative");
        }
        if (overlap >= maxSize) {
            throw new IllegalArgumentException("studio.chunking.overlap must be less than studio.chunking.max-size");
        }
        strategyType();
        unitType();
    }

    public int effectiveMaxSize(int requestedMaxSize) {
        return requestedMaxSize <= 0 ? maxSize : requestedMaxSize;
    }

    public int effectiveOverlap(int requestedOverlap) {
        return requestedOverlap < 0 ? overlap : requestedOverlap;
    }

    public static class TokenizerProperties {
        private boolean autoDetect = true;
        private String fallback = "approximate";
        private boolean failOnUnknownModel = false;
        private final Map<String, TokenizerMappingProperties> mappings = new LinkedHashMap<>();

        public boolean isAutoDetect() {
            return autoDetect;
        }

        public void setAutoDetect(boolean autoDetect) {
            this.autoDetect = autoDetect;
        }

        public String getFallback() {
            return fallback;
        }

        public void setFallback(String fallback) {
            this.fallback = fallback;
        }

        public boolean isFailOnUnknownModel() {
            return failOnUnknownModel;
        }

        public void setFailOnUnknownModel(boolean failOnUnknownModel) {
            this.failOnUnknownModel = failOnUnknownModel;
        }

        public Map<String, TokenizerMappingProperties> getMappings() {
            return mappings;
        }
    }

    public static class TokenizerMappingProperties {
        private String provider;
        private String encoding;
        private String tokenizerModel;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEncoding() {
            return encoding;
        }

        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }

        public String getTokenizerModel() {
            return tokenizerModel;
        }

        public void setTokenizerModel(String tokenizerModel) {
            this.tokenizerModel = tokenizerModel;
        }
    }

    public static class BlockifyProperties {
        private boolean enabled = false;
        private int maxInputTokens = 2500;
        private int maxOutputTokensPerBlock = 500;
        private int maxBlocksPerSection = 10;
        private int maxSectionsPerDocument = 500;
        private double maxEstimatedCostPerJob = 10.0d;
        private int perJobConcurrency = 2;
        private int globalConcurrency = 4;
        private String generatorType = "heuristic";
        private String promptVersion = "blockify-v1";
        private String generatorModel = "heuristic-blockify-v1";
        private String llmProvider;
        private String llmModel;
        private Duration generationTimeout = Duration.ofSeconds(30);
        private double temperature = 0.0d;
        private double topP = 1.0d;
        private boolean requireSourceEvidence = true;
        private boolean distillationEnabled = true;
        private double distillationSimilarityThreshold = 0.82d;
        private String documentType = "auto";
        private int minAnswerChars = 80;
        private int minChunkChars = 150;
        private int maxChunkChars = 900;
        private int minEvidenceChars = 40;
        private final BlockifyPiiMaskingProperties piiMasking = new BlockifyPiiMaskingProperties();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxInputTokens() {
            return maxInputTokens;
        }

        public void setMaxInputTokens(int maxInputTokens) {
            this.maxInputTokens = positive(maxInputTokens, "studio.chunking.blockify.max-input-tokens");
        }

        public int getMaxOutputTokensPerBlock() {
            return maxOutputTokensPerBlock;
        }

        public void setMaxOutputTokensPerBlock(int maxOutputTokensPerBlock) {
            this.maxOutputTokensPerBlock = positive(maxOutputTokensPerBlock,
                    "studio.chunking.blockify.max-output-tokens-per-block");
        }

        public int getMaxBlocksPerSection() {
            return maxBlocksPerSection;
        }

        public void setMaxBlocksPerSection(int maxBlocksPerSection) {
            this.maxBlocksPerSection = positive(maxBlocksPerSection,
                    "studio.chunking.blockify.max-blocks-per-section");
        }

        public int getMaxSectionsPerDocument() {
            return maxSectionsPerDocument;
        }

        public void setMaxSectionsPerDocument(int maxSectionsPerDocument) {
            this.maxSectionsPerDocument = positive(maxSectionsPerDocument,
                    "studio.chunking.blockify.max-sections-per-document");
        }

        public double getMaxEstimatedCostPerJob() {
            return maxEstimatedCostPerJob;
        }

        public void setMaxEstimatedCostPerJob(double maxEstimatedCostPerJob) {
            if (maxEstimatedCostPerJob < 0) {
                throw new IllegalArgumentException("studio.chunking.blockify.max-estimated-cost-per-job must not be negative");
            }
            this.maxEstimatedCostPerJob = maxEstimatedCostPerJob;
        }

        public int getPerJobConcurrency() {
            return perJobConcurrency;
        }

        public void setPerJobConcurrency(int perJobConcurrency) {
            this.perJobConcurrency = positive(perJobConcurrency, "studio.chunking.blockify.per-job-concurrency");
        }

        public int getGlobalConcurrency() {
            return globalConcurrency;
        }

        public void setGlobalConcurrency(int globalConcurrency) {
            this.globalConcurrency = positive(globalConcurrency, "studio.chunking.blockify.global-concurrency");
        }

        public String getGeneratorType() {
            return generatorType;
        }

        public void setGeneratorType(String generatorType) {
            String normalized = normalize(generatorType);
            if (normalized == null) {
                this.generatorType = "heuristic";
                return;
            }
            normalized = normalized.toLowerCase(java.util.Locale.ROOT);
            if (!normalized.equals("heuristic") && !normalized.equals("llm")) {
                throw new IllegalArgumentException("studio.chunking.blockify.generator-type must be heuristic or llm");
            }
            this.generatorType = normalized;
        }

        public String getPromptVersion() {
            return promptVersion;
        }

        public void setPromptVersion(String promptVersion) {
            this.promptVersion = promptVersion;
        }

        public String getGeneratorModel() {
            return generatorModel;
        }

        public void setGeneratorModel(String generatorModel) {
            this.generatorModel = generatorModel;
        }

        public String getLlmProvider() {
            return llmProvider;
        }

        public void setLlmProvider(String llmProvider) {
            this.llmProvider = normalize(llmProvider);
        }

        public String getLlmModel() {
            return llmModel;
        }

        public void setLlmModel(String llmModel) {
            this.llmModel = normalize(llmModel);
        }

        public Duration getGenerationTimeout() {
            return generationTimeout;
        }

        public void setGenerationTimeout(Duration generationTimeout) {
            this.generationTimeout = generationTimeout == null || generationTimeout.isZero() || generationTimeout.isNegative()
                    ? Duration.ofSeconds(30)
                    : generationTimeout;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getTopP() {
            return topP;
        }

        public void setTopP(double topP) {
            this.topP = topP;
        }

        public boolean isRequireSourceEvidence() {
            return requireSourceEvidence;
        }

        public void setRequireSourceEvidence(boolean requireSourceEvidence) {
            this.requireSourceEvidence = requireSourceEvidence;
        }

        public boolean isDistillationEnabled() {
            return distillationEnabled;
        }

        public void setDistillationEnabled(boolean distillationEnabled) {
            this.distillationEnabled = distillationEnabled;
        }

        public double getDistillationSimilarityThreshold() {
            return distillationSimilarityThreshold;
        }

        public void setDistillationSimilarityThreshold(double distillationSimilarityThreshold) {
            if (distillationSimilarityThreshold < 0.0d || distillationSimilarityThreshold > 1.0d) {
                throw new IllegalArgumentException(
                        "studio.chunking.blockify.distillation-similarity-threshold must be between 0 and 1");
            }
            this.distillationSimilarityThreshold = distillationSimilarityThreshold;
        }

        public String getDocumentType() {
            return documentType;
        }

        public void setDocumentType(String documentType) {
            String normalized = normalize(documentType);
            this.documentType = normalized == null ? "auto" : normalized.toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public int getMinAnswerChars() {
            return minAnswerChars;
        }

        public void setMinAnswerChars(int minAnswerChars) {
            this.minAnswerChars = positive(minAnswerChars, "studio.chunking.blockify.min-answer-chars");
        }

        public int getMinChunkChars() {
            return minChunkChars;
        }

        public void setMinChunkChars(int minChunkChars) {
            this.minChunkChars = positive(minChunkChars, "studio.chunking.blockify.min-chunk-chars");
        }

        public int getMaxChunkChars() {
            return maxChunkChars;
        }

        public void setMaxChunkChars(int maxChunkChars) {
            this.maxChunkChars = positive(maxChunkChars, "studio.chunking.blockify.max-chunk-chars");
        }

        public int getMinEvidenceChars() {
            return minEvidenceChars;
        }

        public void setMinEvidenceChars(int minEvidenceChars) {
            this.minEvidenceChars = positive(minEvidenceChars, "studio.chunking.blockify.min-evidence-chars");
        }

        public BlockifyPiiMaskingProperties getPiiMasking() {
            return piiMasking;
        }

        private int positive(int value, String propertyName) {
            if (value <= 0) {
                throw new IllegalArgumentException(propertyName + " must be greater than zero");
            }
            return value;
        }

        private String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    public static class BlockifyPiiMaskingProperties {
        private boolean enabled = true;
        private boolean required = true;
        private String analyzerUrl = "http://localhost:5002";
        private String anonymizerUrl = "http://localhost:5001";
        private String language = "ko";
        private double minScore = 0.5d;
        private Duration timeout = Duration.ofSeconds(5);
        private String onFailure = "fail";
        private Set<String> entityTypes = Set.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getAnalyzerUrl() {
            return analyzerUrl;
        }

        public void setAnalyzerUrl(String analyzerUrl) {
            this.analyzerUrl = normalizeLocal(analyzerUrl) == null ? "http://localhost:5002" : analyzerUrl.trim();
        }

        public String getAnonymizerUrl() {
            return anonymizerUrl;
        }

        public void setAnonymizerUrl(String anonymizerUrl) {
            this.anonymizerUrl = normalizeLocal(anonymizerUrl) == null ? "http://localhost:5001" : anonymizerUrl.trim();
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = normalizeLocal(language) == null ? "ko" : language.trim();
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            if (minScore < 0.0d || minScore > 1.0d) {
                throw new IllegalArgumentException("studio.chunking.blockify.pii-masking.min-score must be between 0 and 1");
            }
            this.minScore = minScore;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout == null || timeout.isZero() || timeout.isNegative()
                    ? Duration.ofSeconds(5)
                    : timeout;
        }

        public String getOnFailure() {
            return onFailure;
        }

        public void setOnFailure(String onFailure) {
            String normalized = normalizeLocal(onFailure);
            if (normalized == null) {
                this.onFailure = "fail";
                return;
            }
            normalized = normalized.toLowerCase(Locale.ROOT);
            if (!normalized.equals("fail") && !normalized.equals("fallback")) {
                throw new IllegalArgumentException(
                        "studio.chunking.blockify.pii-masking.on-failure must be fail or fallback");
            }
            this.onFailure = normalized;
        }

        public Set<String> getEntityTypes() {
            return entityTypes;
        }

        public void setEntityTypes(Set<String> entityTypes) {
            this.entityTypes = normalizeEntityTypes(entityTypes);
        }

        public void setEntityTypes(String entityTypes) {
            if (entityTypes == null || entityTypes.isBlank()) {
                this.entityTypes = Set.of();
                return;
            }
            this.entityTypes = normalizeEntityTypes(new LinkedHashSet<>(Arrays.asList(entityTypes.split(","))));
        }

        public boolean failPipelineOnFailure() {
            return required || "fail".equals(onFailure);
        }

        private Set<String> normalizeEntityTypes(Set<String> values) {
            if (values == null || values.isEmpty()) {
                return Set.of();
            }
            return values.stream()
                    .map(BlockifyPiiMaskingProperties::normalizeLocal)
                    .filter(value -> value != null)
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        private static String normalizeLocal(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    public static class KnowledgeBlockProperties {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
