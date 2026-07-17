package studio.one.platform.markdown.autoconfigure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.domain.MarkdownResource;

final class NormalizedDocumentSnapshot {
    static final String SCHEMA_VERSION = "normalized-document-v1";
    static final String STATUS_VALID = "VALID";
    static final String STATUS_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    static final String SOURCE_NATIVE = "NATIVE_PARSED_FILE";
    static final String SOURCE_PANDOC = "PANDOC_MARKDOWN";
    static final String SOURCE_FALLBACK = "MARKDOWN_FALLBACK";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private NormalizedDocumentSnapshot() {
    }

    static MarkdownResource resource(String revisionId, NormalizedDocument document,
            String normalizationSource, List<String> issues, ObjectMapper objectMapper) {
        Map<String, Object> payload = payload(document, normalizationSource, issues);
        return new MarkdownResource("mres-normalized-" + UUID.randomUUID(), revisionId,
                MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT,
                "normalized-document.json", null, writeJson(objectMapper, payload));
    }

    static List<MarkdownResource> withDocumentProfile(List<MarkdownResource> resources,
            String requestedProfile, String resolvedProfile, String profileVersion, ObjectMapper objectMapper) {
        if (resources == null || resources.isEmpty() || !hasText(resolvedProfile)) {
            return resources == null ? List.of() : List.copyOf(resources);
        }
        return resources.stream().map(resource -> withDocumentProfile(
                resource, requestedProfile, resolvedProfile, profileVersion, objectMapper)).toList();
    }

    static NormalizedDocument withDocumentProfile(NormalizedDocument document,
            String requestedProfile, String resolvedProfile, String profileVersion) {
        if (document == null || !hasText(resolvedProfile)) {
            return document;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(document.metadata());
        putIfText(metadata, "requestedDocumentProfile", requestedProfile);
        putIfText(metadata, "resolvedDocumentProfile", resolvedProfile);
        putIfText(metadata, "documentProfileVersion", profileVersion);
        return NormalizedDocument.builder(document.sourceDocumentId())
                .plainText(document.plainText())
                .sourceFormat(document.sourceFormat())
                .filename(document.filename())
                .blocks(document.blocks())
                .metadata(metadata)
                .build();
    }

    private static MarkdownResource withDocumentProfile(MarkdownResource resource,
            String requestedProfile, String resolvedProfile, String profileVersion, ObjectMapper objectMapper) {
        if (resource == null
                || !MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT.equals(resource.resourceType())) {
            return resource;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(resource.metadataJson(), MAP_TYPE);
            putIfText(payload, "requestedDocumentProfile", requestedProfile);
            putIfText(payload, "resolvedDocumentProfile", resolvedProfile);
            putIfText(payload, "documentProfileVersion", profileVersion);
            NormalizedDocument document = withDocumentProfile(document(payload, objectMapper),
                    requestedProfile, resolvedProfile, profileVersion);
            payload.put("document", documentMap(document));
            return new MarkdownResource(resource.resourceId(), resource.revisionId(), resource.resourceType(),
                    resource.name(), resource.attachmentId(), writeJson(objectMapper, payload));
        } catch (RuntimeException | java.io.IOException ex) {
            return resource;
        }
    }

    static Optional<Snapshot> read(MarkdownResource resource, ObjectMapper objectMapper) {
        if (resource == null
                || !MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT.equals(resource.resourceType())) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(resource.metadataJson(), MAP_TYPE);
            if (!SCHEMA_VERSION.equals(text(payload.get("schemaVersion")))) {
                return Optional.empty();
            }
            NormalizedDocument document = document(payload, objectMapper);
            if (document.blocks().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Snapshot(
                    document,
                    text(payload.get("normalizationStatus"), STATUS_REVIEW_REQUIRED),
                    stringList(payload.get("normalizationIssues")),
                    text(payload.get("normalizationSource"), SOURCE_FALLBACK),
                    qualityMetrics(payload)));
        } catch (RuntimeException | java.io.IOException ex) {
            return Optional.empty();
        }
    }

