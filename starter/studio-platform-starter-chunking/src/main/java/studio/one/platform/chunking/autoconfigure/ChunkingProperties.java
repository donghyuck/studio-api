package studio.one.platform.chunking.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.chunking.core.ChunkingStrategyType;

@ConfigurationProperties(prefix = "studio.chunking")
public class ChunkingProperties {

    private boolean enabled = true;

    private String strategy = "recursive";

    private int maxSize = 800;

    private int overlap = 100;

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

    public ChunkingStrategyType strategyType() {
        return ChunkingStrategyType.from(strategy);
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
    }

    public int effectiveMaxSize(int requestedMaxSize) {
        return requestedMaxSize <= 0 ? maxSize : requestedMaxSize;
    }

    public int effectiveOverlap(int requestedOverlap) {
        return requestedOverlap < 0 ? overlap : requestedOverlap;
    }
}
