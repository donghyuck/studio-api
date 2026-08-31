package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.LocationScheme;
import studio.one.platform.ai.core.rag.usability.DocumentUsabilityEvidence.QualityStatus;
import studio.one.platform.ai.core.rag.usability.MeasurementState;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;

class MarkdownRagObjectUsabilityEvidenceContributorTest {

    @Test
    void keepsLowScoreEligibleAndTreatsEpubPagesAsNotApplicable() {
        MarkdownRepository repository = repository("epub", """
                {"schemaVersion":"normalized-document-v1","markdownQualityStatus":"REVIEW_REQUIRED",
                 "markdownQualityScore":0.7,"markdownQualityIssues":["FALLBACK_USED"],
                 "ragIndexEligible":true,"qualityGateStatus":"REVIEW_REQUIRED"}
                """, new MarkdownLocator("loc-1", "mrev-1", "NORMALIZED_BLOCK", 1, null,
                0, 20, "epub:OEBPS/chapter1.xhtml#element[3]", "{}", null, null, null));

        var evidence = contributor(repository).contribute("attachment", "17").orElseThrow();

        assertThat(evidence.quality().score().value()).isEqualTo(0.7d);
        assertThat(evidence.quality().status()).isEqualTo(QualityStatus.REVIEW_REQUIRED);
        assertThat(evidence.quality().blocking()).isFalse();
        assertThat(evidence.location().scheme()).isEqualTo(LocationScheme.EPUB_RESOURCE_ELEMENT);
        assertThat(evidence.location().pageCoverage().state()).isEqualTo(MeasurementState.NOT_APPLICABLE);
        assertThat(evidence.location().samples().get(0).resourcePath()).isEqualTo("OEBPS/chapter1.xhtml");
        assertThat(evidence.location().samples().get(0).elementIndex()).isEqualTo(3);
    }

    @Test
    void exposesPdfPageAndMeasuredZeroCoverage() {
        MarkdownRepository repository = repository("pdf", """
                {"schemaVersion":"normalized-document-v1","markdownQualityStatus":"VALID",
                 "markdownQualityScore":1.0,"ragIndexEligible":true,"qualityGateStatus":"PASSED",
                 "pageProvenanceCoverage":0.0}
                """, new MarkdownLocator("loc-1", "mrev-1", "page", 2, null,
                0, 20, "page[2]/block[0]", "{}", 2, null, List.of(1, 2, 3, 4)));

        var evidence = contributor(repository).contribute("attachment", "17").orElseThrow();

        assertThat(evidence.location().scheme()).isEqualTo(LocationScheme.PAGE_BBOX);
        assertThat(evidence.location().pageCoverage().state()).isEqualTo(MeasurementState.MEASURED);
        assertThat(evidence.location().pageCoverage().value()).isZero();
        assertThat(evidence.location().samples().get(0).page()).isEqualTo(2);
    }

    @Test
    void mapsSpreadsheetToSheetAndCellLocation() {
        MarkdownRepository repository = repository("xlsx", """
                {"schemaVersion":"normalized-document-v1","markdownQualityStatus":"VALID",
                 "markdownQualityScore":0.9,"ragIndexEligible":true,"qualityGateStatus":"PASSED"}
                """, new MarkdownLocator("loc-1", "mrev-1", "NORMALIZED_BLOCK", 1, null,
                0, 20, "sheet[2]/row[4]/cell[3]", "{\"sheetName\":\"매출\",\"cellAddress\":\"C4\"}",
                null, null, null));

        var location = contributor(repository).contribute("attachment", "17").orElseThrow().location();

        assertThat(location.scheme()).isEqualTo(LocationScheme.SHEET_CELL_RANGE);
        assertThat(location.pageCoverage().state()).isEqualTo(MeasurementState.NOT_APPLICABLE);
        assertThat(location.samples().get(0).sheetName()).isEqualTo("매출");
        assertThat(location.samples().get(0).sheetIndex()).isEqualTo(2);
        assertThat(location.samples().get(0).cellRange()).isEqualTo("C4");
    }

    @Test
    void doesNotAssumeEligibilityWhenLegacySnapshotOmitsIt() {
        MarkdownRepository repository = repository("pdf", """
                {"schemaVersion":"normalized-document-v1","markdownQualityStatus":"VALID",
                 "markdownQualityScore":1.0,"pageProvenanceCoverage":1.0}
                """, new MarkdownLocator("loc-1", "mrev-1", "page", 1, null,
                0, 20, "page[1]/block[0]", "{}", 1, null, null));

        var eligibility = contributor(repository).contribute("attachment", "17").orElseThrow().eligibility();

        assertThat(eligibility.state()).isEqualTo(MeasurementState.NOT_MEASURED);
        assertThat(eligibility.eligible()).isNull();
        assertThat(eligibility.reasonCodes()).containsExactly("RAG_INDEX_ELIGIBILITY_UNAVAILABLE");
    }

    private MarkdownRagObjectUsabilityEvidenceContributor contributor(MarkdownRepository repository) {
        return new MarkdownRagObjectUsabilityEvidenceContributor(repository, new ObjectMapper());
    }

    private MarkdownRepository repository(String sourceFormat, String snapshotJson, MarkdownLocator locator) {
        MarkdownRepository repository = mock(MarkdownRepository.class);
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        MarkdownDocument document = new MarkdownDocument("mdoc-1", 17L, "mrev-1", now, now);
        MarkdownRevision revision = new MarkdownRevision(
                "mrev-1", "mdoc-1", 17L, null, null,
                "TEXTRACT", "native", "{}", "options", "source-hash", "content-hash", "content",
                "source." + sourceFormat, sourceFormat, "attachment", "17",
                MarkdownRevisionStatus.COMPLETED, null, null, now, now, now, now);
        MarkdownResource resource = new MarkdownResource(
                "mres-1", "mrev-1", MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT,
                "normalized-document.json", null, snapshotJson);
        when(repository.findDocumentBySourceAttachmentId(17L)).thenReturn(Optional.of(document));
        when(repository.findRevision("mrev-1")).thenReturn(Optional.of(revision));
        when(repository.findResource("mrev-1", MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT))
                .thenReturn(Optional.of(resource));
        when(repository.findLocators("mrev-1")).thenReturn(List.of(locator));
        return repository;
    }
}
