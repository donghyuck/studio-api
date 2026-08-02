package studio.one.application.webknowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class WebKnowledgeSitePreviewServiceTest {

    @Test
    void previewsOnlyScopedNormalizedCandidatesWithoutPersistingBodies() {
        WebPageFetchPort fetchPort = mock(WebPageFetchPort.class);
        WebSiteDiscoveryPort discovery = mock(WebSiteDiscoveryPort.class);
        URI seed = URI.create("https://example.org/docs/");
        byte[] html = "<html><body>docs</body></html>".getBytes(StandardCharsets.UTF_8);
        when(fetchPort.fetch(
                eq(seed),
                any(WebPageFetchPort.ConditionalRequest.class),
                eq(WebPageFetchPort.ResourceKind.PAGE)))
                .thenReturn(new WebPageFetchPort.FetchResult(
                        seed, seed, 200, "text/html", html, null, null, Instant.now(), false));
        when(discovery.links(html, seed)).thenReturn(List.of(
                "/docs/guide?tracking=secret",
                "/docs/api",
                "/outside",
                "https://other.example/docs"));

        WebKnowledgeSitePreviewService service = new WebKnowledgeSitePreviewService(
                fetchPort,
                discovery,
                new WebSitemapParser(),
                new WebCrawlUrlPolicy(),
                new WebCrawlPolicyResolver(WebCrawlPolicyResolver.Limits.defaults()));
        WebCrawlPolicyInput policy = new WebCrawlPolicyInput(
                "PATH_PREFIX", "LINKS_ONLY", 2, 50, 2, List.of(), List.of(), List.of());

        WebKnowledgeSitePreviewView result = service.preview(seed.toString(), policy);

        assertEquals(3, result.candidateCount());
        assertEquals(List.of(
                        "https://example.org/docs/",
                        "https://example.org/docs/guide",
                        "https://example.org/docs/api"),
                result.candidates().stream().map(WebKnowledgeSitePreviewView.Candidate::url).toList());
        assertEquals(2, result.excludedCount());
        assertEquals(1, result.queryParametersRemovedCount());
        assertTrue(result.warnings().contains("PREVIEW_FIRST_HOP_ONLY"));
        assertTrue(result.excludedSamples().stream()
                .allMatch(candidate -> candidate.path() == null || !candidate.path().contains("?")));
    }

    @Test
    void rejectsPreviewWhenSiteCollectionFeatureIsDisabled() {
        WebKnowledgeSitePreviewService service = new WebKnowledgeSitePreviewService(
                mock(WebPageFetchPort.class),
                mock(WebSiteDiscoveryPort.class),
                new WebSitemapParser(),
                new WebCrawlUrlPolicy(),
                new WebCrawlPolicyResolver(WebCrawlPolicyResolver.Limits.defaults()),
                false);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.preview(
                        "https://example.org/docs/",
                        WebCrawlPolicyInput.defaults(),
                        "tester"));

        assertEquals("WEB_SITE_CRAWL_DISABLED", error.getMessage());
    }
}
