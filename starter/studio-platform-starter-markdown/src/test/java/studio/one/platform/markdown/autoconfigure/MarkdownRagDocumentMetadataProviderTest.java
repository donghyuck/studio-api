package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentMetadataClassification;
import studio.one.platform.documentmetadata.DocumentMetadataEvidence;
import studio.one.platform.documentmetadata.DocumentMetadataField;
import studio.one.platform.documentmetadata.DocumentMetadataProvenance;
import studio.one.platform.documentmetadata.DocumentMetadataQuality;
import studio.one.platform.documentmetadata.DocumentSemanticType;
import studio.one.platform.documentmetadata.DocumentSemanticTypeSelection;
import studio.one.platform.markdown.application.MarkdownDocumentMetadataService;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownResource;

class MarkdownRagDocumentMetadataProviderTest {

    @Test
    void exposesNativeMetadataEvidenceWithSourceRefAndNoBlockId() throws Exception {
        MarkdownRepository repository = mock(MarkdownRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Instant now = Instant.parse("2026-07-28T00:00:00Z");
        MarkdownDocument document = new MarkdownDocument("mdoc-13", 13L, "mrev-13", now, now);
        DocumentMetadataField authors = new DocumentMetadataField(
                "authors",
                List.of("폴 클라인먼 지음 | 이세진 옮김"),
                List.of("폴 클라인먼 지음 | 이세진 옮김"),
                1.0d,
                DocumentMetadataProvenance.NATIVE_STRUCTURED,
                List.of(new DocumentMetadataEvidence(
                        "폴 클라인먼 지음 | 이세진 옮김",
                        "metadata:authors",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));
        DocumentMetadataArtifact artifact = new DocumentMetadataArtifact(
                "metadata:mrev-13",
                "mrev-13",
                "document-metadata-v1",
                "native-v1",
                "fingerprint",
                new DocumentMetadataClassification(
                        null,
                        null,
                        DocumentSemanticTypeSelection.AUTO,
                        DocumentSemanticType.BOOK,
                        DocumentSemanticType.BOOK,
                        "HUMANITIES",
                        1.0d,
                        "native-v1",
                        null,
                        null,
                        null,
                        null),
                DocumentMetadataQuality.COMPLETE,
                Map.of("authors", authors),
                List.of());
        MarkdownResource resource = new MarkdownResource(
                "metadata:mrev-13",
                "mrev-13",
                MarkdownDocumentMetadataService.RESOURCE_TYPE,
                "document-metadata.json",
                null,
                objectMapper.writeValueAsString(artifact));
        when(repository.findDocumentBySourceAttachmentId(13L)).thenReturn(Optional.of(document));
        when(repository.findResource("mrev-13", MarkdownDocumentMetadataService.RESOURCE_TYPE))
                .thenReturn(Optional.of(resource));

        var results = new MarkdownRagDocumentMetadataProvider(repository, objectMapper)
                .find("attachment", "13");

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.content()).isEqualTo("폴 클라인먼 지음 | 이세진 옮김");
            assertThat(result.metadata())
                    .containsEntry("sourceRef", "metadata:authors")
                    .containsEntry("evidenceKind", "DOCUMENT_METADATA")
                    .doesNotContainKey("blockIds");
        });
    }
}
