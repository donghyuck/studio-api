package studio.one.application.webknowledge.application;

import java.util.List;

public record WebCrawlPolicyInput(
        String scope,
        String discoveryMode,
        Integer maxDepth,
        Integer maxPages,
        Integer maxConcurrency,
        List<String> includePathGlobs,
        List<String> excludePathGlobs,
        List<String> allowedQueryKeys) {

    public WebCrawlPolicyInput {
        includePathGlobs = includePathGlobs == null ? List.of() : List.copyOf(includePathGlobs);
        excludePathGlobs = excludePathGlobs == null ? List.of() : List.copyOf(excludePathGlobs);
        allowedQueryKeys = allowedQueryKeys == null ? List.of() : List.copyOf(allowedQueryKeys);
    }

    public static WebCrawlPolicyInput defaults() {
        return new WebCrawlPolicyInput(null, null, null, null, null, List.of(), List.of(), List.of());
    }
}
