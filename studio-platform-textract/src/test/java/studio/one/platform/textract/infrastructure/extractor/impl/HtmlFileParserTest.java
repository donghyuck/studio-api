package studio.one.platform.textract.infrastructure.extractor.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.ExtractedTable;
import studio.one.platform.textract.domain.model.ExtractedTableCell;
import studio.one.platform.textract.domain.model.ParsedFile;

class HtmlFileParserTest {

    @Test
    void parseStructuredExtractsSemanticBlocksAndSkipsBoilerplate() {
        String html = "<html>\n" +
                "  <body>\n" +
                "    <nav>메뉴 링크</nav>\n" +
                "    <main>\n" +
                "      <h1>문서 제목</h1>\n" +
                "      <p>본문 문단</p>\n" +
                "      <ul><li>목록 항목</li></ul>\n" +
                "      <table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>\n" +
                "      <img src=\"hero.png\" alt=\"대표 이미지\">\n" +
                "    </main>\n" +
                "    <footer>푸터</footer>\n" +
                "  </body>\n" +
                "</html>\n" +
                "";

        ParsedFile result = new HtmlFileParser().parseStructured(html.getBytes(UTF_8), "text/html", "sample.html");

        assertEquals(DocumentFormat.HTML, result.format());
        assertTrue(result.plainText().contains("문서 제목"));
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.TITLE));
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.LIST_ITEM));
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.TABLE));
        assertEquals(1, result.tables().size());
        ExtractedTable table = result.tables().get(0);
        assertEquals("html", table.format());
        assertEquals("A | B\nA: 1 | B: 2", table.vectorText());
        assertEquals(1, table.headerRowCount());
        assertEquals("table[3]/row[0]/cell[0]", table.cells().get(0).sourceRef());
        assertEquals(0, table.cells().get(0).order());
        assertTrue(table.cells().get(0).header());
        assertEquals(1, result.images().size());
        assertEquals("hero.png", result.images().get(0).src());
        assertEquals("대표 이미지", result.images().get(0).altText());
        assertFalse(result.plainText().contains("메뉴 링크"));
        assertFalse(result.plainText().contains("푸터"));
    }

    @Test
    void parseStructuredKeepsHtmlTableSpanMetadata() {
        String html = "<main>\n" +
                "  <table>\n" +
                "    <tr><th>A</th><th colspan=\"2\">B</th></tr>\n" +
                "    <tr><td>1</td><td>2</td><td>3</td></tr>\n" +
                "  </table>\n" +
                "</main>\n" +
                "";

        ParsedFile result = new HtmlFileParser().parseStructured(html.getBytes(UTF_8), "text/html", "span.html");

        ExtractedTableCell first = result.tables().get(0).cells().get(0);
        ExtractedTableCell second = result.tables().get(0).cells().get(1);
        ExtractedTableCell thirdDataCell = result.tables().get(0).cells().get(4);
        assertEquals(1, first.rowSpan());
        assertEquals(2, second.colSpan());
        assertEquals("table[0]/row[0]/cell[0]", first.sourceRef());
        assertEquals(2, thirdDataCell.col());
        assertEquals("A | B\nA: 1 | B: 2 | B: 3", result.tables().get(0).vectorText());
    }

    @Test
    void parseStructuredKeepsHtmlRowspanLogicalColumns() {
        String html = "<main>\n" +
                "  <table>\n" +
                "    <tr><th rowspan=\"2\">구분</th><th>값</th></tr>\n" +
                "    <tr><td>A</td></tr>\n" +
                "  </table>\n" +
                "</main>\n" +
                "";

        ParsedFile result = new HtmlFileParser().parseStructured(html.getBytes(UTF_8), "text/html", "rowspan.html");

        ExtractedTableCell rowDataCell = result.tables().get(0).cells().get(2);
        assertEquals(1, rowDataCell.col());
        assertEquals("구분 | 값\n값: A", result.tables().get(0).vectorText());
    }

    @Test
    void parseStructuredUsesAllSpannedHeadersForHtmlDataColspan() {
        String html = "<main>\n" +
                "  <table>\n" +
                "    <tr><th>Q1</th><th>Q2</th></tr>\n" +
                "    <tr><td colspan=\"2\">100</td></tr>\n" +
                "  </table>\n" +
                "</main>\n" +
                "";

        ParsedFile result = new HtmlFileParser().parseStructured(html.getBytes(UTF_8), "text/html", "data-colspan.html");

        ExtractedTableCell dataCell = result.tables().get(0).cells().get(2);
        assertEquals(2, dataCell.colSpan());
        assertEquals("Q1 | Q2\nQ1 Q2: 100", result.tables().get(0).vectorText());
    }

    @Test
    void parseStructuredKeepsPlainVectorTextWhenHtmlTableHasNoHeader() {
        String html = "<main><table><tr><td>A</td><td>B</td></tr></table></main>";

        ParsedFile result = new HtmlFileParser().parseStructured(html.getBytes(UTF_8), "text/html", "plain.html");

        assertEquals(0, result.tables().get(0).headerRowCount());
        assertEquals("A | B", result.tables().get(0).vectorText());
    }
}
