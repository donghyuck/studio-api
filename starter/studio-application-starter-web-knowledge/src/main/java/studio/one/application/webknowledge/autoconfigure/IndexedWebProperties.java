package studio.one.application.webknowledge.autoconfigure;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studio.ai.indexed-web")
public class IndexedWebProperties {

    private boolean enabled = true;
    private int maxSelectedSources = 10;
    private final Fetch fetch = new Fetch();
    private final Crawl crawl = new Crawl();
    private final Quota quota = new Quota();
    private final ContentSecurity contentSecurity = new ContentSecurity();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxSelectedSources() {
        return maxSelectedSources;
    }

    public void setMaxSelectedSources(int maxSelectedSources) {
        this.maxSelectedSources = maxSelectedSources;
    }

    public Fetch getFetch() {
        return fetch;
    }

    public Crawl getCrawl() {
        return crawl;
    }

    public Quota getQuota() {
        return quota;
    }

    public ContentSecurity getContentSecurity() {
        return contentSecurity;
    }

    public static class Fetch {
        private List<String> allowedSchemes = List.of("https");
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(15);
        private int maxResponseBytes = 5 * 1024 * 1024;
        private int maxNormalizedChars = 2_000_000;
        private int maxRedirects = 3;
        private boolean robotsEnabled = true;
        private String userAgent = "StudioOne-WebKnowledge/1.0";

        public List<String> getAllowedSchemes() { return allowedSchemes; }
        public void setAllowedSchemes(List<String> value) {
            if (value == null || value.size() != 1 || !"https".equalsIgnoreCase(value.get(0))) {
                throw new IllegalArgumentException("Indexed web fetch supports HTTPS only");
            }
            allowedSchemes = List.of("https");
        }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration value) { connectTimeout = value; }
        public Duration getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(Duration value) { requestTimeout = value; }
        public int getMaxResponseBytes() { return maxResponseBytes; }
        public void setMaxResponseBytes(int value) { maxResponseBytes = value; }
        public int getMaxNormalizedChars() { return maxNormalizedChars; }
        public void setMaxNormalizedChars(int value) { maxNormalizedChars = value; }
        public int getMaxRedirects() { return maxRedirects; }
        public void setMaxRedirects(int value) { maxRedirects = value; }
        public boolean isRobotsEnabled() { return robotsEnabled; }
        public void setRobotsEnabled(boolean value) { robotsEnabled = value; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String value) { userAgent = value; }
    }

    public static class ContentSecurity {
        private boolean piiRedactionEnabled = true;

        public boolean isPiiRedactionEnabled() {
            return piiRedactionEnabled;
        }

        public void setPiiRedactionEnabled(boolean piiRedactionEnabled) {
            this.piiRedactionEnabled = piiRedactionEnabled;
        }
    }

    public static class Crawl {
        private boolean siteCrawlEnabled;
        private int defaultMaxDepth = 2;
        private int maximumDepth = 5;
        private int defaultMaxPages = 50;
        private int maximumPages = 500;
        private int defaultMaxConcurrency = 2;
        private int maximumConcurrency = 8;
        private Duration minDelayPerOrigin = Duration.ofMillis(500);
        private long maxTotalResponseBytes = 50L * 1024L * 1024L;
        private int maxTotalNormalizedChars = 10_000_000;
        private Duration maxRunDuration = Duration.ofMinutes(10);
        private int maxActiveRunsGlobal = 2;
        private int maxActiveRunsPerWorkspace = 1;
        private int maxActiveRunsPerPrincipal = 1;

        public boolean isSiteCrawlEnabled() { return siteCrawlEnabled; }
        public void setSiteCrawlEnabled(boolean value) { siteCrawlEnabled = value; }
        public int getDefaultMaxDepth() { return defaultMaxDepth; }
        public void setDefaultMaxDepth(int value) { defaultMaxDepth = value; }
        public int getMaximumDepth() { return maximumDepth; }
        public void setMaximumDepth(int value) { maximumDepth = value; }
        public int getDefaultMaxPages() { return defaultMaxPages; }
        public void setDefaultMaxPages(int value) { defaultMaxPages = value; }
        public int getMaximumPages() { return maximumPages; }
        public void setMaximumPages(int value) { maximumPages = value; }
        public int getDefaultMaxConcurrency() { return defaultMaxConcurrency; }
        public void setDefaultMaxConcurrency(int value) { defaultMaxConcurrency = value; }
        public int getMaximumConcurrency() { return maximumConcurrency; }
        public void setMaximumConcurrency(int value) { maximumConcurrency = value; }
        public Duration getMinDelayPerOrigin() { return minDelayPerOrigin; }
        public void setMinDelayPerOrigin(Duration value) { minDelayPerOrigin = value; }
        public long getMaxTotalResponseBytes() { return maxTotalResponseBytes; }
        public void setMaxTotalResponseBytes(long value) { maxTotalResponseBytes = value; }
        public int getMaxTotalNormalizedChars() { return maxTotalNormalizedChars; }
        public void setMaxTotalNormalizedChars(int value) { maxTotalNormalizedChars = value; }
        public Duration getMaxRunDuration() { return maxRunDuration; }
        public void setMaxRunDuration(Duration value) { maxRunDuration = value; }
        public int getMaxActiveRunsGlobal() { return maxActiveRunsGlobal; }
        public void setMaxActiveRunsGlobal(int value) { maxActiveRunsGlobal = value; }
        public int getMaxActiveRunsPerWorkspace() { return maxActiveRunsPerWorkspace; }
        public void setMaxActiveRunsPerWorkspace(int value) { maxActiveRunsPerWorkspace = value; }
        public int getMaxActiveRunsPerPrincipal() { return maxActiveRunsPerPrincipal; }
        public void setMaxActiveRunsPerPrincipal(int value) { maxActiveRunsPerPrincipal = value; }
    }

    public static class Quota {
        private long maxSourcesPerWorkspace = 100;
        private long maxActivePagesPerWorkspace = 10_000;
        private long maxSnapshotUnitsPerWorkspace = 1_000_000_000L;

        public long getMaxSourcesPerWorkspace() { return maxSourcesPerWorkspace; }
        public void setMaxSourcesPerWorkspace(long value) { maxSourcesPerWorkspace = value; }
        public long getMaxActivePagesPerWorkspace() { return maxActivePagesPerWorkspace; }
        public void setMaxActivePagesPerWorkspace(long value) { maxActivePagesPerWorkspace = value; }
        public long getMaxSnapshotUnitsPerWorkspace() { return maxSnapshotUnitsPerWorkspace; }
        public void setMaxSnapshotUnitsPerWorkspace(long value) { maxSnapshotUnitsPerWorkspace = value; }
    }
}
