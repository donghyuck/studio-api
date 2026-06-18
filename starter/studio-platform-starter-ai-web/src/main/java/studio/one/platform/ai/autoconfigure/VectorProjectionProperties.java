package studio.one.platform.ai.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.ai.core.vector.visualization.ProjectionSamplingStrategy;

@ConfigurationProperties(prefix = "studio.ai.vector.projection")
public class VectorProjectionProperties {

    private int maxItems = 1_000;
    private int defaultSampleSize = 1_000;
    private ProjectionSamplingStrategy defaultSamplingStrategy = ProjectionSamplingStrategy.STRATIFIED;
    private Duration processingTimeout = Duration.ofMinutes(5);

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = Math.max(1, maxItems);
    }

    public int getDefaultSampleSize() {
        return defaultSampleSize;
    }

    public void setDefaultSampleSize(int defaultSampleSize) {
        this.defaultSampleSize = Math.max(1, defaultSampleSize);
    }

    public ProjectionSamplingStrategy getDefaultSamplingStrategy() {
        return defaultSamplingStrategy;
    }

    public void setDefaultSamplingStrategy(ProjectionSamplingStrategy defaultSamplingStrategy) {
        if (defaultSamplingStrategy != null) {
            this.defaultSamplingStrategy = defaultSamplingStrategy;
        }
    }

    public Duration getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(Duration processingTimeout) {
        if (processingTimeout != null && !processingTimeout.isNegative() && !processingTimeout.isZero()) {
            this.processingTimeout = processingTimeout;
        }
    }
}
