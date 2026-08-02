package studio.one.application.webknowledge.infrastructure.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;

import studio.one.application.webknowledge.application.WebPageFetchPolicy;
import studio.one.application.webknowledge.application.WebPageFetchPort;
import studio.one.application.webknowledge.application.RobotsPolicyPort;

public final class SafeHttpWebPageFetcher implements WebPageFetchPort, AutoCloseable {

    private static final Set<String> ALLOWED_TYPES = Set.of("text/html", "application/xhtml+xml");
    private static final Set<String> ROBOTS_TYPES = Set.of("text/plain", "text/html");
    private static final Set<String> SITEMAP_TYPES = Set.of(
            "application/xml",
            "text/xml",
            "application/rss+xml",
            "application/xhtml+xml",
            "text/plain",
            "application/gzip",
            "application/x-gzip");

    private final CloseableHttpClient client;
    private final WebPageFetchPolicy policy;
    private final Clock clock;
    private final RobotsPolicyPort robotsPolicy;
    private final ConcurrentHashMap<String, RobotsCacheEntry> robotsCache = new ConcurrentHashMap<>();

    public SafeHttpWebPageFetcher(WebPageFetchPolicy policy) {
        this(policy, Clock.systemUTC());
    }

    SafeHttpWebPageFetcher(WebPageFetchPolicy policy, Clock clock) {
        this(policy, clock, httpClient(policy == null ? WebPageFetchPolicy.defaults() : policy));
    }

    SafeHttpWebPageFetcher(
            WebPageFetchPolicy policy,
            Clock clock,
            CloseableHttpClient client) {
        this(policy, clock, client, new StandardRobotsPolicy());
    }

    SafeHttpWebPageFetcher(
            WebPageFetchPolicy policy,
            Clock clock,
            CloseableHttpClient client,
            RobotsPolicyPort robotsPolicy) {
        this.policy = policy == null ? WebPageFetchPolicy.defaults() : policy;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.robotsPolicy = robotsPolicy == null ? new StandardRobotsPolicy() : robotsPolicy;
    }

