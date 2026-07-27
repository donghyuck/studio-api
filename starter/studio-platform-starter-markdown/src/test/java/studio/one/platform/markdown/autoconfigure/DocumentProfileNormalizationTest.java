package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.markdown.application.port.MarkdownNormalizationPort;
import studio.one.platform.markdown.domain.MarkdownResource;

class DocumentProfileNormalizationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void addsProfileMetadataToExistingNativeSnapshot() {
        NormalizedDocument document = NormalizedDocument.builder("attachment-6")
                .plainText("본문")
                .sourceFormat("pdf")
                .filename("math.pdf")
                .blocks(List.of(NormalizedBlock.builder(NormalizedBlockType.PARAGRAPH, "본문")
                        .id("block-1")
                        .page(1)
                        .build()))
                .build();
        MarkdownResource resource = NormalizedDocumentSnapshot.resource(
                "mrev-1", document, NormalizedDocumentSnapshot.SOURCE_NATIVE, List.of(), objectMapper);
        DefaultMarkdownNormalizationPort port = new DefaultMarkdownNormalizationPort(
                new MarkdownTextBlockParser(), new NormalizedMarkdownRenderer(), objectMapper);

        var result = port.normalize(new MarkdownNormalizationPort.NormalizationRequest(
                "mrev-1", "pdf", "math.pdf", "본문", List.of(), List.of(resource),
                NormalizedDocumentSnapshot.SOURCE_NATIVE, "MATH_TEXTBOOK", "MATH_TEXTBOOK", "v1"));

        var snapshot = NormalizedDocumentSnapshot.read(result.resources().get(0), objectMapper).orElseThrow();
        assertThat(snapshot.document().metadata())
                .containsEntry("requestedDocumentProfile", "MATH_TEXTBOOK")
                .containsEntry("resolvedDocumentProfile", "MATH_TEXTBOOK")
                .containsEntry("documentProfileVersion", "v1");
    }

    @Test
    void addsProfileMetadataToPandocNormalizationSnapshot() {
        DefaultMarkdownNormalizationPort port = new DefaultMarkdownNormalizationPort(
                new MarkdownTextBlockParser(), new NormalizedMarkdownRenderer(), objectMapper);

        var result = port.normalize(new MarkdownNormalizationPort.NormalizationRequest(
                "mrev-2", "docx", "book.docx", "# 제목\n\n본문", List.of(), List.of(),
                NormalizedDocumentSnapshot.SOURCE_PANDOC,
                "PROFESSIONAL_BOOK", "PROFESSIONAL_BOOK", "v1"));

        var snapshot = NormalizedDocumentSnapshot.read(result.resources().get(0), objectMapper).orElseThrow();
        assertThat(snapshot.document().metadata())
                .containsEntry("resolvedDocumentProfile", "PROFESSIONAL_BOOK");
    }
}
