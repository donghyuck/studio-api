package studio.one.platform.ai.web.controller;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.chunking.core.ChunkMetadata;

/**
 * Immutable, response-scoped source of truth for the prompt context and citations.
 */
public record PackedEvidenceSet(
        String promptContext,
        List<PackedEvidence> evidence,
        Map<String, Object> diagnostics,
        String contextFingerprint) {

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
            List<SourceSpan> sourceSpans) {

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
                    stringList(metadata.get("blockIds")));
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
                    firstText(metadata, "docTitle", "documentTitle"),
                    firstText(metadata, "docSemanticType"),
                    result.score(),
                    firstText(metadata, "evidenceKind") == null ? "NORMALIZED_CHUNK" : firstText(metadata, "evidenceKind"),
                    firstText(metadata, "supportStatus") == null ? "SOURCE_VERIFIED" : firstText(metadata, "supportStatus"),
                    spans);
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
            List<String> blockIds) {

        public SourceSpan {
            exactText = exactText == null ? "" : exactText;
            blockIds = blockIds == null ? List.of() : List.copyOf(blockIds);
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
                    stringList(metadata.get("blockIds"))));
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

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            target.put(key, value);
        }
    }
}