    private static CloseableHttpClient httpClient(WebPageFetchPolicy policy) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(policy.connectTimeout()))
                .build();
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new PublicOnlyDnsResolver())
                .setDefaultConnectionConfig(connectionConfig)
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(policy.connectTimeout()))
                .setResponseTimeout(Timeout.of(policy.requestTimeout()))
                .setRedirectsEnabled(false)
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableContentCompression()
                .build();
    }

    @Override
    public FetchResult fetch(URI uri, ConditionalRequest conditionalRequest) {
        return fetch(uri, conditionalRequest, ResourceKind.PAGE);
    }

    @Override
    public FetchResult fetch(
            URI uri,
            ConditionalRequest conditionalRequest,
            ResourceKind resourceKind) {
        ResourceKind kind = resourceKind == null ? ResourceKind.PAGE : resourceKind;
        URI current = WebUrlPolicy.normalize(uri == null ? null : uri.toString());
        ConditionalRequest conditional = conditionalRequest == null ? ConditionalRequest.none() : conditionalRequest;
        for (int redirect = 0; redirect <= policy.maxRedirects(); redirect++) {
            WebUrlPolicy.assertPublicHost(current);
            if (kind == ResourceKind.PAGE) {
                assertRobotsAllowed(current);
            }
            HttpGet request = new HttpGet(current);
            request.setHeader("Accept", accept(kind));
            request.setHeader("User-Agent", policy.userAgent());
            if (conditional.etag() != null && !conditional.etag().isBlank()) {
                request.setHeader("If-None-Match", conditional.etag());
            }
            if (conditional.lastModified() != null && !conditional.lastModified().isBlank()) {
                request.setHeader("If-Modified-Since", conditional.lastModified());
            }
            int maxBytes = maxBytes(kind);
            TransportResponse response = send(request, maxBytes, responseTooLargeCode(kind));
            int status = response.status();
            if (status == 304) {
                return new FetchResult(uri, current, status, null, new byte[0],
                        header(response, "etag"), header(response, "last-modified"), clock.instant(), true, 0L, 0L);
            }
            if (status >= 300 && status < 400) {
                if (redirect == policy.maxRedirects()) {
                    throw new WebPageFetchException("TOO_MANY_REDIRECTS");
                }
                String location = header(response, "location");
                if (location == null) {
                    throw new WebPageFetchException("INVALID_REDIRECT");
                }
                URI redirected = WebUrlPolicy.normalize(current.resolve(location).toString());
                if (!sameOrigin(current, redirected)) {
                    throw new WebPageFetchException("CROSS_ORIGIN_REDIRECT_NOT_ALLOWED");
                }
                current = redirected;
                continue;
            }
            if ((kind == ResourceKind.ROBOTS || kind == ResourceKind.SITEMAP) && status == 404) {
                if (kind == ResourceKind.ROBOTS) {
                    cacheRobots(current, "");
                }
                return new FetchResult(uri, current, status, null, new byte[0],
                        null, null, clock.instant(), false, 0L, 0L);
            }
            if (status < 200 || status >= 300) {
                throw new WebPageFetchException(kind == ResourceKind.PAGE
                        ? (status == 404 || status == 410 ? "PAGE_REMOVED" : "HTTP_STATUS_REJECTED")
                        : kind == ResourceKind.ROBOTS ? "ROBOTS_UNAVAILABLE" : "SITEMAP_UNAVAILABLE");
            }
            String contentType = normalizedContentType(header(response, "content-type"));
            if (!allowedTypes(kind).contains(contentType)) {
                throw new WebPageFetchException("CONTENT_TYPE_NOT_ALLOWED");
            }
            byte[] compressedBody = response.body();
            byte[] body = decompressIfRequired(compressedBody, response.contentEncoding(), maxBytes,
                    responseTooLargeCode(kind));
            if (kind == ResourceKind.PAGE) {
                assertPageCollectionAllowed(response, body);
            } else if (kind == ResourceKind.ROBOTS) {
                cacheRobots(current, new String(body, StandardCharsets.UTF_8));
            }
            return new FetchResult(uri, current, status, contentType, body,
                    header(response, "etag"), header(response, "last-modified"), clock.instant(), false,
                    compressedBody.length, body.length);
        }
        throw new WebPageFetchException("TOO_MANY_REDIRECTS");
    }

    private static String accept(ResourceKind kind) {
        return switch (kind) {
            case PAGE -> "text/html,application/xhtml+xml";
            case ROBOTS -> "text/plain";
            case SITEMAP -> "application/xml,text/xml,application/rss+xml,text/plain,application/gzip";
        };
    }

    private int maxBytes(ResourceKind kind) {
        return switch (kind) {
            case PAGE -> policy.maxResponseBytes();
            case ROBOTS -> Math.min(policy.maxResponseBytes(), 256 * 1024);
            case SITEMAP -> Math.min(policy.maxResponseBytes(), 2 * 1024 * 1024);
        };
    }

    private static String responseTooLargeCode(ResourceKind kind) {
        return switch (kind) {
            case PAGE -> "RESPONSE_TOO_LARGE";
            case ROBOTS -> "ROBOTS_RESPONSE_TOO_LARGE";
            case SITEMAP -> "SITEMAP_RESPONSE_TOO_LARGE";
        };
    }

    private static Set<String> allowedTypes(ResourceKind kind) {
        return switch (kind) {
            case PAGE -> ALLOWED_TYPES;
            case ROBOTS -> ROBOTS_TYPES;
            case SITEMAP -> SITEMAP_TYPES;
        };
    }

    private void assertRobotsAllowed(URI target) {
        if (!policy.robotsEnabled() || "/robots.txt".equals(target.getPath())) {
            return;
        }
        try {
            RobotsCacheEntry cached = robotsCache.get(originKey(target));
            if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
                if (!robotsPolicy.isAllowed(cached.text(), policy.userAgent(), pathAndQuery(target))) {
                    throw new WebPageFetchException("ROBOTS_DISALLOWED");
                }
                return;
            }
            URI robots = new URI("https", null, target.getHost(), target.getPort(), "/robots.txt", null, null);
            WebUrlPolicy.assertPublicHost(robots);
            HttpGet request = new HttpGet(robots);
            request.setHeader("Accept", "text/plain");
            request.setHeader("User-Agent", policy.userAgent());
            int robotsLimit = Math.min(policy.maxResponseBytes(), 256 * 1024);
            TransportResponse response = send(request, robotsLimit, "ROBOTS_RESPONSE_TOO_LARGE");
            if (response.status() == 404) {
                cacheRobots(robots, "");
                return;
            }
            if (response.status() < 200 || response.status() >= 300) {
                throw new WebPageFetchException("ROBOTS_UNAVAILABLE");
            }
            String robotsText = new String(response.body(), StandardCharsets.UTF_8);
            cacheRobots(robots, robotsText);
            if (!robotsPolicy.isAllowed(robotsText, policy.userAgent(), pathAndQuery(target))) {
                throw new WebPageFetchException("ROBOTS_DISALLOWED");
            }
        } catch (WebPageFetchException ex) {
            if ("ROBOTS_DISALLOWED".equals(ex.errorCode())
                    || "ROBOTS_RESPONSE_TOO_LARGE".equals(ex.errorCode())
                    || "ROBOTS_UNAVAILABLE".equals(ex.errorCode())) {
                throw ex;
            }
            throw new WebPageFetchException("ROBOTS_UNAVAILABLE", ex);
        } catch (Exception ignored) {
            throw new WebPageFetchException("ROBOTS_UNAVAILABLE");
        }
    }

    private void cacheRobots(URI uri, String text) {
        robotsCache.put(
                originKey(uri),
                new RobotsCacheEntry(text == null ? "" : text, clock.instant().plus(Duration.ofHours(1))));
    }

    private static String originKey(URI uri) {
        return uri.getHost().toLowerCase(Locale.ROOT) + ":" + effectivePort(uri);
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private void assertPageCollectionAllowed(TransportResponse response, byte[] body) {
        String header = header(response, "x-robots-tag");
        if (containsCollectionProhibition(header)) {
            throw new WebPageFetchException("PAGE_COLLECTION_DISALLOWED");
        }
        String meta = Jsoup.parse(new String(body, StandardCharsets.UTF_8))
                .select("meta[name=robots], meta[name=googlebot]")
                .stream()
                .map(element -> element.attr("content"))
                .reduce("", (left, right) -> left + "," + right);
        if (containsCollectionProhibition(meta)) {
            throw new WebPageFetchException("PAGE_COLLECTION_DISALLOWED");
        }
    }

    private static boolean containsCollectionProhibition(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("noindex")
                || normalized.contains("noarchive")
                || normalized.contains("nosnippet");
    }

    private TransportResponse send(HttpGet request, int maxBytes, String responseTooLargeCode) {
        try {
            return client.execute(
                    request,
                    response -> transportResponse(response, maxBytes, responseTooLargeCode));
        } catch (IOException ex) {
            WebPageFetchException policyFailure = findPolicyFailure(ex);
            if (policyFailure != null) {
                throw policyFailure;
            }
            throw new WebPageFetchException("FETCH_FAILED", ex);
        }
    }

    private TransportResponse transportResponse(
            ClassicHttpResponse response,
            int maxBytes,
            String responseTooLargeCode) throws IOException {
        int status = response.getCode();
        byte[] body = status >= 200 && status < 300 && status != 304
                ? readBounded(response.getEntity(), maxBytes, responseTooLargeCode)
                : new byte[0];
        return new TransportResponse(
                status,
                header(response, "location"),
                header(response, "content-type"),
                header(response, "etag"),
                header(response, "last-modified"),
                header(response, "x-robots-tag"),
                header(response, "content-encoding"),
                body);
    }

    private byte[] readBounded(HttpEntity entity, int maxBytes, String responseTooLargeCode) throws IOException {
        if (entity == null) {
            return new byte[0];
        }
        try (InputStream body = entity.getContent()) {
            byte[] value = body.readNBytes(maxBytes + 1);
            if (value.length > maxBytes) {
                throw new WebPageFetchException(responseTooLargeCode);
            }
            return value;
        }
    }

    private static String normalizedContentType(String value) {
        if (value == null) {
            return "";
        }
        int separator = value.indexOf(';');
        return (separator < 0 ? value : value.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI value) {
        return value.getPort() < 0 ? 443 : value.getPort();
    }

    private static String header(ClassicHttpResponse response, String name) {
        var header = response.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    private static String header(TransportResponse response, String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "location" -> response.location();
            case "content-type" -> response.contentType();
            case "etag" -> response.etag();
            case "last-modified" -> response.lastModified();
            case "x-robots-tag" -> response.robotsTag();
            case "content-encoding" -> response.contentEncoding();
            default -> null;
        };
    }

    private static byte[] decompressIfRequired(
            byte[] body,
            String contentEncoding,
            int maxBytes,
            String tooLargeCode) {
        if (body == null || body.length == 0) {
            return new byte[0];
        }
        boolean gzip = contentEncoding != null && contentEncoding.toLowerCase(Locale.ROOT).contains("gzip");
        if (!gzip && body.length >= 2) {
            gzip = Byte.toUnsignedInt(body[0]) == 0x1f && Byte.toUnsignedInt(body[1]) == 0x8b;
        }
        if (!gzip) {
            return body;
        }
        try (GZIPInputStream input = new GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
            byte[] expanded = input.readNBytes(maxBytes + 1);
            if (expanded.length > maxBytes) {
                throw new WebPageFetchException(tooLargeCode);
            }
            return expanded;
        } catch (WebPageFetchException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new WebPageFetchException("CONTENT_DECOMPRESSION_FAILED", ex);
        }
    }

    private static WebPageFetchException findPolicyFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof WebPageFetchException fetchException) {
                return fetchException;
            }
            current = current.getCause();
        }
        return null;
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (IOException ignored) {
            // best effort during application shutdown
        }
    }

    private record TransportResponse(
            int status,
            String location,
            String contentType,
            String etag,
            String lastModified,
            String robotsTag,
            String contentEncoding,
            byte[] body) {
    }

    private record RobotsCacheEntry(String text, Instant expiresAt) {
    }
}
