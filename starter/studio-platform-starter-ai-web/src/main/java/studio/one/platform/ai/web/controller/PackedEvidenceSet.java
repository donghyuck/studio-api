package studio.one.platform.ai.web.controller;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.rag.external.ExternalEvidence;
import studio.one.platform.chunking.core.ChunkMetadata;

/**
 * Immutable, response-scoped source of truth for the prompt context and citations.
 */
public record PackedEvidenceSet(
        String promptContext,
        List<PackedEvidence> evidence,
        Map<String, Object> diagnostics,
        String contextFingerprint) {

    private static final int MAX_COMBINED_CONTEXT_CHARS = 24_000;
    private static final int MAX_EXTERNAL_EXCERPT_CHARS = 2_000;

    public PackedEvidenceSet {
        promptContext = promptContext == null ? "" : promptContext;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
        contextFingerprint = contextFingerprint == null ? fingerprint(promptContext, evidence) : contextFingerprint;
    }

    public static PackedEvidenceSet empty(String promptContext, Map<String, Object> diagnostics) {
        return new PackedEvidenceSet(promptContext, List.of(), diagnostics, null);
    }

    public static PackedEvidenceSet from(
            String promptContext,
            List<RagSearchResult> packedResults,
            Map<String, Object> diagnostics) {
        List<PackedEvidence> evidence = new ArrayList<>();
        if (packedResults != null) {
            for (int i = 0; i < packedResults.size(); i++) {
                evidence.add(PackedEvidence.from(i + 1, packedResults.get(i)));
            }
        }
        return new PackedEvidenceSet(promptContext, evidence, diagnostics, null);
    }

    public PackedEvidenceSet withExternalEvidence(List<ExternalEvidence> externalEvidence) {
        if (externalEvidence == null || externalEvidence.isEmpty()) {
            return this;
        }
        List<PackedEvidence> combined = new ArrayList<>(evidence);
        StringBuilder externalContext = new StringBuilder();
        externalContext.append("<EXTERNAL_EVIDENCE>\n");
        int nextIndex = evidence.size() + 1;
        for (ExternalEvidence item : externalEvidence) {
            if (item == null) {
                continue;
            }
            String exactText = boundedText(item.exactText(), MAX_EXTERNAL_EXCERPT_CHARS);
            PackedEvidence packed = PackedEvidence.fromExternal(nextIndex, item, exactText);
            String entry = new StringBuilder()
                    .append("[근거 ")
                    .append(packed.citationIndex())
                    .append("]\n자료 유형: ")
                    .append(packed.sourceType())
                    .append("\n제목: ")
                    .append(packed.documentTitle())
                    .append("\n발행기관: ")
                    .append(packed.publisher())
                    .append("\n원문:\n")
                    .append(exactText)
                    .append("\n\n")
                    .toString();
            int projectedLength = promptContext.length()
                    + 2
                    + externalContext.length()
                    + entry.length()
                    + "</EXTERNAL_EVIDENCE>".length();
            if (projectedLength > MAX_COMBINED_CONTEXT_CHARS) {
                continue;
            }
            nextIndex++;
            combined.add(packed);
            externalContext.append(entry);
        }
        externalContext.append("</EXTERNAL_EVIDENCE>");
        if (combined.size() == evidence.size()) {
            return this;
        }
        String combinedContext = promptContext.isBlank()
                ? externalContext.toString()
                : promptContext + "\n\n" + externalContext;
        Map<String, Object> combinedDiagnostics = new LinkedHashMap<>(diagnostics);
        combinedDiagnostics.put("externalEvidenceCount", combined.size() - evidence.size());
        return new PackedEvidenceSet(combinedContext, combined, combinedDiagnostics, null);
    }

    public PackedEvidenceSet withDiagnostic(String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return this;
        }
        Map<String, Object> updated = new LinkedHashMap<>(diagnostics);
        updated.put(key, value);
        return new PackedEvidenceSet(promptContext, evidence, updated, null);
    }

    public boolean hasOrigin(String expected) {
        return evidence.stream().anyMatch(item -> expected.equals(item.origin()));
    }

    public boolean hasExternalOrigin() {
        return evidence.stream().anyMatch(item ->
                "INDEXED_WEB".equals(item.origin())
                        || "OFFICIAL_EXTERNAL".equals(item.origin()));
    }

    private static String fingerprint(String context, List<PackedEvidence> evidence) {
        StringBuilder canonical = new StringBuilder(context == null ? "" : context);
        evidence.forEach(item -> canonical.append('\n').append(item.evidenceId()));
        return UUID.nameUUIDFromBytes(canonical.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record PackedEvidence(
            int citationIndex,
            String evidenceId,
            String documentId,
            String revisionId,
            String chunkId,
            Integer chunkOrder,
            String originalFileName,
            String documentTitle,
            String documentSemanticType,
            double score,
            String evidenceKind,
            String supportStatus,
            List<SourceSpan> sourceSpans,
            String origin,
            String sourceType,
            String publisher,
            String canonicalUrl,
            String publishedDate,
            String effectiveDate,
            String retrievedAt) {

        public PackedEvidence {
            if (citationIndex < 1) {
                throw new IllegalArgumentException("citationIndex must start at 1");
            }
            sourceSpans = sourceSpans == null ? List.of() : List.copyOf(sourceSpans);
        }

        static PackedEvidence from(int citationIndex, RagSearchResult result) {
            Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
            String documentId = firstText(metadata,
                    "documentId", ChunkMetadata.KEY_SOURCE_DOCUMENT_ID);
            if (documentId == null) {
                documentId = result.documentId();
            }
            String revisionId = firstText(metadata, "revisionId", "sourceRevisionId");
            String chunkId = firstText(metadata, "chunkId");
            if (chunkId == null) {
                chunkId = result.documentId();
            }
            String exactText = result.content() == null ? "" : result.content();
            String identity = String.join("|",
                    Objects.toString(documentId, ""),
                    Objects.toString(revisionId, ""),
                    Objects.toString(chunkId, ""),
                    exactText);
            String evidenceId = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
            SourceSpan fallbackSpan = new SourceSpan(
                    exactText,
                    chunkId,
                    firstText(metadata, ChunkMetadata.KEY_SOURCE_REF, "sourceRef"),
                    firstInteger(metadata, ChunkMetadata.KEY_PAGE, "pageNumber"),
                    firstInteger(metadata, "slide", "slideNumber"),
                    firstText(metadata, ChunkMetadata.KEY_SECTION, ChunkMetadata.KEY_HEADING_PATH, "heading"),
                    firstInteger(metadata, "startOffset"),
                    firstInteger(metadata, "endOffset"),
                    Boolean.TRUE.equals(metadata.get("truncated")),
                    stringList(metadata.get("blockIds")),
                    preferredExcerpt(exactText, stringList(metadata.get("_matchedQueryTerms"))));
            List<SourceSpan> spans = PackedEvidenceSet.sourceSpans(metadata.get("sourceSpans"), exactText);
            if (spans.isEmpty()) {
                spans = List.of(fallbackSpan);
            }
            return new PackedEvidence(
                    citationIndex,
                    evidenceId,
                    documentId,
                    revisionId,
                    chunkId,
                    firstInteger(metadata, ChunkMetadata.KEY_CHUNK_ORDER, "chunkIndex"),
                    firstText(metadata, "sourceFileName", "filename", "fileName", "name", "sourceName"),
                    firstText(metadata, "docTitle", "documentTitle", "title", "displayName"),
                    firstText(metadata, "docSemanticType"),
                    result.score(),
                    firstText(metadata, "evidenceKind") == null ? "NORMALIZED_CHUNK" : firstText(metadata, "evidenceKind"),
                    firstText(metadata, "supportStatus") == null ? "SOURCE_VERIFIED" : firstText(metadata, "supportStatus"),
                    spans,
                    firstText(metadata, "evidenceOrigin") == null
                            ? "DOCUMENT"
                            : firstText(metadata, "evidenceOrigin"),
                    firstText(metadata, "sourceType") == null ? "NORMALIZED_CHUNK" : firstText(metadata, "sourceType"),
                    firstText(metadata, "publisher"),
                    firstText(metadata, "canonicalUrl"),
                    firstText(metadata, "publishedAt", "publishedDate"),
                    null,
                    firstText(metadata, "retrievedAt"));
        }

        static PackedEvidence fromExternal(
                int citationIndex,
                ExternalEvidence evidence,
                String exactText) {
            SourceSpan span = new SourceSpan(
                    exactText,
                    null,
                    evidence.canonicalUri().toString(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    exactText.length() > 500,
                    List.of(),
                    boundedText(exactText, 500));
            return new PackedEvidence(
                    citationIndex,
                    evidence.evidenceId(),
                    null,
                    null,
                    null,
                    null,
                    evidence.publisher(),
                    evidence.title(),
                    null,
                    evidence.score(),
                    evidence.sourceType().name(),
                    "SOURCE_VERIFIED",
                    List.of(span),
                    "OFFICIAL_EXTERNAL",
                    evidence.sourceType().name(),
                    evidence.publisher(),
                    evidence.canonicalUri().toString(),
                    evidence.publishedDate() == null ? null : evidence.publishedDate().toString(),
                    evidence.effectiveDate() == null ? null : evidence.effectiveDate().toString(),
                    evidence.retrievedAt().toString());
        }
    }

    public record SourceSpan(
            String exactText,
            String chunkId,
            String sourceRef,
            Integer page,
            Integer slide,
            String section,
            Integer startOffset,
            Integer endOffset,
            boolean truncated,
            List<String> blockIds,
            @JsonIgnore String preferredExcerpt) {

        public SourceSpan {
            exactText = exactText == null ? "" : exactText;
            blockIds = blockIds == null ? List.of() : List.copyOf(blockIds);
            preferredExcerpt = preferredExcerpt == null ? boundedText(exactText, 500) : preferredExcerpt;
        }
    }

    private static List<SourceSpan> sourceSpans(Object value, String packedContent) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<SourceSpan> spans = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            raw.forEach((key, entryValue) -> {
                if (key != null) {
                    metadata.put(key.toString(), entryValue);
                }
            });
            String exactText = firstText(metadata, "exactText");
            if (exactText == null || !packedContent.contains(exactText)) {
                continue;
            }
            spans.add(new SourceSpan(
                    exactText,
                    firstText(metadata, "chunkId"),
                    firstText(metadata, "sourceRef"),
                    firstInteger(metadata, "page"),
                    firstInteger(metadata, "slide"),
                    firstText(metadata, "section"),
                    firstInteger(metadata, "startOffset"),
                    firstInteger(metadata, "endOffset"),
                    Boolean.TRUE.equals(metadata.get("truncated")),
                    stringList(metadata.get("blockIds")),
                    preferredExcerpt(exactText, stringList(metadata.get("_matchedQueryTerms")))));
        }
        return List.copyOf(spans);
    }

    private static String firstText(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return null;
    }

    private static Integer firstInteger(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                try {
                    return Integer.valueOf(value.toString().trim());
                } catch (NumberFormatException ignored) {
                    // Try the next key.
                }
            }
        }
        return null;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            iterable.forEach(item -> {
                if (item != null && !item.toString().isBlank()) {
                    values.add(item.toString().trim());
                }
            });
            return List.copyOf(values);
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.trim());
        }
        return List.of();
    }

    public List<Map<String, Object>> toReferences(boolean includePackedContent) {
        List<Map<String, Object>> references = new ArrayList<>(evidence.size());
        for (PackedEvidence item : evidence) {
            Map<String, Object> reference = new LinkedHashMap<>();
            reference.put("citationIndex", item.citationIndex());
            reference.put("index", item.citationIndex());
            reference.put("evidenceId", item.evidenceId());
            put(reference, "documentId", item.documentId());
            put(reference, "revisionId", item.revisionId());
            put(reference, "chunkId", item.chunkId());
            put(reference, "chunkOrder", item.chunkOrder());
            put(reference, "originalFileName", item.originalFileName());
            put(reference, "sourceFileName", item.originalFileName());
            put(reference, "sourceName",
                    item.originalFileName() == null ? item.documentTitle() : item.originalFileName());
            put(reference, "title", item.documentTitle());
            put(reference, "documentSemanticType", item.documentSemanticType());
            reference.put("score", item.score());
            reference.put("evidenceKind", item.evidenceKind());
            reference.put("supportStatus", item.supportStatus());
            reference.put("origin", item.origin());
            reference.put("sourceType", item.sourceType());
            put(reference, "publisher", item.publisher());
            put(reference, "canonicalUrl", item.canonicalUrl());
            put(reference, "publishedDate", item.publishedDate());
            put(reference, "effectiveDate", item.effectiveDate());
            put(reference, "retrievedAt", item.retrievedAt());
            reference.put("citationLabel", "근거 " + item.citationIndex());
            reference.put("spans", item.sourceSpans());
            if (!item.sourceSpans().isEmpty()) {
                SourceSpan span = item.sourceSpans().get(0);
                reference.put("exactText", span.exactText());
                reference.put("excerpt", span.exactText());
                put(reference, "spanChunkId", span.chunkId());
                put(reference, "sourceRef", span.sourceRef());
                put(reference, "page", span.page());
                put(reference, "pageNumber", span.page());
                put(reference, "slide", span.slide());
                put(reference, "slideNumber", span.slide());
                put(reference, "section", span.section());
                put(reference, "startOffset", span.startOffset());
                put(reference, "endOffset", span.endOffset());
                reference.put("truncated", span.truncated());
                reference.put("blockIds", span.blockIds());
                if (includePackedContent) {
                    reference.put("content", span.exactText());
                }
            }
            references.add(Map.copyOf(reference));
        }
        return List.copyOf(references);
    }

    /**
     * Produces the bounded public reference contract without internal storage identifiers.
     */
    public List<Map<String, Object>> toPublicReferences(
            Set<Integer> selectedIndexes,
            String usageStatus) {
        Set<Integer> selected = selectedIndexes == null ? Set.of() : Set.copyOf(selectedIndexes);
        List<Map<String, Object>> references = new ArrayList<>(Math.min(3, selected.size()));
        for (PackedEvidence item : evidence) {
            if (!selected.contains(item.citationIndex())) {
                continue;
            }
            SourceSpan span = item.sourceSpans().stream()
                    .filter(candidate -> candidate.exactText() != null && !candidate.exactText().isBlank())
                    .findFirst()
                    .orElse(null);
            if (span == null) {
                continue;
            }
            Map<String, Object> reference = new LinkedHashMap<>();
            reference.put("citationIndex", item.citationIndex());
            reference.put("evidenceId", item.evidenceId());
            reference.put("usageStatus", usageStatus);
            put(reference, "sourceName",
                    item.originalFileName() == null ? item.documentTitle() : item.originalFileName());
            put(reference, "title", item.documentTitle());
            put(reference, "documentSemanticType", item.documentSemanticType());
            reference.put("score", item.score());
            reference.put("evidenceKind", item.evidenceKind());
            reference.put("supportStatus", item.supportStatus());
            reference.put("origin", item.origin());
            reference.put("sourceType", item.sourceType());
            put(reference, "publisher", item.publisher());
            put(reference, "canonicalUrl", item.canonicalUrl());
            put(reference, "publishedDate", item.publishedDate());
            put(reference, "effectiveDate", item.effectiveDate());
            put(reference, "retrievedAt", item.retrievedAt());
            String exactText = span.preferredExcerpt();
            reference.put("exactText", exactText);
            put(reference, "page", span.page());
            put(reference, "slide", span.slide());
            put(reference, "section", boundedText(span.section(), 200));
            put(reference, "locator", locator(span));
            reference.put("truncated", span.truncated() || exactText.length() < span.exactText().length());
            references.add(Map.copyOf(reference));
            if (references.size() == 3 && "RETRIEVED_ONLY".equals(usageStatus)) {
                break;
            }
        }
        return List.copyOf(references);
    }

    private static String preferredExcerpt(String value, List<String> matchedQueryTerms) {
        if (value == null || value.length() <= 500) {
            return boundedText(value, 500);
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        int matchOffset = matchedQueryTerms == null ? -1 : matchedQueryTerms.stream()
                .filter(term -> term != null && !term.isBlank())
                .map(term -> lower.indexOf(term.toLowerCase(java.util.Locale.ROOT)))
                .filter(offset -> offset >= 0)
                .findFirst()
                .orElse(-1);
        if (matchOffset < 0) {
            return boundedText(value, 500);
        }
        int start = Math.max(0, matchOffset - 200);
        start = Math.min(start, value.length() - 500);
        return value.substring(start, start + 500).strip();
    }

    private static String boundedText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String locator(SourceSpan span) {
        List<String> parts = new ArrayList<>(3);
        if (span.page() != null) {
            parts.add("페이지 " + span.page());
        }
        if (span.slide() != null) {
            parts.add("슬라이드 " + span.slide());
        }
        String section = boundedText(span.section(), 200);
        if (section != null && !section.isBlank()) {
            parts.add(section);
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            target.put(key, value);
        }
    }
}
