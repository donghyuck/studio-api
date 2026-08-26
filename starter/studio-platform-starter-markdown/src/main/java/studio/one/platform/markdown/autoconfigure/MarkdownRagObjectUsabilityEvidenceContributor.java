package studio.one.platform.markdown.autoconfigure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.DocumentLocationRef;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.EligibilityEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.LocationEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.LocationScheme;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.QualityEvidence;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.QualityStatus;
import studio.one.platform.ai.core.rag.usability.MeasuredValue;
import studio.one.platform.ai.core.rag.usability.MeasurementState;
import studio.one.platform.ai.core.rag.usability.RagObjectUsabilityEvidenceContributor;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownRevision;

/**
 * Projects the current attachment revision into the common usability contract.
 */
final class MarkdownRagObjectUsabilityEvidenceContributor implements RagObjectUsabilityEvidenceContributor {
    private static final int SAMPLE_LIMIT = 10;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern EPUB_REF = Pattern.compile("^epub:(.+)#element\\[(\\d+)]$");
    private static final Pattern SLIDE_REF = Pattern.compile(".*slide\\[(\\d+)](?:/shape\\[(\\d+)])?.*");
    private static final Pattern SHEET_REF = Pattern.compile(
            ".*sheet\\[(\\d+)](?:/row\\[(\\d+)])?(?:/cell\\[(\\d+)])?.*");

    private final MarkdownRepository repository;
    private final ObjectMapper objectMapper;

