package studio.one.platform.ai.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityAssessment.SearchabilityStatus;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagObjectMetadataContributor;
import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto;
import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto.Availability;
import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto.Source;
import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto.Status;
import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto.Suggestion;
import studio.one.platform.ai.web.dto.DocumentQuestionSuggestionsResponseDto.Type;

/** Builds revision-bound, deterministic RAG question suggestions from indexed metadata. */
public final class DocumentQuestionSuggestionService {

    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final long MAX_CACHE_ENTRIES = 10_000;

    private static final List<String> REVISION_KEYS = List.of(
            "markdownRevisionId", "sourceRevisionId", "revisionId");
    private static final List<String> KEY_POINT_KEYS = List.of(
            "keyPoints", "keyContent", "highlights");
    private static final List<String> UNSAFE_PHRASES = List.of(
            "ignore previous", "ignore all", "system prompt", "developer message",
            "이전 지시를 무시", "앞선 지시를 무시", "시스템 프롬프트");

    private final DocumentUsabilityService usabilityService;
    private final VectorStorePort vectorStorePort;
    private final List<RagObjectMetadataContributor> metadataContributors;
    private final DocumentQuestionSuggestionPolicy policy;
    private final Cache<QuestionSuggestionCacheKey, DocumentQuestionSuggestionsResponseDto> suggestionCache;

    public DocumentQuestionSuggestionService(
            DocumentUsabilityService usabilityService,
            VectorStorePort vectorStorePort) {
        this(usabilityService, vectorStorePort, List.of(), new DocumentQuestionSuggestionPolicy());
    }

    public DocumentQuestionSuggestionService(
            DocumentUsabilityService usabilityService,
            VectorStorePort vectorStorePort,
            List<RagObjectMetadataContributor> metadataContributors) {
        this(usabilityService, vectorStorePort, metadataContributors, new DocumentQuestionSuggestionPolicy());
    }

    DocumentQuestionSuggestionService(
            DocumentUsabilityService usabilityService,
            VectorStorePort vectorStorePort,
            DocumentQuestionSuggestionPolicy policy) {
        this(usabilityService, vectorStorePort, List.of(), policy);
    }

    DocumentQuestionSuggestionService(
            DocumentUsabilityService usabilityService,
            VectorStorePort vectorStorePort,
            List<RagObjectMetadataContributor> metadataContributors,
            DocumentQuestionSuggestionPolicy policy) {
        this.usabilityService = java.util.Objects.requireNonNull(usabilityService, "usabilityService");
        this.vectorStorePort = java.util.Objects.requireNonNull(vectorStorePort, "vectorStorePort");
        this.metadataContributors = metadataContributors == null ? List.of() : List.copyOf(metadataContributors);
        this.policy = policy == null ? new DocumentQuestionSuggestionPolicy() : policy;
        this.suggestionCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(CACHE_TTL)
                .build();
    }

    public DocumentQuestionSuggestionsResponseDto suggest(String objectType, String objectId) {
        DocumentUsabilityAssessment assessment = usabilityService.evaluate(objectType, objectId);
        DocumentQuestionSuggestionsResponseDto.Basis basis = basis(assessment);
        if (assessment.searchability().status() != SearchabilityStatus.SEARCHABLE) {
            LinkedHashSet<String> reasons = new LinkedHashSet<>();
            reasons.add("DOCUMENT_NOT_SEARCHABLE");
            reasons.add("SEARCHABILITY_" + assessment.searchability().status().name());
            reasons.addAll(assessment.searchability().reasonCodes());
            return response(basis, Status.NOT_READY, List.copyOf(reasons), List.of());
        }

        List<Map<String, Object>> objectMetadata = objectMetadata(basis);
        QuestionSuggestionCacheKey cacheKey = QuestionSuggestionCacheKey.from(
                basis, metadataSignalFingerprint(objectMetadata));
        DocumentQuestionSuggestionsResponseDto cached = suggestionCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<VectorSearchResult> rows = vectorStorePort.listByObject(
                basis.objectType(), basis.objectId(), 0, DocumentQuestionSuggestionPolicy.MAX_CANDIDATES);
        List<VectorDocument> currentRevision = (rows == null ? List.<VectorSearchResult>of() : rows).stream()
                .map(VectorSearchResult::document)
                .filter(java.util.Objects::nonNull)
                .filter(document -> revisionCompatible(document.metadata(), basis.revisionId()))
                .sorted(Comparator.comparing(VectorDocument::id))
                .toList();
        if (currentRevision.isEmpty()) {
            return response(
                    basis,
                    Status.NOT_READY,
                    List.of("CURRENT_REVISION_VECTORS_NOT_FOUND"),
                    List.of());
        }

        List<Suggestion> suggestions = suggestions(basis, currentRevision, objectMetadata);
        if (suggestions.isEmpty()) {
            return response(
                    basis,
                    Status.NO_SIGNALS,
                    List.of("QUESTION_SIGNALS_NOT_FOUND"),
                    List.of());
        }
        DocumentQuestionSuggestionsResponseDto generated =
                response(basis, Status.AVAILABLE, List.of(), suggestions);
        suggestionCache.put(cacheKey, generated);
        return generated;
    }

