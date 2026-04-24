package studio.one.platform.textract.extractor.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ParsedFile;

class HtmlFileParserTest {

    @Test
    void parseStructuredExtractsSemanticBlocksAndSkipsBoilerplate() {
        String html = """
                <html>
                  <body>
                    <nav>메뉴 링크</nav>
                    <main>
                      <h1>문서 제목</h1>
                      <p>본문 문단</p>
                      <ul><li>목록 항목</li></ul>
                      <table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>
                      <img src="hero.png" alt="대표 이미지">
                    </main>
                    <footer>푸터</footer>
                  </body>
                </html>
                """;

        ParsedFile result = new HtmlFileParser().parseStructured(html.getBytes(UTF_8), "text/html", "sample.html");

        assertEquals(DocumentFormat.HTML, result.format());
        assertTrue(result.plainText().contains("문서 제목"));
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.TITLE));
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.LIST_ITEM));
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.TABLE));
        assertEquals(1, result.tables().size());
        assertEquals("html", result.tables().get(0).format());
        assertEquals(1, result.images().size());
        assertEquals("hero.png", result.images().get(0).src());
        assertEquals("대표 이미지", result.images().get(0).altText());
        assertFalse(result.plainText().contains("메뉴 링크"));
        assertFalse(result.plainText().contains("푸터"));
    }
}