    MarkdownRagObjectUsabilityEvidenceContributor(MarkdownRepository repository, ObjectMapper objectMapper) {
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public boolean supports(String objectType, String objectId) {
        if (!"attachment".equalsIgnoreCase(objectType)) {
            return false;
        }
        try {
            Long.parseLong(objectId);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    public Optional<DocumentUsabilityEvidence> contribute(String objectType, String objectId) {
        long attachmentId = Long.parseLong(objectId);
        MarkdownDocument document = repository.findDocumentBySourceAttachmentId(attachmentId).orElse(null);
        if (document == null || document.currentRevisionId() == null) {
            return Optional.empty();
        }
        MarkdownRevision revision = repository.findRevision(document.currentRevisionId()).orElse(null);
        if (revision == null) {
            return Optional.empty();
        }
        SnapshotRead snapshot = readSnapshot(revision.revisionId());
        List<MarkdownLocator> locators = repository.findLocators(revision.revisionId());
        return Optional.of(new DocumentUsabilityEvidence(
                objectType,
                objectId,
                document.documentId(),
                revision.revisionId(),
                revision.sourceContentHash(),
                revision.sourceFormat(),
                quality(snapshot),
                eligibility(snapshot),
                location(revision.sourceFormat(), snapshot, locators)));
    }

    private EligibilityEvidence eligibility(SnapshotRead snapshot) {
        if (snapshot.state() == MeasurementState.FAILED) {
            return EligibilityEvidence.failed(snapshot.reasonCode());
        }
        if (snapshot.state() != MeasurementState.MEASURED) {
            return EligibilityEvidence.notMeasured(snapshot.reasonCode());
        }
        Boolean eligible = booleanValue(snapshot.payload().get("ragIndexEligible"));
        if (eligible == null) {
            return EligibilityEvidence.notMeasured("RAG_INDEX_ELIGIBILITY_UNAVAILABLE");
        }
        return EligibilityEvidence.measured(
                eligible,
                List.of(eligible ? "NO_BLOCKING_QUALITY_FAILURE" : "BLOCKING_QUALITY_FAILURE"));
    }

    private SnapshotRead readSnapshot(String revisionId) {
        return repository.findResource(revisionId, MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT)
                .map(resource -> {
                    try {
                        return new SnapshotRead(MeasurementState.MEASURED,
                                objectMapper.readValue(resource.metadataJson(), MAP_TYPE), null);
                    } catch (RuntimeException ex) {
                        return new SnapshotRead(MeasurementState.FAILED, Map.of(), "NORMALIZED_SNAPSHOT_INVALID");
                    }
                })
                .orElseGet(() -> new SnapshotRead(
                        MeasurementState.NOT_MEASURED, Map.of(), "NORMALIZED_SNAPSHOT_NOT_FOUND"));
    }

    private QualityEvidence quality(SnapshotRead snapshot) {
        if (snapshot.state() == MeasurementState.FAILED) {
            return new QualityEvidence(
                    MeasurementState.FAILED,
                    QualityStatus.UNKNOWN,
                    MeasuredValue.failed(snapshot.reasonCode()),
                    false,
                    List.of(snapshot.reasonCode()));
        }
        if (snapshot.state() != MeasurementState.MEASURED) {
            return QualityEvidence.notMeasured(snapshot.reasonCode());
        }
        String gateStatus = text(snapshot.payload().get("qualityGateStatus"));
        String qualityStatus = text(snapshot.payload().get("markdownQualityStatus"));
        boolean blocking = Boolean.FALSE.equals(booleanValue(snapshot.payload().get("ragIndexEligible")))
                || "BLOCKED".equalsIgnoreCase(gateStatus);
        QualityStatus status = blocking
                ? QualityStatus.FAILED
                : "REVIEW_REQUIRED".equalsIgnoreCase(gateStatus)
                        || "REVIEW_REQUIRED".equalsIgnoreCase(qualityStatus)
                                ? QualityStatus.REVIEW_REQUIRED
                                : "VALID".equalsIgnoreCase(qualityStatus)
                                        ? QualityStatus.PASSED
                                        : QualityStatus.UNKNOWN;
        Double score = number(snapshot.payload().get("markdownQualityScore"));
        List<String> reasons = stringList(snapshot.payload().get("markdownQualityIssues"));
        if (blocking) {
            reasons = appended(reasons, "QUALITY_GATE_BLOCKED");
        }
        if (status == QualityStatus.UNKNOWN) {
            reasons = appended(reasons, "QUALITY_STATUS_UNKNOWN");
        }
        return new QualityEvidence(
                MeasurementState.MEASURED,
                status,
                score == null
                        ? MeasuredValue.notMeasured("QUALITY_SCORE_UNAVAILABLE")
                        : MeasuredValue.measured(score),
                blocking,
                reasons);
    }

    private LocationEvidence location(
            String sourceFormat,
            SnapshotRead snapshot,
            List<MarkdownLocator> locators) {
        List<MarkdownLocator> safeLocators = locators == null ? List.of() : locators;
        List<DocumentLocationRef> samples = safeLocators.stream()
                .limit(SAMPLE_LIMIT)
                .map(locator -> locationRef(sourceFormat, locator))
                .toList();
        LocationScheme scheme = samples.stream()
                .map(DocumentLocationRef::scheme)
                .filter(value -> value != LocationScheme.UNKNOWN)
                .findFirst()
                .orElseGet(() -> defaultScheme(sourceFormat));
        MeasuredValue<Double> coverage = safeLocators.isEmpty()
                ? MeasuredValue.notMeasured("LOCATORS_NOT_FOUND")
                : MeasuredValue.measured(safeLocators.stream()
                        .map(locator -> locationRef(sourceFormat, locator))
                        .filter(ref -> ref.scheme() != LocationScheme.UNKNOWN)
                        .count() / (double) safeLocators.size());
        MeasuredValue<Double> pageCoverage = pageCoverage(sourceFormat, snapshot);
        List<String> reasons = new ArrayList<>();
        if (safeLocators.isEmpty()) {
            reasons.add("LOCATORS_NOT_FOUND");
        }
        if (pageCoverage.state() == MeasurementState.NOT_APPLICABLE) {
            reasons.add(pageCoverage.reasonCode());
        }
        MeasurementState state = safeLocators.isEmpty()
                ? MeasurementState.NOT_MEASURED : MeasurementState.MEASURED;
        return new LocationEvidence(state, scheme, coverage, pageCoverage, reasons, samples);
    }

    private MeasuredValue<Double> pageCoverage(String sourceFormat, SnapshotRead snapshot) {
        String format = format(sourceFormat);
        if ("EPUB".equals(format)) {
            return MeasuredValue.notApplicable("FORMAT_HAS_NO_STABLE_PAGES");
        }
        if ("PPT".equals(format) || "PPTX".equals(format)) {
            return MeasuredValue.notApplicable("FORMAT_USES_SLIDES");
        }
        if ("XLS".equals(format) || "XLSX".equals(format) || "EXCEL".equals(format)) {
            return MeasuredValue.notApplicable("FORMAT_USES_SHEETS");
        }
        if (snapshot.state() == MeasurementState.FAILED) {
            return MeasuredValue.failed(snapshot.reasonCode());
        }
        Double coverage = number(snapshot.payload().get("pageProvenanceCoverage"));
        return coverage == null
                ? MeasuredValue.notMeasured("PAGE_COVERAGE_UNAVAILABLE")
                : MeasuredValue.measured(coverage);
    }

    private DocumentLocationRef locationRef(String sourceFormat, MarkdownLocator locator) {
        String format = format(sourceFormat);
        String sourceRef = locator.sourceRef();
        Map<String, Object> metadata = metadata(locator.metadataJson());
        if ("PDF".equals(format) && locator.page() != null) {
            return ref(locator.bbox() == null ? LocationScheme.PAGE : LocationScheme.PAGE_BBOX,
                    locator, locator.page(), null, null, null, null, null, null, metadata);
        }
        Matcher epub = EPUB_REF.matcher(sourceRef == null ? "" : sourceRef);
        if ("EPUB".equals(format) && epub.matches()) {
            return ref(LocationScheme.EPUB_RESOURCE_ELEMENT, locator, null, null,
                    epub.group(1), integer(epub.group(2)), null, null, null, metadata);
        }
        Matcher slide = SLIDE_REF.matcher(sourceRef == null ? "" : sourceRef);
        if (("PPT".equals(format) || "PPTX".equals(format)) && slide.matches()) {
            Integer slideNo = locator.slide() == null ? integer(slide.group(1)) : locator.slide();
            LocationScheme scheme = slide.group(2) == null ? LocationScheme.SLIDE : LocationScheme.SLIDE_SHAPE;
            return ref(scheme, locator, null, slideNo, null, null, null, null, null, metadata);
        }
        Matcher sheet = SHEET_REF.matcher(sourceRef == null ? "" : sourceRef);
        if (("XLS".equals(format) || "XLSX".equals(format) || "EXCEL".equals(format)) && sheet.matches()) {
            String sheetName = firstText(metadata, "sheetName", "sheet");
            Integer sheetIndex = integer(sheet.group(1));
            String cellRange = firstText(metadata, "cellRange", "cellAddress", "range");
            LocationScheme scheme = cellRange == null && sheet.group(3) == null
                    ? LocationScheme.SHEET : LocationScheme.SHEET_CELL_RANGE;
            return ref(scheme, locator, null, null, null, null, sheetName, sheetIndex, cellRange, metadata);
        }
        if (locator.startOffset() >= 0 && locator.endOffset() >= locator.startOffset()) {
            return new DocumentLocationRef(LocationScheme.TEXT_OFFSET, sourceRef, locator.page(), locator.slide(),
                    null, null, null, null, null, locator.startOffset(), locator.endOffset(), locator.bbox(), metadata);
        }
        return ref(sourceRef == null ? LocationScheme.UNKNOWN : LocationScheme.SOURCE_REF,
                locator, locator.page(), locator.slide(), null, null, null, null, null, metadata);
    }

    private DocumentLocationRef ref(
            LocationScheme scheme,
            MarkdownLocator locator,
            Integer page,
            Integer slide,
            String resourcePath,
            Integer elementIndex,
            String sheetName,
            Integer sheetIndex,
            String cellRange,
            Map<String, Object> metadata) {
        return new DocumentLocationRef(scheme, locator.sourceRef(), page, slide, resourcePath, elementIndex,
                sheetName, sheetIndex, cellRange, null, null, locator.bbox(), metadata);
    }

    private LocationScheme defaultScheme(String sourceFormat) {
        return switch (format(sourceFormat)) {
            case "PDF" -> LocationScheme.PAGE;
            case "EPUB" -> LocationScheme.EPUB_RESOURCE_ELEMENT;
            case "PPT", "PPTX" -> LocationScheme.SLIDE;
            case "XLS", "XLSX", "EXCEL" -> LocationScheme.SHEET;
            default -> LocationScheme.UNKNOWN;
        };
    }

    private Map<String, Object> metadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(json, MAP_TYPE);
            return value == null ? Map.of() : new LinkedHashMap<>(value);
        } catch (RuntimeException ex) {
            return Map.of("metadataStatus", "INVALID");
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        iterable.forEach(item -> {
            String text = text(item);
            if (text != null) {
                values.add(text);
            }
        });
        return values.stream().distinct().toList();
    }

    private List<String> appended(List<String> values, String value) {
        List<String> result = new ArrayList<>(values);
        result.add(value);
        return result.stream().distinct().toList();
    }

    private String firstText(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String value = text(values.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? null : Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private Integer integer(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String format(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private record SnapshotRead(MeasurementState state, Map<String, Object> payload, String reasonCode) {
    }
}