    static Map<String, Object> payload(NormalizedDocument document, String normalizationSource, List<String> issues) {
        List<String> effectiveIssues = issues == null ? List.of() : issues.stream()
                .filter(issue -> issue != null && !issue.isBlank())
                .distinct()
                .toList();
        String status = effectiveIssues.isEmpty() && document != null && !document.blocks().isEmpty()
                ? STATUS_VALID
                : STATUS_REVIEW_REQUIRED;
        List<NormalizedBlock> blocks = document == null ? List.of() : document.blocks();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("normalizationStatus", status);
        payload.put("normalizationIssues", effectiveIssues);
        payload.put("normalizationSource", text(normalizationSource, SOURCE_FALLBACK));
        payload.put("blockCount", blocks.size());
        payload.put("contentBlockCount", countContentBlocks(blocks));
        payload.put("pageProvenanceBlockCount", countPageProvenanceBlocks(blocks));
        payload.put("pageProvenanceCoverage", ratio(countPageProvenanceBlocks(blocks), countContentBlocks(blocks)));
        payload.put("searchablePageBlockCount", countSearchablePageBlocks(blocks));
        payload.put("searchablePageCoverage", ratio(countSearchablePageBlocks(blocks), countSearchableCandidateBlocks(blocks)));
        payload.put("mathBlockCount", countMathBlocks(blocks));
        payload.put("mathPageProvenanceCoverage", ratio(countMathPageProvenanceBlocks(blocks), countMathBlocks(blocks)));
        payload.put("tableCount", count(blocks, NormalizedBlockType.TABLE));
        payload.put("imageCount", count(blocks, NormalizedBlockType.IMAGE));
        payload.put("pageCount", blocks.stream().map(NormalizedBlock::page).filter(value -> value != null).distinct().count());
        copyMetadata(payload, document, "pdfExtractionEngine");
        copyMetadata(payload, document, "extractionEngine");
        copyMetadata(payload, document, "pdfRecommendedRoute");
        copyMetadata(payload, document, "pdfActualRoute");
        copyMetadata(payload, document, "pdfEngineSelectionReason");
        copyMetadata(payload, document, "recommendedRoute");
        copyMetadata(payload, document, "actualRoute");
        copyMetadata(payload, document, "engineSelectionReason");
        copyMetadata(payload, document, "pymupdfStatus");
        copyMetadata(payload, document, "pymupdfError");
        copyMetadata(payload, document, "pdfExtractionFallbackReason");
        copyMetadata(payload, document, "fallbackApplied");
        copyMetadata(payload, document, "fallbackFrom");
        copyMetadata(payload, document, "fallbackTo");
        copyMetadata(payload, document, "fallbackReason");
        copyMetadata(payload, document, "baselineEngine");
        copyMetadata(payload, document, "pdfAnalysis");
        copyMetadata(payload, document, "ocrRequired");
        copyMetadata(payload, document, "ocrMode");
        copyMetadata(payload, document, "ocrRequestedBy");
        copyMetadata(payload, document, "ocrDecisionReason");
        copyMetadata(payload, document, "ocrApplied");
        copyMetadata(payload, document, "ocrLanguage");
        copyMetadata(payload, document, "mathMarkdownApplied");
        copyMetadata(payload, document, "mathMarkdownExpressionCount");
        copyMetadata(payload, document, "mathMarkdownEngine");
        copyMetadata(payload, document, "mathMarkdownQuality");
        copyMetadata(payload, document, "mathDocumentEngineRequired");
        copyMetadata(payload, document, "mathOcrProvider");
        copyMetadata(payload, document, "mathFallbackApplied");
        copyMetadata(payload, document, "mathHybridFormulaBlockCount");
        copyMetadata(payload, document, "mathVisionCorrectionRequested");
        copyMetadata(payload, document, "mathVisionCorrectionApplied");
        copyMetadata(payload, document, "mathVisionCorrectionProvider");
        copyMetadata(payload, document, "mathVisionCorrectionSkipReason");
        copyMetadata(payload, document, "mathVisionFormulaBlockCount");
        copyMetadata(payload, document, "koreanTextOcrApplied");
        copyMetadata(payload, document, "koreanTextOcrProvider");
        copyMetadata(payload, document, "koreanTextOcrPages");
        copyMetadata(payload, document, "koreanTextOcrRequestedPages");
        copyMetadata(payload, document, "koreanTextOcrMissingPages");
        copyMetadata(payload, document, "koreanTextOcrComplete");
        copyMetadata(payload, document, "koreanTextOcrBlockCount");
        copyMetadata(payload, document, "markdownQualityStatus");
        copyMetadata(payload, document, "markdownQualityIssues");
        copyMetadata(payload, document, "markdownQualityScore");
        copyMetadata(payload, document, "markdownQualityTargetShortLineRatio");
        copyMetadata(payload, document, "markdownQualityTargetScore");
        copyMetadata(payload, document, "normalizedShortLineCount");
        copyMetadata(payload, document, "normalizedShortLineRatio");
        copyMetadata(payload, document, "normalizedMathBlockCount");
        copyMetadata(payload, document, "normalizedQualityScore");
        copyMetadata(payload, document, "markdownShortLineCount");
        copyMetadata(payload, document, "markdownShortLineRatio");
        copyMetadata(payload, document, "markdownMathBlockCount");
        copyMetadata(payload, document, "renderedMarkdownNonBlankLineCount");
        copyMetadata(payload, document, "renderedMarkdownShortLineCount");
        copyMetadata(payload, document, "renderedMarkdownShortLineRatio");
        copyMetadata(payload, document, "renderedMarkdownFormulaLineCount");
        copyMetadata(payload, document, "renderedMarkdownNoiseLineCount");
        copyMetadata(payload, document, "renderedMarkdownMinusSuspectCount");
        copyMetadata(payload, document, "renderedMarkdownBrokenLatexDelimiter");
        copyMetadata(payload, document, "renderedMarkdownJamoLineCount");
        copyMetadata(payload, document, "renderedMarkdownJamoLineRatio");
        copyMetadata(payload, document, "ragIndexEligible");
        copyMetadata(payload, document, "qualityGateStatus");
        copyMetadata(payload, document, "pageProvenanceStatus");
        copyMetadata(payload, document, "discardedOcrNoiseCount");
        copyMetadata(payload, document, "lineMergeAppliedCount");
        copyMetadata(payload, document, "mathReplacementCount");
        copyMetadata(payload, document, "mathReplacementStrategy");
        copyMetadata(payload, document, "pageQuality");
        copyMetadata(payload, document, "requestedDocumentProfile");
        copyMetadata(payload, document, "resolvedDocumentProfile");
        copyMetadata(payload, document, "documentProfileVersion");
        putAlias(payload, "recommendedRoute", "pdfRecommendedRoute");
        putAlias(payload, "actualRoute", "pdfActualRoute");
        putAlias(payload, "engineSelectionReason", "pdfEngineSelectionReason");
        payload.putIfAbsent("markdownQualityStatus", status);
        payload.putIfAbsent("markdownQualityIssues", effectiveIssues);
        payload.putIfAbsent("markdownQualityScore", markdownQualityScore(effectiveIssues));
        payload.putIfAbsent("pageProvenanceStatus",
                ratio(countPageProvenanceBlocks(blocks), countContentBlocks(blocks)) >= 1.0d ? STATUS_VALID : STATUS_REVIEW_REQUIRED);
        payload.put("document", documentMap(document));
        return payload;
    }

