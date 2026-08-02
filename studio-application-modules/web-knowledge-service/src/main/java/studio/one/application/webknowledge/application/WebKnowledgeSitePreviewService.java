package studio.one.application.webknowledge.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import studio.one.application.webknowledge.infrastructure.web.WebUrlPolicy;

/**
 * Performs a bounded, read-only first-hop discovery. Preview never persists page
 * bodies and never bypasses the production fetch transport or URL scope policy.
 */
public final class WebKnowledgeSitePreviewService {

    private static final int MAX_PREVIEW_CANDIDATES = 200;
    private static final int MAX_EXCLUDED_SAMPLES = 10;
    private static final int MAX_PREVIEW_SITEMAPS = 5;
    private static final Semaphore GLOBAL_PREVIEW_LIMIT = new Semaphore(2);
    private static final Set<String> ACTIVE_PRINCIPALS = ConcurrentHashMap.newKeySet();

    private final WebPageFetchPort fetchPort;
    private final WebSiteDiscoveryPort discovery;
    private final WebSitemapParser sitemapParser;
    private final WebCrawlUrlPolicy urlPolicy;
    private final WebCrawlPolicyResolver policyResolver;
    private final boolean enabled;

    public WebKnowledgeSitePreviewService(
            WebPageFetchPort fetchPort,
            WebSiteDiscoveryPort discovery,
            WebSitemapParser sitemapParser,
            WebCrawlUrlPolicy urlPolicy,
            WebCrawlPolicyResolver policyResolver) {
        this(fetchPort, discovery, sitemapParser, urlPolicy, policyResolver, true);
    }

    public WebKnowledgeSitePreviewService(
            WebPageFetchPort fetchPort,
            WebSiteDiscoveryPort discovery,
            WebSitemapParser sitemapParser,
            WebCrawlUrlPolicy urlPolicy,
            WebCrawlPolicyResolver policyResolver,
            boolean enabled) {
        this.fetchPort = fetchPort;
        this.discovery = discovery;
        this.sitemapParser = sitemapParser;
        this.urlPolicy = urlPolicy;
        this.policyResolver = policyResolver;
        this.enabled = enabled;
    }

    public WebKnowledgeSitePreviewView preview(String url, WebCrawlPolicyInput input) {
        return preview(url, input, "anonymous");
    }

    public WebKnowledgeSitePreviewView preview(
            String url,
            WebCrawlPolicyInput input,
            String principal) {
        if (!enabled) {
            throw new IllegalStateException("WEB_SITE_CRAWL_DISABLED");
        }
        String principalKey = principal == null || principal.isBlank() ? "anonymous" : principal.trim();
        if (!GLOBAL_PREVIEW_LIMIT.tryAcquire()) {
            throw new IllegalStateException("WEB_SITE_PREVIEW_BUSY");
        }
        if (!ACTIVE_PRINCIPALS.add(principalKey)) {
            GLOBAL_PREVIEW_LIMIT.release();
            throw new IllegalStateException("WEB_SITE_PREVIEW_BUSY");
        }
        try {
            return executePreview(url, input);
        } finally {
            ACTIVE_PRINCIPALS.remove(principalKey);
            GLOBAL_PREVIEW_LIMIT.release();
        }
    }

