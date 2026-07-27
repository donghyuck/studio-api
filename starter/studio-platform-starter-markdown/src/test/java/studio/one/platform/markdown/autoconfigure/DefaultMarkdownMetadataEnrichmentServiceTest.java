package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.documentmetadata.BuiltInDocumentMetadataSchemaRegistry;
import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentSemanticType;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;

class DefaultMarkdownMetadataEnrichmentServiceTest {

    @Test
    void sourceVerificationRequiresEveryProposedValueToAppearInTheExactEvidence() {
        assertThat(DefaultMarkdownMetadataEnrichmentService.valuesSupported(
                List.of("J. D. Salinger", "1951"),
                "J. D. Salinger, published in 1951")).isTrue();
        assertThat(DefaultMarkdownMetadataEnrichmentService.valuesSupported(
                List.of("J. D. Salinger", "1965"),
                "J. D. Salinger, published in 1951")).isFalse();
    }

    @Test
    void rejectsDirectContactAndGovernmentIdentifiers() {
        assertThat(DefaultMarkdownMetadataEnrichmentService.isSensitiveValue("author@example.com")).isTrue();
        assertThat(DefaultMarkdownMetadataEnrichmentService.isSensitiveValue("010-1234-5678")).isTrue();
        assertThat(DefaultMarkdownMetadataEnrichmentService.isSensitiveValue("900101-1234567")).isTrue();
        assertThat(DefaultMarkdownMetadataEnrichmentService.isSensitiveValue("ISBN 9780316769488")).isFalse();
    }

    @Test
    void classifiesHumanitiesEpubAsBookAndPersistsNativeMetadataOnce() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownRepository repository = mock(MarkdownRepository.class);
        NormalizedDocument document = NormalizedDocument.builder("mdoc-1")
                .sourceFormat("epub")
                .filename("humanities.epub")
                .plainText("인문학의 역사 철학과 문학")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.TITLE, "인문학의 역사")
                        .id("block-1")
                        .sourceRef("epub:chapter.xhtml#element[0]")
                        .order(0)
                        .build()))
                .metadata(Map.of(
                        "title", "인문학의 역사",
                        "authors", List.of("홍길동"),
                        "publisher", "테스트 출판사",
                        "publicationDate", "2026-07-24",
                        "email", "do-not-store@example.com"))
                .build();
        MarkdownResource normalized = NormalizedDocumentSnapshot.resource(
                "mrev-1", document, NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        when(repository.findResource("mrev-1", MarkdownNormalizationPort.RESOURCE_TYPE_NORMALIZED_DOCUMENT))
                .thenReturn(Optional.of(normalized));
        when(repository.findResource("mrev-1", "DOCUMENT_METADATA")).thenReturn(Optional.empty());

        MarkdownProperties properties = new MarkdownProperties();
        DefaultMarkdownMetadataEnrichmentService service = new DefaultMarkdownMetadataEnrichmentService(
                repository,
                objectMapper,
                new BuiltInDocumentMetadataSchemaRegistry(),
                null,
                properties.getMetadata());
        MarkdownPipelineOptions options = new MarkdownPipelineOptions(
                false, false, false,
                null, null, null, null, null, null, null,
                null, null, null, null,
                false, null, false, null, null, null,
                null, null, null, null,
                "GENERAL_DOCUMENT", null, null, null,
                "AUTO", "OFF");

        service.enrich(revision(), options);

        ArgumentCaptor<MarkdownResource> captor = ArgumentCaptor.forClass(MarkdownResource.class);
        verify(repository).upsertResource(captor.capture());
        MarkdownResource resource = captor.getValue();
        DocumentMetadataArtifact artifact = objectMapper.readValue(
                resource.metadataJson(), DocumentMetadataArtifact.class);
        assertThat(artifact.classification().effectiveSemanticType()).isEqualTo(DocumentSemanticType.BOOK);
        assertThat(artifact.classification().subject()).isEqualTo("HUMANITIES");
        assertThat(artifact.fields()).containsKeys("title", "authors", "publisher", "publicationDate");
        assertThat(artifact.fields()).doesNotContainKey("email");
        assertThat(resource.resourceId()).startsWith("mres-metadata-");
    }

    private static MarkdownRevision revision() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new MarkdownRevision(
                "mrev-1", "mdoc-1", 9L, null, null,
                "TEXTRACT", "native", "{}", "options-hash", "source-hash", "content-hash",
                "인문학의 역사", "humanities.epub", "epub", "attachment", "9",
                MarkdownRevisionStatus.COMPLETED, null, null, now, now, now, now);
    }
}