    private List<Suggestion> suggestions(
            DocumentQuestionSuggestionsResponseDto.Basis basis,
            List<VectorDocument> documents,
            List<Map<String, Object>> objectMetadata) {
        List<Suggestion> result = new ArrayList<>();
        Set<String> seenQueries = new LinkedHashSet<>();

        for (VectorDocument document : documents) {
            Map<String, Object> metadata = safeMetadata(document.metadata());
            String question = safeQuestion(firstText(metadata, "criticalQuestion", "question"));
            List<String> keywords = preferredKeywords(metadata);
            if (question == null || keywords.isEmpty() || !verifiedIdeaBlock(metadata)) {
                continue;
            }
            addSuggestion(
                    result,
                    seenQueries,
                    basis,
                    question,
                    Type.CRITICAL_QUESTION,
                    keywords,
                    Source.IDEA_BLOCK_QUESTION);
            if (result.size() >= DocumentQuestionSuggestionPolicy.MAX_SUGGESTIONS) {
                return List.copyOf(result);
            }
        }

        List<Signal> signals = rankedSignals(documents, objectMetadata);
        if (signals.isEmpty()) {
            return List.copyOf(result);
        }

        Signal first = signals.get(0);
        addSuggestion(
                result,
                seenQueries,
                basis,
                DocumentQuestionSuggestionPolicy.KEYWORD_EXPLANATION_TEMPLATE.formatted(first.value()),
                Type.KEYWORD_EXPLANATION,
                List.of(first.value()),
                first.source());

        if (signals.size() >= 2 && result.size() < DocumentQuestionSuggestionPolicy.MAX_SUGGESTIONS) {
            Signal second = signals.get(1);
            addSuggestion(
                    result,
                    seenQueries,
                    basis,
                    DocumentQuestionSuggestionPolicy.KEYWORD_RELATION_TEMPLATE.formatted(
                            first.value(), second.value()),
                    Type.KEYWORD_RELATION,
                    List.of(first.value(), second.value()),
                    higherPriority(first.source(), second.source()));
        }

        if (signals.size() >= 3 && result.size() < DocumentQuestionSuggestionPolicy.MAX_SUGGESTIONS) {
            Signal third = signals.get(2);
            addSuggestion(
                    result,
                    seenQueries,
                    basis,
                    DocumentQuestionSuggestionPolicy.KEYWORD_SUMMARY_TEMPLATE.formatted(third.value()),
                    Type.KEYWORD_SUMMARY,
                    List.of(third.value()),
                    third.source());
        }
        return List.copyOf(result);
    }