    private WebKnowledgeSitePreviewView executePreview(String url, WebCrawlPolicyInput input) {
        URI seed = WebUrlPolicy.normalize(url);
        ResolvedWebCrawlPolicy policy =
                policyResolver.resolve(WebKnowledgeCollectionMode.SITE, input);
        PreviewAccumulator accumulator = new PreviewAccumulator(
                Math.min(policy.maxPages(), MAX_PREVIEW_CANDIDATES));

        WebOriginRequestScheduler.await(seed, policy.minDelayPerOrigin());
        WebPageFetchPort.FetchResult seedResult = fetchPort.fetch(
                seed,
                WebPageFetchPort.ConditionalRequest.none(),
                WebPageFetchPort.ResourceKind.PAGE);
        URI effectiveSeed = urlPolicy.candidate(seed, seed, seedResult.finalUri().toString(), policy)
                .orElseThrow(() -> new IllegalStateException("WEB_CRAWL_REDIRECT_OUT_OF_SCOPE"));
        accumulator.accept(effectiveSeed, 0, "SEED", effectiveSeed.getRawQuery() != null);

        if (policy.discoveryMode() != WebCrawlDiscoveryMode.SITEMAP_ONLY) {
            for (String link : discovery.links(seedResult.body(), effectiveSeed)) {
                acceptCandidate(accumulator, seed, effectiveSeed, link, policy, 1, "HTML_LINK");
            }
        }
        if (policy.discoveryMode() != WebCrawlDiscoveryMode.LINKS_ONLY) {
            discoverSitemaps(seed, policy, accumulator);
        }

        List<String> warnings = new ArrayList<>();
        warnings.add("PREVIEW_FIRST_HOP_ONLY");
        if (accumulator.truncated) {
            warnings.add("PREVIEW_CANDIDATE_LIMIT_REACHED");
        }
        return new WebKnowledgeSitePreviewView(
                displayUri(effectiveSeed),
                effectivePolicy(policy),
                accumulator.accepted.size(),
                List.copyOf(accumulator.accepted.values()),
                accumulator.excludedCount,
                List.copyOf(accumulator.excludedSamples),
                accumulator.queryParametersRemoved,
                accumulator.truncated,
                warnings);
    }

    private void discoverSitemaps(
            URI seed,
            ResolvedWebCrawlPolicy policy,
            PreviewAccumulator accumulator) {
        ArrayDeque<URI> sitemapQueue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        URI robotsUri = origin(seed, "/robots.txt");
        try {
            WebOriginRequestScheduler.await(robotsUri, policy.minDelayPerOrigin());
            WebPageFetchPort.FetchResult robots = fetchPort.fetch(
                    robotsUri,
                    WebPageFetchPort.ConditionalRequest.none(),
                    WebPageFetchPort.ResourceKind.ROBOTS);
            if (robots.statusCode() == 200) {
                new String(robots.body(), StandardCharsets.UTF_8).lines()
                        .map(String::trim)
                        .filter(line -> line.regionMatches(true, 0, "sitemap:", 0, "sitemap:".length()))
                        .map(line -> line.substring(line.indexOf(':') + 1).trim())
                        .map(value -> sameOrigin(seed, value))
                        .flatMap(java.util.Optional::stream)
                        .forEach(sitemapQueue::add);
            }
        } catch (RuntimeException ignored) {
            // A missing or unavailable robots document does not invalidate seed preview.
        }
        sitemapQueue.add(origin(seed, "/sitemap.xml"));

        while (!sitemapQueue.isEmpty() && visited.size() < MAX_PREVIEW_SITEMAPS
                && !accumulator.truncated) {
            URI sitemap = sitemapQueue.removeFirst();
            if (!visited.add(sitemap.toString())) {
                continue;
            }
            try {
                WebOriginRequestScheduler.await(sitemap, policy.minDelayPerOrigin());
                WebPageFetchPort.FetchResult fetched = fetchPort.fetch(
                        sitemap,
                        WebPageFetchPort.ConditionalRequest.none(),
                        WebPageFetchPort.ResourceKind.SITEMAP);
                if (fetched.statusCode() == 404) {
                    continue;
                }
                WebSitemapParser.ParsedSitemap parsed = sitemapParser.parse(fetched.body());
                for (String page : parsed.pageLocations()) {
                    acceptCandidate(accumulator, seed, sitemap, page, policy, 1, "SITEMAP");
                    if (accumulator.truncated) {
                        break;
                    }
                }
                for (String nested : parsed.sitemapLocations()) {
                    sameOrigin(seed, nested).ifPresent(sitemapQueue::add);
                }
            } catch (RuntimeException ignored) {
                // Supplemental discovery failure is returned as a partial first-hop preview.
            }
        }
    }