    private static void putAlias(Map<String, Object> payload, String alias, String sourceKey) {
        if (!payload.containsKey(alias) && payload.containsKey(sourceKey)) {
            payload.put(alias, payload.get(sourceKey));
        }
    }

    private static double markdownQualityScore(List<String> issues) {
        if (issues == null || issues.isEmpty()) {
            return 1.0d;
        }
        double score = 1.0d;
        for (String issue : issues) {
            score -= switch (text(issue)) {
                case "MARKDOWN_BLANK", "NO_NORMALIZED_BLOCKS" -> 1.0d;
                case "FRAGMENTED_SHORT_LINES", "BROKEN_LATEX_DELIMITER" -> 0.25d;
                case "MATH_OCR_REVIEW_REQUIRED", "MATH_NOT_RENDERED_AS_MARKDOWN" -> 0.20d;
                case "PAGE_PROVENANCE_INCOMPLETE", "MATH_PAGE_PROVENANCE_REVIEW_REQUIRED" -> 0.15d;
                default -> 0.05d;
            };
        }
        return Math.max(0.0d, score);
    }

    private static void copyMetadata(Map<String, Object> payload, NormalizedDocument document, String key) {
        if (document != null && document.metadata().containsKey(key)) {
            payload.put(key, document.metadata().get(key));
        }
    }

