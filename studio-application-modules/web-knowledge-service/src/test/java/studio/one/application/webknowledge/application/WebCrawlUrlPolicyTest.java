package studio.one.application.webknowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebCrawlUrlPolicyTest {

    private final WebCrawlUrlPolicy policy = new WebCrawlUrlPolicy();

    @Test
    void acceptsOnlySameOriginPathPrefixAndCanonicalizesAllowedQuery() {
        URI seed = URI.create("https://1.1.1.1/docs/");
        ResolvedWebCrawlPolicy resolved = resolved(
                WebCrawlScope.PATH_PREFIX,
                List.of("/docs/**"),
                List.of("/docs/private/**"),
                List.of("lang", "page"));

        assertThat(policy.candidate(
                        seed,
                        seed,
                        "/docs/guide?page=2&utm_source=test&lang=ko",
                        resolved))
                .contains(URI.create("https://1.1.1.1/docs/guide?lang=ko&page=2"));
        assertThat(policy.candidate(seed, seed, "/docs/private/key", resolved)).isEmpty();
        assertThat(policy.candidate(seed, seed, "/outside", resolved)).isEmpty();
        assertThat(policy.candidate(seed, seed, "https://8.8.8.8/docs/guide", resolved)).isEmpty();
    }

    @Test
    void dropsAllQueryParametersWhenNoAllowlistExists() {
        URI seed = URI.create("https://1.1.1.1/");
        assertThat(policy.candidate(
                        seed,
                        seed,
                        "/article?id=10&utm_source=test#section",
                        resolved(WebCrawlScope.SAME_ORIGIN, List.of(), List.of(), List.of())))
                .contains(URI.create("https://1.1.1.1/article"));
    }

    private static ResolvedWebCrawlPolicy resolved(
            WebCrawlScope scope,
            List<String> include,
            List<String> exclude,
            List<String> queryKeys) {
        return new ResolvedWebCrawlPolicy(
                scope,
                WebCrawlDiscoveryMode.SITEMAP_AND_LINKS,
                2,
                50,
                2,
                java.time.Duration.ofMillis(500),
                50L * 1024L * 1024L,
                10_000_000,
                java.time.Duration.ofMinutes(10),
                false,
                false,
                queryKeys.isEmpty(),
                include,
                exclude,
                queryKeys,
                WebCrawlPolicyResolver.POLICY_VERSION);
    }
}