    private void acceptCandidate(
            PreviewAccumulator accumulator,
            URI seed,
            URI base,
            String raw,
            ResolvedWebCrawlPolicy policy,
            int depth,
            String discoveredBy) {
        URI rawUri;
        try {
            rawUri = base.resolve(raw);
        } catch (RuntimeException ex) {
            accumulator.exclude(null, null, "INVALID_URL");
            return;
        }
        var accepted = urlPolicy.candidate(seed, base, raw, policy);
        if (accepted.isEmpty()) {
            accumulator.exclude(rawUri.getHost(), rawUri.getPath(), "OUT_OF_SCOPE_OR_FILTERED");
            return;
        }
        accumulator.accept(accepted.get(), depth, discoveredBy, rawUri.getRawQuery() != null);
    }

    private static WebKnowledgeSitePreviewView.EffectivePolicy effectivePolicy(
            ResolvedWebCrawlPolicy policy) {
        return new WebKnowledgeSitePreviewView.EffectivePolicy(
                policy.scope().name(),
                policy.discoveryMode().name(),
                policy.maxDepth(),
                policy.maxPages(),
                policy.maxConcurrency(),
                policy.minDelayPerOrigin().toMillis(),
                policy.dropAllQuery(),
                policy.includePathGlobs(),
                policy.excludePathGlobs(),
                policy.allowedQueryKeys(),
                policy.policyVersion());
    }

    private static URI origin(URI seed, String path) {
        return URI.create(seed.getScheme() + "://" + seed.getAuthority() + path);
    }

    private static java.util.Optional<URI> sameOrigin(URI seed, String value) {
        try {
            URI candidate = seed.resolve(value);
            return seed.getScheme().equalsIgnoreCase(candidate.getScheme())
                            && seed.getHost().equalsIgnoreCase(candidate.getHost())
                            && effectivePort(seed) == effectivePort(candidate)
                    ? java.util.Optional.of(candidate)
                    : java.util.Optional.empty();
        } catch (RuntimeException ex) {
            return java.util.Optional.empty();
        }
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : 443;
    }

    private static String displayUri(URI uri) {
        try {
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null).toString();
        } catch (Exception ex) {
            throw new IllegalStateException("WEB_CRAWL_URL_INVALID", ex);
        }
    }

    private static final class PreviewAccumulator {
        private final int limit;
        private final Map<String, WebKnowledgeSitePreviewView.Candidate> accepted = new LinkedHashMap<>();
        private final List<WebKnowledgeSitePreviewView.ExcludedCandidate> excludedSamples = new ArrayList<>();
        private int excludedCount;
        private int queryParametersRemoved;
        private boolean truncated;

        private PreviewAccumulator(int limit) {
            this.limit = Math.max(1, limit);
        }

        private void accept(URI uri, int depth, String discoveredBy, boolean queryRemoved) {
            String key = uri.toString();
            if (accepted.containsKey(key)) {
                return;
            }
            if (accepted.size() >= limit) {
                truncated = true;
                return;
            }
            if (queryRemoved) {
                queryParametersRemoved++;
            }
            accepted.put(key, new WebKnowledgeSitePreviewView.Candidate(
                    displayUri(uri),
                    uri.getHost(),
                    uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath(),
                    depth,
                    discoveredBy));
        }

        private void exclude(String host, String path, String reasonCode) {
            excludedCount++;
            if (excludedSamples.size() >= MAX_EXCLUDED_SAMPLES) {
                return;
            }
            excludedSamples.add(new WebKnowledgeSitePreviewView.ExcludedCandidate(
                    host,
                    path == null || path.isBlank() ? "/" : path,
                    reasonCode));
        }
    }
}
