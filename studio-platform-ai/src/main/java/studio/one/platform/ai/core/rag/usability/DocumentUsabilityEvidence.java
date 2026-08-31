package studio.one.platform.ai.core.rag.usability;

import java.util.List;
import java.util.Map;

/**
 * Revision-scoped document evidence contributed by a source-specific adapter.
 */
public record DocumentUsabilityEvidence(
        String objectType,
        String objectId,
        String documentId,
        String revisionId,
        String sourceContentHash,
        String sourceFormat,
        QualityEvidence quality,
        EligibilityEvidence eligibility,
        LocationEvidence location) {

    public DocumentUsabilityEvidence {
        objectType = normalize(objectType);
        objectId = normalize(objectId);
        documentId = normalize(documentId);
        revisionId = normalize(revisionId);
        sourceContentHash = normalize(sourceContentHash);
        sourceFormat = normalize(sourceFormat);
        quality = quality == null ? QualityEvidence.notMeasured("QUALITY_EVIDENCE_UNAVAILABLE") : quality;
        eligibility = eligibility == null
                ? EligibilityEvidence.notMeasured("ELIGIBILITY_EVIDENCE_UNAVAILABLE") : eligibility;
        location = location == null ? LocationEvidence.notMeasured("LOCATION_EVIDENCE_UNAVAILABLE") : location;
    }

    public record EligibilityEvidence(
            MeasurementState state,
            Boolean eligible,
            List<String> reasonCodes) {

        public EligibilityEvidence {
            state = state == null ? MeasurementState.NOT_MEASURED : state;
            if (state == MeasurementState.MEASURED && eligible == null) {
                throw new IllegalArgumentException("Measured eligibility requires a value");
            }
            if (state != MeasurementState.MEASURED && eligible != null) {
                throw new IllegalArgumentException("Only measured eligibility may contain a value");
            }
            reasonCodes = normalized(reasonCodes);
        }

        public static EligibilityEvidence measured(boolean eligible, List<String> reasonCodes) {
            return new EligibilityEvidence(MeasurementState.MEASURED, eligible, reasonCodes);
        }

        public static EligibilityEvidence notMeasured(String reasonCode) {
            return new EligibilityEvidence(MeasurementState.NOT_MEASURED, null, List.of(reasonCode));
        }

        public static EligibilityEvidence failed(String reasonCode) {
            return new EligibilityEvidence(MeasurementState.FAILED, null, List.of(reasonCode));
        }
    }

    public record QualityEvidence(
            MeasurementState state,
            QualityStatus status,
            MeasuredValue<Double> score,
            boolean blocking,
            List<String> reasonCodes) {

        public QualityEvidence {
            state = state == null ? MeasurementState.NOT_MEASURED : state;
            status = status == null ? QualityStatus.UNKNOWN : status;
            score = score == null ? MeasuredValue.notMeasured("QUALITY_SCORE_UNAVAILABLE") : score;
            reasonCodes = normalized(reasonCodes);
        }

        public static QualityEvidence notMeasured(String reasonCode) {
            return new QualityEvidence(
                    MeasurementState.NOT_MEASURED,
                    QualityStatus.UNKNOWN,
                    MeasuredValue.notMeasured(reasonCode),
                    false,
                    List.of(reasonCode));
        }
    }

    public record LocationEvidence(
            MeasurementState state,
            LocationScheme scheme,
            MeasuredValue<Double> coverage,
            MeasuredValue<Double> pageCoverage,
            List<String> reasonCodes,
            List<DocumentLocationRef> samples) {

        public LocationEvidence {
            state = state == null ? MeasurementState.NOT_MEASURED : state;
            scheme = scheme == null ? LocationScheme.UNKNOWN : scheme;
            coverage = coverage == null ? MeasuredValue.notMeasured("LOCATION_COVERAGE_UNAVAILABLE") : coverage;
            pageCoverage = pageCoverage == null
                    ? MeasuredValue.notMeasured("PAGE_COVERAGE_UNAVAILABLE")
                    : pageCoverage;
            reasonCodes = normalized(reasonCodes);
            samples = samples == null ? List.of() : List.copyOf(samples);
        }

        public static LocationEvidence notMeasured(String reasonCode) {
            return new LocationEvidence(
                    MeasurementState.NOT_MEASURED,
                    LocationScheme.UNKNOWN,
                    MeasuredValue.notMeasured(reasonCode),
                    MeasuredValue.notMeasured(reasonCode),
                    List.of(reasonCode),
                    List.of());
        }
    }

    public record DocumentLocationRef(
            LocationScheme scheme,
            String sourceRef,
            Integer page,
            Integer slide,
            String resourcePath,
            Integer elementIndex,
            String sheetName,
            Integer sheetIndex,
            String cellRange,
            Integer startOffset,
            Integer endOffset,
            Object bbox,
            Map<String, Object> attributes) {

        public DocumentLocationRef {
            scheme = scheme == null ? LocationScheme.UNKNOWN : scheme;
            sourceRef = normalize(sourceRef);
            resourcePath = normalize(resourcePath);
            sheetName = normalize(sheetName);
            cellRange = normalize(cellRange);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    public enum QualityStatus {
        PASSED,
        REVIEW_REQUIRED,
        FAILED,
        UNKNOWN
    }

    public enum LocationScheme {
        PAGE,
        PAGE_BBOX,
        EPUB_RESOURCE_ELEMENT,
        SLIDE,
        SLIDE_SHAPE,
        SHEET,
        SHEET_CELL_RANGE,
        SECTION,
        TEXT_OFFSET,
        SOURCE_REF,
        UNKNOWN
    }

    private static List<String> normalized(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
