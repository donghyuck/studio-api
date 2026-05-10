package studio.one.platform.textract.infrastructure.extractor.pdf.pymupdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;
import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.ExtractedImage;
import studio.one.platform.textract.domain.model.ParsedFile;

class PyMuPdf4LlmResultMapperTest {

    @Test
    void mapsMarkdownBlocksTablesImagesAndWarningsToParsedFile() {
        PyMuPdf4LlmResponse response = new PyMuPdf4LlmResponse(
                "sample.pdf",
                "application/pdf",
                "# Title\n\nBody",
                List.of(new PyMuPdf4LlmResponse.Page(1, "Title\nBody", List.of(), Map.of())),
                List.of(new PyMuPdf4LlmResponse.Block(
                        "heading",
                        "Title",
                        1,
                        0,
                        1,
                        "page[1]/heading[0]",
                        List.of(1.0, 2.0, 3.0, 4.0),
                        Map.of())),
                List.of(new PyMuPdf4LlmResponse.Table(
                        1,
                        "Scores",
                        List.of("Name", "Score"),
                        List.of(List.of("Alice", "90")),
                        "",
                        "page[1]/table[0]",
                        List.of(),
                        Map.of())),
                List.of(new PyMuPdf4LlmResponse.Image(
                        1,
                        "image-1.png",
                        "image/png",
                        100,
                        80,
                        "page[1]/image[0]",
                        "",
                        "diagram",
                        "",
                        false,
                        List.of(),
                        Map.of())),
                Map.of("source", "worker"),
                List.of(new PyMuPdf4LlmResponse.Warning("layout.partial", "partial layout", "page[1]", Map.of())),
                123L,
                false);

        ParsedFile result = new PyMuPdf4LlmResultMapper().map(response, new PdfExtractionRequest(
                new byte[] {1},
                "application/pdf",
                "sample.pdf",
                PdfExtractionOptions.defaults()));

        assertThat(result.plainText()).isEqualTo("# Title\n\nBody");
        assertThat(result.metadata())
                .containsEntry(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm")
                .containsEntry("markdownAvailable", true)
                .containsEntry("elapsedMs", 123L);
        assertThat(result.pages()).hasSize(1);
        assertThat(result.blocks()).extracting(block -> block.blockType())
                .contains(BlockType.HEADING, BlockType.TABLE);
        assertThat(result.tables()).hasSize(1);
        assertThat(result.tables().get(0).vectorText()).contains("Name: Alice", "Score: 90");
        ExtractedImage image = result.images().get(0);
        assertThat(image.altText()).isEqualTo("diagram");
        assertThat(image.page()).isEqualTo(1);
        assertThat(result.warnings()).extracting(warning -> warning.canonicalCode())
                .contains("LAYOUT_PARTIAL");
    }

    @Test
    void handlesWorkerMetadataWithNullValues() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("author", null);
        PyMuPdf4LlmResponse response = new PyMuPdf4LlmResponse(
                "sample.pdf",
                "application/pdf",
                "Body",
                List.of(new PyMuPdf4LlmResponse.Page(1, "Body", List.of(), metadata)),
                List.of(),
                List.of(),
                List.of(),
                metadata,
                List.of(),
                1L,
                false);

        ParsedFile result = new PyMuPdf4LlmResultMapper().map(response, new PdfExtractionRequest(
                new byte[] {1},
                "application/pdf",
                "sample.pdf",
                PdfExtractionOptions.defaults()));

        assertThat(result.metadata()).doesNotContainKey("author");
        assertThat(result.pages().get(0).metadata()).doesNotContainKey("author");
    }
}
