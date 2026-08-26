package studio.one.platform.markdown;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentMetadataClassification;
import studio.one.platform.documentmetadata.DocumentMetadataField;
import studio.one.platform.documentmetadata.DocumentMetadataProjectionPolicy;
import studio.one.platform.documentmetadata.DocumentMetadataProvenance;
import studio.one.platform.documentmetadata.DocumentMetadataQuality;
import studio.one.platform.documentmetadata.DocumentSemanticType;
import studio.one.platform.documentmetadata.DocumentSemanticTypeSelection;
import studio.one.platform.markdown.application.DocumentMetadataSummaryView;

class DocumentMetadataSummaryViewTest {

    @Test
    void exposesOnlyCompactBoundedMetadata() {
        DocumentMetadataArtifact artifact = new DocumentMetadataArtifact(
                "metadata:mrev-1",
                "mrev-1",
                "document-metadata-v1",
                "extractor-v2",
                "fingerprint",
                new DocumentMetadataClassification(
                        null,
                        null,
                        DocumentSemanticTypeSelection.AUTO,
                        DocumentSemanticType.REPORT,
                        null,
                        "PUBLIC_POLICY",
                        0.87d,
                        "classifier-v1",
                        null,
                        null,
                        null,
                        null),
                DocumentMetadataQuality.COMPLETE,
                Map.of(
                        "title", field("title", List.of("2026 업무 보고서")),
                        "language", field("language", List.of("ko")),
                        "summary", field("summary", List.of("핵심 추진 현황과 후속 과제를 정리한 보고서입니다.")),
                        "keywords", field("keywords", List.of("업무", "보고서", "후속 과제"))),
                List.of());

        DocumentMetadataSummaryView summary = DocumentMetadataSummaryView.from(
                "mdoc-19",
                artifact,
                new DocumentMetadataProjectionPolicy());

        assertThat(summary.documentId()).isEqualTo("mdoc-19");
        assertThat(summary.revisionId()).isEqualTo("mrev-1");
        assertThat(summary.semanticType()).isEqualTo("REPORT");
        assertThat(summary.subject()).isEqualTo("PUBLIC_POLICY");
        assertThat(summary.language()).isEqualTo("ko");
        assertThat(summary.title()).isEqualTo("2026 업무 보고서");
        assertThat(summary.summary()).isEqualTo("핵심 추진 현황과 후속 과제를 정리한 보고서입니다.");
        assertThat(summary.keywords()).containsExactly("업무", "보고서", "후속 과제");
    }

    private static DocumentMetadataField field(String fieldId, List<String> values) {
        return new DocumentMetadataField(
                fieldId,
                values,
                values,
                0.95d,
                DocumentMetadataProvenance.SOURCE_VERIFIED,
                List.of());
    }
}
