package studio.one.platform.textract.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.model.ExtractedTable;
import studio.one.platform.textract.domain.model.ExtractedTableCell;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;

class MarkdownDocumentBuilderTest {

    private final MarkdownDocumentBuilder builder = new MarkdownDocumentBuilder();

    @Test
    void rendersHeadingListTableAndLocatorsInDocumentOrder() {
        ParsedFile source = new ParsedFile(
                DocumentFormat.PPTX,
                "Title Body",
                List.of(
                        ParsedBlock.text("slide[1]/p", BlockType.PARAGRAPH, "Body", null, 2,
                                Map.of(ParsedBlock.KEY_SLIDE, 1)),
                        ParsedBlock.text("slide[1]/title", BlockType.HEADING, "Title", null, 0,
                                Map.of("headingLevel", 1, ParsedBlock.KEY_SLIDE, 1)),
                        ParsedBlock.text("slide[1]/list", BlockType.LIST_ITEM, "Item", null, 1,
                                Map.of(ParsedBlock.KEY_SLIDE, 1)),
                        ParsedBlock.text("slide[1]/table", BlockType.TABLE, "", null, 3,
                                Map.of(ParsedBlock.KEY_SLIDE, 1))),
                Map.of("filename", "sample.pptx"),
                List.of(),
                List.of(),
                List.of(new ExtractedTable(
                        "slide[1]/table",
                        "",
                        List.of(
                                new ExtractedTableCell(0, 0, 1, 1, "Name", Map.of()),
                                new ExtractedTableCell(1, 0, 1, 1, "Value", Map.of())),
                        Map.of())),
                List.of(),
                false);

        ParsedFile result = builder.normalize(source);

        assertThat(result.markdown()).isEqualTo("""
                # Title

                - Item

                Body

                | Name |
                | --- |
                | Value |""");
        assertThat(result.contentFormat()).isEqualTo("markdown");
        assertThat(result.locators()).extracting(locator -> locator.type())
                .containsExactly("slide", "section");
        assertThat(result.locators().get(0).startOffset()).isZero();
        assertThat(result.locators().get(0).endOffset()).isEqualTo(result.markdown().length());
    }

    @Test
    void sanitizesControlCharactersAndFallsBackToPlainText() {
        ParsedFile source = ParsedFile.textOnly(DocumentFormat.TEXT, "line\u0000 one\n\n\nline two", "sample.txt");

        ParsedFile result = builder.normalize(source);

        assertThat(result.markdown()).isEqualTo("line one\n\nline two");
        assertThat(result.plainText()).isEqualTo("line\u0000 one\n\n\nline two");
    }
}
