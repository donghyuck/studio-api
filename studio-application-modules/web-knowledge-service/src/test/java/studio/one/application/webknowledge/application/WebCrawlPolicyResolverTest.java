package studio.one.application.webknowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebCrawlPolicyResolverTest {

    private final WebCrawlPolicyResolver resolver =
            new WebCrawlPolicyResolver(WebCrawlPolicyResolver.Limits.defaults());

    @Test
    void siteDefaultsAreBoundedAndDropQueries() {
        ResolvedWebCrawlPolicy policy =
                resolver.resolve(WebKnowledgeCollectionMode.SITE, WebCrawlPolicyInput.defaults());

        assertThat(policy.maxDepth()).isEqualTo(2);
        assertThat(policy.maxPages()).isEqualTo(50);
        assertThat(policy.maxConcurrency()).isEqualTo(2);
        assertThat(policy.dropAllQuery()).isTrue();
        assertThat(policy.allowExternalLinks()).isFalse();
        assertThat(policy.allowSubdomains()).isFalse();
    }

    @Test
    void allowedQueryKeysAreNormalizedAndChangeDropPolicy() {
        ResolvedWebCrawlPolicy policy = resolver.resolve(
                WebKnowledgeCollectionMode.SITE,
                new WebCrawlPolicyInput(
                        "PATH_PREFIX",
                        "SITEMAP_AND_LINKS",
                        3,
                        100,
                        2,
                        List.of("/docs/**"),
                        List.of("/docs/private/**"),
                        List.of("Lang", "lang")));

        assertThat(policy.allowedQueryKeys()).containsExactly("lang");
        assertThat(policy.dropAllQuery()).isFalse();
    }

    @Test
    void unsafeGlobAndExcessiveLimitsAreRejected() {
        assertThatThrownBy(() -> resolver.resolve(
                        WebKnowledgeCollectionMode.SITE,
                        new WebCrawlPolicyInput(
                                null, null, 99, null, null, List.of(), List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WEB_CRAWL_MAX_DEPTH_INVALID");

        assertThatThrownBy(() -> resolver.resolve(
                        WebKnowledgeCollectionMode.SITE,
                        new WebCrawlPolicyInput(
                                null, null, null, null, null, List.of("/docs/[a-z]"), List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WEB_CRAWL_INCLUDE_GLOB_INVALID");
    }
}
