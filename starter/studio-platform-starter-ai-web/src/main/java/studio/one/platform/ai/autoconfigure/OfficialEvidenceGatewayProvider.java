package studio.one.platform.ai.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.rag.external.ExternalEvidence;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceProvider;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceRequest;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceSourceType;

/**
 * Calls an operator-controlled gateway that returns exact excerpts from
 * allowlisted official sources.
 */
final class OfficialEvidenceGatewayProvider implements ExternalEvidenceProvider {

    private static final String PROVIDER_ID = "official-evidence-gateway";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;
    private final Set<String> sourceAllowedHosts;
    private final java.time.Duration timeout;
    private final int maxResults;
    private final int maxResponseBytes;
    private final Clock clock;

    OfficialEvidenceGatewayProvider(
            AiWebRagProperties.ExternalSourcesProperties properties,
            ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    OfficialEvidenceGatewayProvider(
            AiWebRagProperties.ExternalSourcesProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        if (properties == null) {
            throw new IllegalArgumentException("external source properties are required");
        }
        this.endpoint = requireAllowedHttpsEndpoint(
                properties.getGatewayUrl(),
                properties.getGatewayAllowedHosts());
        this.apiKey = requireText(properties.getApiKey(), "external source gateway api key");
        this.sourceAllowedHosts = normalizeHosts(properties.getSourceAllowedHosts());
        if (sourceAllowedHosts.isEmpty()) {
            throw new IllegalArgumentException("external source allowed hosts must not be empty");
        }
        this.timeout = properties.getTimeout();
        this.maxResults = properties.getMaxResults();
        this.maxResponseBytes = properties.getMaxResponseBytes();
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supports(ExternalEvidenceRequest request) {
        return request != null;
    }

    @Override
    public List<ExternalEvidence> retrieve(ExternalEvidenceRequest request) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(Map.of(
                    "query", request.query(),
                    "jurisdiction", valueOrEmpty(request.jurisdiction()),
                    "asOfDate", request.asOfDate() == null ? "" : request.asOfDate().toString(),
                    "language", valueOrEmpty(request.language()),
                    "maxResults", Math.min(request.maxResults(), maxResults),
                    "sourcePolicy", "OFFICIAL_ONLY"));
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=" + StandardCharsets.UTF_8.name())
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                closeQuietly(response.body());
                throw new IllegalStateException("official evidence gateway rejected request");
            }
            byte[] body;
            try (InputStream input = response.body()) {
                body = input.readNBytes(maxResponseBytes + 1);
            }
            if (body.length > maxResponseBytes) {
                throw new IllegalStateException("official evidence gateway response exceeded size limit");
            }
            return parseGatewayResponse(body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("official evidence gateway request interrupted", ex);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("official evidence gateway request failed", ex);
        }
    }

    List<ExternalEvidence> parseGatewayResponse(byte[] body) throws IOException {
        GatewayResponse gatewayResponse = objectMapper.readValue(body, GatewayResponse.class);
        return map(gatewayResponse);
    }

    private List<ExternalEvidence> map(GatewayResponse response) {
        if (response == null || response.results() == null) {
            return List.of();
        }
        List<ExternalEvidence> result = new ArrayList<>();
        for (GatewayEvidence item : response.results()) {
            if (item == null || result.size() >= maxResults) {
                continue;
            }
            URI canonicalUri = parseAllowedSourceUri(item.canonicalUrl());
            if (canonicalUri == null || isBlank(item.exactText()) || isBlank(item.title()) || isBlank(item.publisher())) {
                continue;
            }
            String exactText = item.exactText().strip();
            String contentHash = sha256(canonicalUri + "\n" + exactText);
            String evidenceId = isBlank(item.evidenceId())
                    ? "ext-" + contentHash.substring(0, 20)
                    : bounded(item.evidenceId(), 128);
            ExternalEvidenceSourceType sourceType;
            try {
                sourceType = ExternalEvidenceSourceType.valueOf(
                        requireText(item.sourceType(), "sourceType").toUpperCase(Locale.ROOT));
            } catch (RuntimeException ex) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("providerId", PROVIDER_ID);
            result.add(new ExternalEvidence(
                    evidenceId,
                    sourceType,
                    bounded(item.title(), 300),
                    bounded(item.publisher(), 200),
                    canonicalUri,
                    parseDate(item.publishedDate()),
                    parseDate(item.effectiveDate()),
                    Instant.now(clock),
                    exactText,
                    contentHash,
                    normalizeScore(item.score()),
                    metadata));
        }
        return List.copyOf(result);
    }

    private URI parseAllowedSourceUri(String value) {
        try {
            if (isBlank(value) || value.length() > 2_048) {
                return null;
            }
            URI uri = URI.create(value);
            return isAllowedHttps(uri, sourceAllowedHosts) ? uri : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static URI requireAllowedHttpsEndpoint(String value, Set<String> allowedHosts) {
        URI uri;
        try {
            uri = URI.create(requireText(value, "external source gateway url"));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("external source gateway url is invalid", ex);
        }
        if (!isAllowedHttps(uri, normalizeHosts(allowedHosts))) {
            throw new IllegalArgumentException("external source gateway must use an allowlisted https host");
        }
        return uri;
    }

    private static boolean isAllowedHttps(URI uri, Set<String> allowedHosts) {
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            return false;
        }
        return allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
    }

    private static Set<String> normalizeHosts(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static LocalDate parseDate(String value) {
        try {
            return isBlank(value) ? null : LocalDate.parse(value.strip());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static double normalizeScore(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String bounded(String value, int maxChars) {
        String normalized = requireText(value, "external evidence field");
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private static String requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // Nothing else can be done while handling an upstream rejection.
        }
    }

    private record GatewayResponse(List<GatewayEvidence> results) {
    }

    private record GatewayEvidence(
            String evidenceId,
            String sourceType,
            String title,
            String publisher,
            String canonicalUrl,
            String publishedDate,
            String effectiveDate,
            String exactText,
            Double score) {
    }
}
