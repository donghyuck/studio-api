package studio.one.platform.chunking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.chunking.core.NormalizedBlock;
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
                List.of(
                        ParsedBlock.text("body/h1", BlockType.HEADING, "Title", 1, 0,
                                Map.of(ParsedBlock.KEY_CONFIDENCE, 0.98d)),
                        ParsedBlock.text("body/table[0]", BlockType.TABLE, "| A |\n| --- |\n| 1 |", 1, 1,
                                Map.of(ParsedBlock.KEY_CONFIDENCE, 0.87d)),
                        ParsedBlock.text("body/p[1]", BlockType.PARAGRAPH, "After table", 1, 2, Map.of())),
                Map.of("filename", "sample.html"),
                List.of(),
                List.of(),
                List.of(new ExtractedTable(
                        "body/table[0]",
                        "| A |\n| --- |\n| 1 |",
                        List.of(new ExtractedTableCell(0, 0, 1, 1, "A",
                                Map.of(ExtractedTableCell.KEY_SOURCE_REF, "body/table[0]/cell[0,0]"))),
                        Map.of(ExtractedTable.KEY_VECTOR_TEXT, "A: 1", ExtractedTable.KEY_SOURCE_REF, "body/table[0]"))),
                List.of(new ExtractedImage(
                        "body/img[0]",
                        "image/png",
                        "image.png",
                        10,
                        20,
                        Map.of(ExtractedImage.KEY_ALT_TEXT, "architecture diagram",
                                ExtractedImage.KEY_SOURCE_REF, "body/img[0]",
                                ExtractedImage.KEY_SOURCE_REFS, List.of("body/img[0]", "body/img[0]/caption"),
                                ParsedBlock.KEY_ORDER, 3,
                                ParsedBlock.KEY_PARENT_BLOCK_ID, "body/p[1]",
                                ParsedBlock.KEY_CONFIDENCE, 0.76d,
                                NormalizedBlock.KEY_HEADING_PATH, "Title"))),
                false);

        NormalizedDocument document = new TextractNormalizedDocumentAdapter().adapt("doc", parsedFile);

        assertThat(document.sourceFormat()).isEqualTo("HTML");
        assertThat(document.filename()).isEqualTo("sample.html");
        assertThat(document.blocks()).extracting(block -> block.type())
                .containsExactly(
                        NormalizedBlockType.HEADING,
                        NormalizedBlockType.TABLE,
                        NormalizedBlockType.PARAGRAPH,
                        NormalizedBlockType.IMAGE_CAPTION);
        assertThat(document.blocks()).extracting(block -> block.text())
                .contains("Title", "A: 1", "After table", "architecture diagram")
                .doesNotContain("| A |\n| --- |\n| 1 |");
        assertThat(document.blocks().get(1).order()).isEqualTo(1);
        assertThat(document.blocks().get(0).confidence()).isEqualTo(0.98d);
        assertThat(document.blocks().get(2).headingPath()).isEqualTo("Title");
        assertThat(document.blocks().get(1).headingPath()).isEqualTo("Title");
        assertThat(document.blocks().get(1).blockIds()).containsExactly("body/table[0]/cell[0,0]");
        assertThat(document.blocks().get(1).confidence()).isEqualTo(0.87d);
        assertThat(document.blocks().get(3).order()).isEqualTo(3);
        assertThat(document.blocks().get(3).parentBlockId()).isEqualTo("body/p[1]");
        assertThat(document.blocks().get(3).headingPath()).isEqualTo("Title");
        assertThat(document.blocks().get(3).blockIds()).containsExactly("body/img[0]", "body/img[0]/caption");
        assertThat(document.blocks().get(3).confidence()).isEqualTo(0.76d);
    }

    @Test
    void keepsPlainTextFallbackWhenStructuredBlocksAreMissing() {
        ParsedFile parsedFile = new ParsedFile(
                DocumentFormat.TEXT,
                "plain fallback",
                List.of(),
                Map.of("filename", "fallback.txt"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false);

        NormalizedDocument document = new TextractNormalizedDocumentAdapter().adapt("doc", parsedFile);

        assertThat(document.blocks()).isEmpty();
        assertThat(document.chunkableText()).isEqualTo("plain fallback");
        assertThat(document.filename()).isEqualTo("fallback.txt");
    }
}
