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
}
