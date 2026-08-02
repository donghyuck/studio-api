package studio.one.application.webknowledge.web;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import studio.one.application.webknowledge.application.WebCrawlPolicyInput;

public record WebCrawlPolicyRequest(
        @Size(max = 32) String scope,
        @Size(max = 32) String discoveryMode,
        @Min(1) @Max(20) Integer maxDepth,
        @Min(1) @Max(5000) Integer maxPages,
        @Min(1) @Max(32) Integer maxConcurrency,
        @Size(max = 20) List<@Size(max = 200) String> includePathGlobs,
        @Size(max = 20) List<@Size(max = 200) String> excludePathGlobs,
        @Size(max = 10) List<@Size(max = 64) String> allowedQueryKeys) {

    WebCrawlPolicyInput toInput() {
        return new WebCrawlPolicyInput(
                scope,
                discoveryMode,
                maxDepth,
                maxPages,
                maxConcurrency,
                includePathGlobs,
                excludePathGlobs,
                allowedQueryKeys);
    }
}
