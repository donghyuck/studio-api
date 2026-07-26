package studio.one.platform.documentmetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BuiltInDocumentMetadataSchemaRegistryTest {

    private final DocumentMetadataSchemaRegistry registry = new BuiltInDocumentMetadataSchemaRegistry();

    @Test
    void exposesDistinctBookAndAcademicSchemas() {
        assertThat(registry.require(DocumentSemanticType.BOOK).allows("isbn")).isTrue();
        assertThat(registry.require(DocumentSemanticType.BOOK).allows("doi")).isFalse();
        assertThat(registry.require(DocumentSemanticType.ACADEMIC_PAPER).allows("doi")).isTrue();
    }

    @Test
    void autoIsRequestSelectionAndNeverDetectedType() {
        DocumentMetadataClassification classification = new DocumentMetadataClassification(
                "GENERAL_DOCUMENT", "GENERAL_DOCUMENT", DocumentSemanticTypeSelection.AUTO,
                DocumentSemanticType.BOOK, null, "HUMANITIES", 0.92, "rules-v1",
                null, null, null, null);

        assertThat(classification.effectiveSemanticType()).isEqualTo(DocumentSemanticType.BOOK);
        assertThat(classification.subject()).isEqualTo("HUMANITIES");
    }

    @Test
    void projectionExcludesInferredPromptFacts() {
        DocumentMetadataField verified = new DocumentMetadataField("title", List.of("제목"), List.of("제목"), 1.0,
                DocumentMetadataProvenance.NATIVE_STRUCTURED, List.of());
        DocumentMetadataField inferred = new DocumentMetadataField("summary", List.of("추정"), List.of("추정"), 0.4,
                DocumentMetadataProvenance.INFERRED, List.of());
        DocumentMetadataArtifact artifact = new DocumentMetadataArtifact(
                "dmeta-1", "mrev-1", "v1", "native-v1", "hash",
                new DocumentMetadataClassification(null, null, DocumentSemanticTypeSelection.AUTO,
                        DocumentSemanticType.BOOK, null, "HUMANITIES", 0.9, "rules-v1",
                        null, null, null, null),
                DocumentMetadataQuality.PARTIAL, Map.of("title", verified, "summary", inferred), List.of());

        assertThat(new DocumentMetadataProjectionPolicy().promptFacts(artifact))
                .containsOnlyKeys("title");
    }

    @Test
    void evidenceMustBeBounded() {
        assertThatThrownBy(() -> new DocumentMetadataEvidence(
                "x".repeat(DocumentMetadataEvidence.MAX_TEXT_LENGTH + 1), null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compactProjectionKeepsOnlyThePublicationYear() {
        DocumentMetadataField date = new DocumentMetadataField(
                "publicationDate", List.of("1951-07-16"), List.of("1951-07-16"), 1.0,
                DocumentMetadataProvenance.NATIVE_STRUCTURED, List.of());
        DocumentMetadataArtifact artifact = new DocumentMetadataArtifact(
                "dmeta-2", "mrev-2", "v1", "native-v1", "hash",
                new DocumentMetadataClassification(null, null, DocumentSemanticTypeSelection.AUTO,
                        DocumentSemanticType.BOOK, null, "HUMANITIES", 0.9, "rules-v1",
                        null, null, null, null),
                DocumentMetadataQuality.COMPLETE, Map.of("publicationDate", date), List.of());

        assertThat(new DocumentMetadataProjectionPolicy().compact(artifact))
                .containsEntry("docPublicationYear", "1951");
    }
}
