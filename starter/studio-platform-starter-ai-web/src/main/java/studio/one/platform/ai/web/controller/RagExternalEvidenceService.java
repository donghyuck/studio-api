package studio.one.platform.ai.web.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import studio.one.platform.ai.core.rag.external.ExternalEvidence;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceProvider;
import studio.one.platform.ai.core.rag.external.ExternalEvidenceRequest;
import studio.one.platform.ai.web.dto.ExternalSourceOptionsDto;

/**
 * Selects official-source providers and normalizes their bounded evidence.
 */
public final class RagExternalEvidenceService {

    private static final int DEFAULT_MAX_RESULTS = 8;
    private static final int MAX_EXACT_TEXT_CHARS = 8_000;
    private static final int MAX_QUERY_CHARS = 4_000;

    private final List<ExternalEvidenceProvider> providers;
    private final Clock clock;

    public RagExternalEvidenceService(List<ExternalEvidenceProvider> providers) {
        this(providers, Clock.systemUTC());
    }

    RagExternalEvidenceService(List<ExternalEvidenceProvider> providers, Clock clock) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public static RagExternalEvidenceService unavailable() {
        return new RagExternalEvidenceService(List.of());
    }

    public boolean available() {
        return !providers.isEmpty();
    }

    public Result retrieve(
            String question,
            ResolvedRagSourcePolicy sourcePolicy,
            ExternalSourceOptionsDto options) {
        if (sourcePolicy == null || !sourcePolicy.externalSourcesEnabled()) {
            return Result.notRequested();
        }
        if (question == null || question.isBlank()) {
            return new Result(Status.NO_RESULTS, ReasonCode.EMPTY_QUERY, List.of(), 0, 0);
        }
        if (providers.isEmpty()) {
            return new Result(Status.UNAVAILABLE, ReasonCode.NO_PROVIDER, List.of(), 0, 0);
        }

        ExternalEvidenceRequest request = new ExternalEvidenceRequest(
                question.length() > MAX_QUERY_CHARS ? question.substring(0, MAX_QUERY_CHARS) : question,
                options == null ? null : normalizeUpper(options.jurisdiction()),
                options == null || options.asOfDate() == null
                        ? LocalDate.now(clock)
                        : options.asOfDate(),
                options == null ? null : normalizeLower(options.language()),
                DEFAULT_MAX_RESULTS);

        Map<String, ExternalEvidence> accepted = new LinkedHashMap<>();
        int attempted = 0;
        int failed = 0;
        for (ExternalEvidenceProvider provider : providers) {
            if (provider == null || !provider.supports(request)) {
                continue;
            }
            attempted++;
            try {
                List<ExternalEvidence> values = provider.retrieve(request);
                if (values == null) {
                    continue;
                }
                for (ExternalEvidence value : values) {
                    ExternalEvidence normalized = normalize(value);
                    if (normalized == null) {
                        continue;
                    }
                    String key = normalized.canonicalUri() + "|" + normalized.contentHash();
                    accepted.putIfAbsent(key, normalized);
                    if (accepted.size() >= request.maxResults()) {
                        break;
                    }
                }
            } catch (RuntimeException ignored) {
                failed++;
            }
            if (accepted.size() >= request.maxResults()) {
                break;
            }
        }

        if (!accepted.isEmpty()) {
            return new Result(
                    Status.COMPLETE,
                    ReasonCode.NONE,
                    new ArrayList<>(accepted.values()),
                    attempted,
                    failed);
        }
        if (attempted == 0) {
            return new Result(Status.UNAVAILABLE, ReasonCode.NO_SUPPORTING_PROVIDER, List.of(), 0, 0);
        }
        if (failed == attempted) {
            return new Result(Status.FAILED, ReasonCode.PROVIDER_FAILURE, List.of(), attempted, failed);
        }
        return new Result(Status.NO_RESULTS, ReasonCode.NO_VERIFIED_RESULTS, List.of(), attempted, failed);
    }

    private ExternalEvidence normalize(ExternalEvidence evidence) {
        if (evidence == null
                || evidence.canonicalUri() == null
                || !"https".equalsIgnoreCase(evidence.canonicalUri().getScheme())
                || evidence.canonicalUri().getHost() == null
                || evidence.exactText() == null
                || evidence.exactText().isBlank()) {
            return null;
        }
        String exactText = evidence.exactText().strip();
        if (exactText.length() > MAX_EXACT_TEXT_CHARS) {
            exactText = exactText.substring(0, MAX_EXACT_TEXT_CHARS);
        }
        return new ExternalEvidence(
                bounded(evidence.evidenceId(), 128),
                evidence.sourceType(),
                bounded(evidence.title(), 300),
                bounded(evidence.publisher(), 200),
                evidence.canonicalUri(),
                evidence.publishedDate(),
                evidence.effectiveDate(),
                evidence.retrievedAt(),
                exactText,
                evidence.contentHash(),
                evidence.score(),
                evidence.metadata());
    }

    private static String bounded(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private static String normalizeUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeLower(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Result(
            Status status,
            ReasonCode reasonCode,
            List<ExternalEvidence> evidence,
            int attemptedProviderCount,
            int failedProviderCount) {

        public Result {
            status = status == null ? Status.FAILED : status;
            reasonCode = reasonCode == null ? ReasonCode.NONE : reasonCode;
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }

        static Result notRequested() {
            return new Result(Status.NOT_REQUESTED, ReasonCode.NONE, List.of(), 0, 0);
        }

        public Map<String, Object> toMetadata() {
            return Map.of(
                    "status", status.name(),
                    "reasonCode", reasonCode.name(),
                    "evidenceCount", evidence.size());
        }
    }

    public enum Status {
        NOT_REQUESTED,
        COMPLETE,
        NO_RESULTS,
        UNAVAILABLE,
        FAILED
    }

    public enum ReasonCode {
        NONE,
        EMPTY_QUERY,
        NO_PROVIDER,
        NO_SUPPORTING_PROVIDER,
        PROVIDER_FAILURE,
        NO_VERIFIED_RESULTS
    }
}
