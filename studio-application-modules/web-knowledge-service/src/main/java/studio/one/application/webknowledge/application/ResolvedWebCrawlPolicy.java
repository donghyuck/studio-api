package studio.one.application.webknowledge.application;

import java.time.Duration;
import java.util.List;

public record ResolvedWebCrawlPolicy(
        WebCrawlScope scope,
        WebCrawlDiscoveryMode discoveryMode,
        int maxDepth,
        int maxPages,
        int maxConcurrency,
        Duration minDelayPerOrigin,
        long maxTotalResponseBytes,
        int maxTotalNormalizedChars,
        Duration maxRunDuration,
        boolean allowSubdomains,
        boolean allowExternalLinks,
        boolean dropAllQuery,
        List<String> includePathGlobs,
        List<String> excludePathGlobs,
        List<String> allowedQueryKeys,
        String policyVersion) {

    public ResolvedWebCrawlPolicy {
        includePathGlobs = List.copyOf(includePathGlobs);
        excludePathGlobs = List.copyOf(excludePathGlobs);
        allowedQueryKeys = List.copyOf(allowedQueryKeys);
    }
}
