package studio.one.platform.ai.autoconfigure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai.pipeline")
public class RagPipelineProperties {

    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private final CacheProperties cache = new CacheProperties();
    private final RetryProperties retry = new RetryProperties();

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
}
