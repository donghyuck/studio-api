package studio.one.application.webknowledge.application;

import java.util.List;

public record WebKnowledgeSitePreviewView(
        String rootUrl,
        EffectivePolicy effectivePolicy,
        int candidateCount,
        List<Candidate> candidates,
        int excludedCount,
        List<ExcludedCandidate> excludedSamples,
        int queryParametersRemovedCount,
        boolean truncated,
        List<String> warnings) {

    public WebKnowledgeSitePreviewView {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        excludedSamples = excludedSamples == null ? List.of() : List.copyOf(excludedSamples);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record EffectivePolicy(
            String scope,
            String discoveryMode,
            int maxDepth,
            int maxPages,
            int maxConcurrency,
            long minDelayPerOriginMillis,
            boolean dropAllQuery,
            List<String> includePathGlobs,
            List<String> excludePathGlobs,
            List<String> allowedQueryKeys,
            String policyVersion) {

        public EffectivePolicy {
            includePathGlobs = List.copyOf(includePathGlobs);
            excludePathGlobs = List.copyOf(excludePathGlobs);
            allowedQueryKeys = List.copyOf(allowedQueryKeys);
        }
    }

    public record Candidate(String url, String host, String path, int depth, String discoveredBy) {
    }

    public record ExcludedCandidate(String host, String path, String reasonCode) {
    }
}
