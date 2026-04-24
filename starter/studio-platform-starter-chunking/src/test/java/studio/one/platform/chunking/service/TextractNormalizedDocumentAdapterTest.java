package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;
import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ExtractedTableCell;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

class TextractNormalizedDocumentAdapterTest {

    @Test
    void adaptsParsedBlocksTablesAndImagesWithoutParsingAgain() {
        ParsedFile parsedFile = new ParsedFile(
                DocumentFormat.HTML,
                "plain",
                List.of(ParsedBlock.text("body/h1", BlockType.HEADING, "Title", 1, 0, Map.of())),
                Map.of("filename", "sample.html"),
                List.of(),
                List.of(),
                List.of(new ExtractedTable(
                        "body/table[0]",
                        "| A |\n| --- |\n| 1 |",
                        List.of(new ExtractedTableCell(0, 0, 1, 1, "A", Map.of())),
                        Map.of(ExtractedTable.KEY_VECTOR_TEXT, "A: 1", ExtractedTable.KEY_SOURCE_REF, "body/table[0]"))),
                List.of(new ExtractedImage(
                        "body/img[0]",
                        "image/png",
                        "image.png",
                        10,
                        20,
                        Map.of(ExtractedImage.KEY_ALT_TEXT, "architecture diagram",
                                ExtractedImage.KEY_SOURCE_REF, "body/img[0]"))),
                false);

        NormalizedDocument document = new TextractNormalizedDocumentAdapter().adapt("doc", parsedFile);

        assertThat(document.sourceFormat()).isEqualTo("HTML");
        assertThat(document.filename()).isEqualTo("sample.html");
        assertThat(document.blocks()).extracting(block -> block.type())
                .containsExactly(NormalizedBlockType.HEADING, NormalizedBlockType.TABLE, NormalizedBlockType.IMAGE_CAPTION);
        assertThat(document.blocks()).extracting(block -> block.text())
                .contains("Title", "A: 1", "architecture diagram");
    }
}
