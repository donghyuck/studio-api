package studio.one.platform.chunking.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

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
        private String promptVersion = "blockify-v1";
        private String generatorModel = "heuristic-blockify-v1";
        private double temperature = 0.0d;
        private double topP = 1.0d;
        private boolean requireSourceEvidence = true;

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

        private int positive(int value, String propertyName) {
            if (value <= 0) {
                throw new IllegalArgumentException(propertyName + " must be greater than zero");
            }
            return value;
        }
    }
}