    private static void putIfText(Map<String, Object> target, String key, String value) {
        if (hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static NormalizedDocument document(Map<String, Object> payload, ObjectMapper objectMapper) {
        Object raw = payload.get("document");
        if (raw == null) {
            return NormalizedDocument.builder("").build();
        }
        return objectMapper.convertValue(raw, NormalizedDocument.class);
    }

    private static Map<String, Object> documentMap(NormalizedDocument document) {
        if (document == null) {
            return Map.of();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sourceDocumentId", document.sourceDocumentId());
        value.put("plainText", document.plainText());
        value.put("sourceFormat", document.sourceFormat());
        value.put("filename", document.filename());
        value.put("metadata", document.metadata());
        value.put("blocks", document.blocks().stream().map(NormalizedDocumentSnapshot::blockMap).toList());
        return value;
    }

    private static Map<String, Object> blockMap(NormalizedBlock block) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", block.id());
        value.put("type", block.type().name());
        value.put("text", block.text());
        value.put("sourceRef", block.sourceRef());
        value.put("page", block.page());
        value.put("slide", block.slide());
        value.put("order", block.order());
        value.put("parentBlockId", block.parentBlockId());
        value.put("headingPath", block.headingPath());
        value.put("blockIds", block.blockIds());
        value.put("confidence", block.confidence());
        value.put("metadata", block.metadata());
        return value;
    }

    private static long count(List<NormalizedBlock> blocks, NormalizedBlockType type) {
        return blocks.stream().filter(block -> block.type() == type).count();
    }

    private static long countContentBlocks(List<NormalizedBlock> blocks) {
        return blocks.stream().filter(NormalizedBlock::hasText).count();
    }

    private static long countPageProvenanceBlocks(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .filter(NormalizedBlock::hasText)
                .filter(NormalizedDocumentSnapshot::hasPageProvenance)
                .count();
    }

    private static long countSearchableCandidateBlocks(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .filter(NormalizedBlock::hasText)
                .filter(block -> block.text().trim().length() >= 8 || looksLikeMathText(block.text()))
                .count();
    }

    private static long countSearchablePageBlocks(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .filter(NormalizedBlock::hasText)
                .filter(block -> block.text().trim().length() >= 8 || looksLikeMathText(block.text()))
                .filter(NormalizedDocumentSnapshot::hasPageProvenance)
                .count();
    }

    private static long countMathBlocks(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .filter(NormalizedBlock::hasText)
                .filter(block -> looksLikeMathText(block.text()))
                .count();
    }

    private static long countMathPageProvenanceBlocks(List<NormalizedBlock> blocks) {
        return blocks.stream()
                .filter(NormalizedBlock::hasText)
                .filter(block -> looksLikeMathText(block.text()))
                .filter(NormalizedDocumentSnapshot::hasPageProvenance)
                .count();
    }

    private static double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 1.0d;
        }
        return (double) numerator / denominator;
    }

    private static boolean hasPageProvenance(NormalizedBlock block) {
        return block.page() != null
                || sourceRefHasPage(block.sourceRef())
                || sourceRefHasPage(text(block.metadata().get("sourceRef")))
                || positiveInteger(block.metadata().get("pageFrom"))
                || positiveInteger(block.metadata().get("page"));
    }

    private static boolean sourceRefHasPage(String sourceRef) {
        return sourceRef != null && sourceRef.matches(".*page\\[\\d+].*");
    }

    private static boolean positiveInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue() > 0;
        }
        if (value == null) {
            return false;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim()) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean looksLikeMathText(String value) {
        String text = text(value);
        return text.contains("$")
                || text.contains("\\frac")
                || text.contains("\\sqrt")
                || text.matches(".*[0-9A-Za-z가-힣][=+\\-*/^][0-9A-Za-z가-힣({\\[].*")
                || text.matches(".*(^|[^A-Za-z가-힣])[xyab]\\s*(\\^\\s*\\d+|[=+\\-*/]).*")
                || text.matches(".*\\d\\s*[xyab](\\s*\\^\\s*\\d+)?.*");
    }

    private static String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (java.io.IOException ex) {
            return "{}";
        }
    }

    private static String text(Object value) {
        return text(value, "");
    }

    private static String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(String::valueOf)
                .toList();
    }

    private static Map<String, Object> qualityMetrics(Map<String, Object> payload) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        copyMetric(payload, metrics, "contentBlockCount");
        copyMetric(payload, metrics, "pageProvenanceBlockCount");
        copyMetric(payload, metrics, "pageProvenanceCoverage");
        copyMetric(payload, metrics, "searchablePageBlockCount");
        copyMetric(payload, metrics, "searchablePageCoverage");
        copyMetric(payload, metrics, "mathBlockCount");
        copyMetric(payload, metrics, "mathPageProvenanceCoverage");
        copyMetric(payload, metrics, "normalizedShortLineRatio");
        copyMetric(payload, metrics, "renderedMarkdownShortLineRatio");
        copyMetric(payload, metrics, "renderedMarkdownNoiseLineCount");
        copyMetric(payload, metrics, "renderedMarkdownMinusSuspectCount");
        copyMetric(payload, metrics, "renderedMarkdownJamoLineCount");
        copyMetric(payload, metrics, "renderedMarkdownJamoLineRatio");
        copyMetric(payload, metrics, "markdownQualityScore");
        copyMetric(payload, metrics, "ragIndexEligible");
        return Map.copyOf(metrics);
    }

    private static void copyMetric(Map<String, Object> payload, Map<String, Object> metrics, String key) {
        if (payload.containsKey(key)) {
            metrics.put(key, payload.get(key));
        }
    }

    record Snapshot(
            NormalizedDocument document,
            String normalizationStatus,
            List<String> normalizationIssues,
            String normalizationSource,
            Map<String, Object> qualityMetrics) {
    }
}