    private List<Signal> rankedSignals(
            List<VectorDocument> documents,
            List<Map<String, Object>> objectMetadata) {
        Map<String, Signal> documentKeywords = new LinkedHashMap<>();
        Map<String, Signal> keyPoints = new LinkedHashMap<>();
        Map<String, CountedSignal> chunkKeywords = new LinkedHashMap<>();
        for (VectorDocument document : documents) {
            addRankedSignals(
                    safeMetadata(document.metadata()), documentKeywords, keyPoints, chunkKeywords, true);
        }
        for (Map<String, Object> metadata : objectMetadata) {
            addRankedSignals(safeMetadata(metadata), documentKeywords, keyPoints, chunkKeywords, false);
        }

        LinkedHashMap<String, Signal> merged = new LinkedHashMap<>();
        documentKeywords.forEach(merged::putIfAbsent);
        keyPoints.forEach(merged::putIfAbsent);
        chunkKeywords.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, CountedSignal>>comparingInt(entry -> entry.getValue().count())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> merged.putIfAbsent(
                        entry.getKey(),
                        new Signal(entry.getValue().value(), Source.CHUNK_KEYWORDS)));
        return List.copyOf(merged.values());
    }

    private void addRankedSignals(
            Map<String, Object> metadata,
            Map<String, Signal> documentKeywords,
            Map<String, Signal> keyPoints,
            Map<String, CountedSignal> chunkKeywords,
            boolean includeChunkKeywords) {
        addSignals(documentKeywords, strings(metadataValue(metadata, "docKeywords")), Source.DOCUMENT_KEYWORDS);
        addSignals(documentKeywords, strings(metadataValue(metadata, "keywords")), Source.DOCUMENT_KEYWORDS);
        for (String key : KEY_POINT_KEYS) {
            addSignals(keyPoints, strings(metadataValue(metadata, key)), Source.KEY_POINTS);
        }
        if (!includeChunkKeywords) {
            return;
        }
        for (String value : strings(metadataValue(metadata, "chunkKeywords"))) {
            String keyword = safeKeyword(value);
            if (keyword == null) {
                continue;
            }
            String normalized = normalizeKey(keyword);
            chunkKeywords.compute(normalized, (ignored, existing) -> existing == null
                    ? new CountedSignal(keyword, 1)
                    : new CountedSignal(existing.value(), existing.count() + 1));
        }
    }

    private void addSignals(Map<String, Signal> destination, List<String> values, Source source) {
        for (String value : values) {
            String keyword = safeKeyword(value);
            if (keyword != null) {
                destination.putIfAbsent(normalizeKey(keyword), new Signal(keyword, source));
            }
        }
    }

    private void addSuggestion(
            List<Suggestion> destination,
            Set<String> seenQueries,
            DocumentQuestionSuggestionsResponseDto.Basis basis,
            String rawQuery,
            Type type,
            List<String> keywords,
            Source source) {
        if (destination.size() >= DocumentQuestionSuggestionPolicy.MAX_SUGGESTIONS) {
            return;
        }
        String query = safeQuestion(rawQuery);
        if (query == null) {
            return;
        }
        String normalized = normalizeKey(query);
        if (!seenQueries.add(normalized)) {
            return;
        }
        destination.add(new Suggestion(
                suggestionId(basis, normalized),
                query,
                type,
                keywords,
                source));
    }

    private DocumentQuestionSuggestionsResponseDto response(
            DocumentQuestionSuggestionsResponseDto.Basis basis,
            Status status,
            List<String> reasonCodes,
            List<Suggestion> suggestions) {
        return new DocumentQuestionSuggestionsResponseDto(
                DocumentQuestionSuggestionPolicy.CONTRACT_VERSION,
                Instant.now(),
                basis,
                new Availability(status, reasonCodes),
                suggestions,
                policy.snapshot());
    }

    private DocumentQuestionSuggestionsResponseDto.Basis basis(DocumentUsabilityAssessment assessment) {
        DocumentUsabilityAssessment.Basis basis = assessment.basis();
        return new DocumentQuestionSuggestionsResponseDto.Basis(
                basis.objectType(),
                basis.objectId(),
                basis.documentId(),
                basis.revisionId(),
                basis.sourceContentHash(),
                basis.chunkSetId());
    }

    private List<Map<String, Object>> objectMetadata(DocumentQuestionSuggestionsResponseDto.Basis basis) {
        return metadataContributors.stream()
                .map(contributor -> contributor.contribute(basis.objectType(), basis.objectId()))
                .filter(java.util.Objects::nonNull)
                .filter(metadata -> !metadata.isEmpty())
                .toList();
    }

    private String metadataSignalFingerprint(List<Map<String, Object>> objectMetadata) {
        List<String> canonical = new ArrayList<>();
        for (Map<String, Object> metadata : objectMetadata) {
            addFingerprintSignals(canonical, "docKeywords", metadataValue(metadata, "docKeywords"));
            addFingerprintSignals(canonical, "keywords", metadataValue(metadata, "keywords"));
            for (String key : KEY_POINT_KEYS) {
                addFingerprintSignals(canonical, key, metadataValue(metadata, key));
            }
        }
        return sha256(String.join("\n", canonical));
    }

    private void addFingerprintSignals(List<String> target, String key, Object value) {
        for (String candidate : strings(value)) {
            String signal = safeKeyword(candidate);
            if (signal != null) {
                target.add(key + "=" + normalizeKey(signal));
            }
        }
    }

    private boolean revisionCompatible(Map<String, Object> metadata, String revisionId) {
        String candidate = firstText(safeMetadata(metadata), REVISION_KEYS.toArray(String[]::new));
        return revisionId == null ? candidate == null : revisionId.equals(candidate);
    }

    private boolean verifiedIdeaBlock(Map<String, Object> metadata) {
        String status = firstText(metadata, "validationStatus");
        if (status != null) {
            String normalized = status.toUpperCase(Locale.ROOT);
            if (normalized.contains("INVALID") || normalized.contains("REJECT")) {
                return false;
            }
        }
        boolean ideaBlock = "IDEABLOCK".equalsIgnoreCase(firstText(metadata, "chunkType"))
                || Boolean.TRUE.equals(metadata.get("ideaBlockDistilled"));
        boolean hasEvidence = metadata.get("sourceEvidence") != null
                || metadata.get("sourceBlockRange") != null
                || metadata.get("sourceBlockRanges") != null;
        return ideaBlock && hasEvidence;
    }

    private List<String> preferredKeywords(Map<String, Object> metadata) {
        LinkedHashMap<String, Signal> values = new LinkedHashMap<>();
        addSignals(values, strings(metadataValue(metadata, "docKeywords")), Source.DOCUMENT_KEYWORDS);
        addSignals(values, strings(metadataValue(metadata, "keywords")), Source.DOCUMENT_KEYWORDS);
        addSignals(values, strings(metadataValue(metadata, "chunkKeywords")), Source.CHUNK_KEYWORDS);
        return values.values().stream().map(Signal::value).limit(3).toList();
    }

    private Object metadataValue(Map<String, Object> metadata, String key) {
        return metadataValue(metadata, key, 0);
    }

    private Object metadataValue(Map<?, ?> metadata, String key, int depth) {
        Object direct = metadata.get(key);
        if (direct != null) {
            return direct;
        }
        if (depth >= 3) {
            return null;
        }
        for (String container : List.of("documentMetadata", "documentOverview", "overview", "markdown")) {
            Object value = metadata.get(container);
            if (value instanceof Map<?, ?> nested) {
                Object nestedValue = metadataValue(nested, key, depth + 1);
                if (nestedValue != null) {
                    return nestedValue;
                }
            }
        }
        return null;
    }

    private List<String> strings(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object item : iterable) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return List.copyOf(result);
        }
        if (value.getClass().isArray() && value instanceof Object[] array) {
            return java.util.Arrays.stream(array)
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
        }
        return List.of(String.valueOf(value));
    }

    private String safeKeyword(String value) {
        return safeText(value, DocumentQuestionSuggestionPolicy.MAX_KEYWORD_CHARS);
    }

    private String safeQuestion(String value) {
        return safeText(value, DocumentQuestionSuggestionPolicy.MAX_QUESTION_CHARS);
    }

    private String safeText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return null;
        }
        String normalized = value.replace("`", "")
                .replaceAll("\\p{Cntrl}", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() < 2 || normalized.length() > maxLength) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || UNSAFE_PHRASES.stream().anyMatch(lower::contains)) {
            return null;
        }
        return normalized;
    }

    private String firstText(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private Map<String, Object> safeMetadata(Map<String, Object> metadata) {
        return metadata == null ? Map.of() : metadata;
    }

    private String suggestionId(DocumentQuestionSuggestionsResponseDto.Basis basis, String normalizedQuery) {
        String canonical = String.join("|",
                nullToEmpty(basis.objectType()),
                nullToEmpty(basis.objectId()),
                nullToEmpty(basis.revisionId()),
                normalizedQuery,
                DocumentQuestionSuggestionPolicy.FINGERPRINT);
        return "qs-" + sha256(canonical).substring(0, 16);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Source higherPriority(Source first, Source second) {
        return first.ordinal() <= second.ordinal() ? first : second;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record QuestionSuggestionCacheKey(
            String objectType,
            String objectId,
            String documentId,
            String revisionId,
            String sourceContentHash,
            String chunkSetId,
            String metadataSignalFingerprint,
            String policyFingerprint) {

        private static QuestionSuggestionCacheKey from(
                DocumentQuestionSuggestionsResponseDto.Basis basis,
                String metadataSignalFingerprint) {
            return new QuestionSuggestionCacheKey(
                    basis.objectType(),
                    basis.objectId(),
                    basis.documentId(),
                    basis.revisionId(),
                    basis.sourceContentHash(),
                    basis.chunkSetId(),
                    metadataSignalFingerprint,
                    DocumentQuestionSuggestionPolicy.FINGERPRINT);
        }
    }

    private record Signal(String value, Source source) {
    }

    private record CountedSignal(String value, int count) {
    }
}
