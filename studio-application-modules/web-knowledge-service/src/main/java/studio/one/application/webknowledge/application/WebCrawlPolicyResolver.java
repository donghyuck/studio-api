package studio.one.application.webknowledge.application;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class WebCrawlPolicyResolver {

    public static final String POLICY_VERSION = "web-crawl-policy-v1";
    private static final int MAX_GLOBS_PER_SIDE = 20;
    private static final int MAX_GLOB_LENGTH = 200;
    private static final int MAX_QUERY_KEYS = 10;
    private static final Pattern SAFE_GLOB = Pattern.compile("[A-Za-z0-9_./*?\\-]+");
    private static final Pattern SAFE_QUERY_KEY = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");

    private final Limits limits;

    public WebCrawlPolicyResolver(Limits limits) {
        this.limits = limits == null ? Limits.defaults() : limits.validated();
    }

    public ResolvedWebCrawlPolicy resolve(
            WebKnowledgeCollectionMode collectionMode,
            WebCrawlPolicyInput input) {
        WebKnowledgeCollectionMode mode = collectionMode == null
                ? WebKnowledgeCollectionMode.SINGLE_PAGE
                : collectionMode;
        WebCrawlPolicyInput requested = input == null ? WebCrawlPolicyInput.defaults() : input;
        if (mode == WebKnowledgeCollectionMode.SINGLE_PAGE) {
            return singlePage();
        }
        int depth = bounded(requested.maxDepth(), limits.defaultMaxDepth(), limits.maximumDepth(),
                "WEB_CRAWL_MAX_DEPTH_INVALID");
        int pages = bounded(requested.maxPages(), limits.defaultMaxPages(), limits.maximumPages(),
                "WEB_CRAWL_MAX_PAGES_INVALID");
        int concurrency = bounded(
                requested.maxConcurrency(),
                limits.defaultMaxConcurrency(),
                limits.maximumConcurrency(),
                "WEB_CRAWL_MAX_CONCURRENCY_INVALID");
        List<String> allowedQueryKeys = queryKeys(requested.allowedQueryKeys());
        return new ResolvedWebCrawlPolicy(
                WebCrawlScope.from(requested.scope()),
                WebCrawlDiscoveryMode.from(requested.discoveryMode()),
                depth,
                pages,
                concurrency,
                limits.minDelayPerOrigin(),
                limits.maxTotalResponseBytes(),
                limits.maxTotalNormalizedChars(),
                limits.maxRunDuration(),
                false,
                false,
                allowedQueryKeys.isEmpty(),
                globs(requested.includePathGlobs(), "WEB_CRAWL_INCLUDE_GLOB_INVALID"),
                globs(requested.excludePathGlobs(), "WEB_CRAWL_EXCLUDE_GLOB_INVALID"),
                allowedQueryKeys,
                POLICY_VERSION);
    }

    private ResolvedWebCrawlPolicy singlePage() {
        return new ResolvedWebCrawlPolicy(
                WebCrawlScope.PATH_PREFIX,
                WebCrawlDiscoveryMode.LINKS_ONLY,
                0,
                1,
                1,
                limits.minDelayPerOrigin(),
                limits.maxTotalResponseBytes(),
                limits.maxTotalNormalizedChars(),
                limits.maxRunDuration(),
                false,
                false,
                true,
                List.of(),
                List.of(),
                List.of(),
                POLICY_VERSION);
    }

    private static int bounded(Integer requested, int defaultValue, int maximum, String code) {
        int value = requested == null ? defaultValue : requested;
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException(code);
        }
        return value;
    }

    private static List<String> globs(List<String> values, String code) {
        List<String> source = values == null ? List.of() : values;
        if (source.size() > MAX_GLOBS_PER_SIDE) {
            throw new IllegalArgumentException(code);
        }
        return source.stream()
                .map(WebCrawlPolicyResolver::normalize)
                .peek(value -> {
                    if (value == null || value.length() > MAX_GLOB_LENGTH || !SAFE_GLOB.matcher(value).matches()) {
                        throw new IllegalArgumentException(code);
                    }
                })
                .distinct()
                .toList();
    }

    private static List<String> queryKeys(List<String> values) {
        List<String> source = values == null ? List.of() : values;
        if (source.size() > MAX_QUERY_KEYS) {
            throw new IllegalArgumentException("WEB_CRAWL_QUERY_KEYS_INVALID");
        }
        return source.stream()
                .map(WebCrawlPolicyResolver::normalize)
                .map(value -> value == null ? null : value.toLowerCase(Locale.ROOT))
                .peek(value -> {
                    if (value == null || !SAFE_QUERY_KEY.matcher(value).matches()) {
                        throw new IllegalArgumentException("WEB_CRAWL_QUERY_KEYS_INVALID");
                    }
                })
                .distinct()
                .toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Limits(
            int defaultMaxDepth,
            int maximumDepth,
            int defaultMaxPages,
            int maximumPages,
            int defaultMaxConcurrency,
            int maximumConcurrency,
            Duration minDelayPerOrigin,
            long maxTotalResponseBytes,
            int maxTotalNormalizedChars,
            Duration maxRunDuration) {

        public static Limits defaults() {
            return new Limits(
                    2,
                    5,
                    50,
                    500,
                    2,
                    8,
                    Duration.ofMillis(500),
                    50L * 1024L * 1024L,
                    10_000_000,
                    Duration.ofMinutes(10));
        }

        Limits validated() {
            if (defaultMaxDepth < 1 || defaultMaxDepth > maximumDepth
                    || defaultMaxPages < 1 || defaultMaxPages > maximumPages
                    || defaultMaxConcurrency < 1 || defaultMaxConcurrency > maximumConcurrency
                    || minDelayPerOrigin == null || minDelayPerOrigin.isNegative()
                    || maxTotalResponseBytes <= 0L
                    || maxTotalNormalizedChars <= 0
                    || maxRunDuration == null || maxRunDuration.isNegative() || maxRunDuration.isZero()) {
                throw new IllegalArgumentException("WEB_CRAWL_LIMITS_INVALID");
            }
            return this;
        }
    }
}
