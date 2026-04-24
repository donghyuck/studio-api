package studio.one.platform.textract.extractor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ParsedFile;

class PptxFileParserTest {

    @Test
    void parseStructuredClassifiesTitleBodyAndFooterBlocks() throws Exception {
        byte[] bytes = pptxWithTitleBodyAndFooter();

        ParsedFile result = new PptxFileParser().parseStructured(
                bytes,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "sample.pptx");

        assertEquals(DocumentFormat.PPTX, result.format());
        assertTrue(result.plainText().contains("슬라이드 제목"));
        assertEquals(1, result.pages().size());
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.TITLE));
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.PARAGRAPH));
        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.FOOTER));
        assertEquals(1, result.blocks().get(0).slide());
        assertEquals(0, result.blocks().get(0).order());
    }

    private byte[] pptxWithTitleBodyAndFooter() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(new java.awt.Dimension(640, 480));
            XSLFSlide slide = ppt.createSlide();
            addTextBox(slide, "슬라이드 제목", new Rectangle(20, 20, 500, 50));
            addTextBox(slide, "본문 내용", new Rectangle(20, 120, 500, 80));
            addTextBox(slide, "푸터", new Rectangle(20, 420, 500, 30));
            ppt.write(out);
            return out.toByteArray();
        }
    }

    private void addTextBox(XSLFSlide slide, String text, Rectangle anchor) {
        XSLFTextBox textBox = slide.createTextBox();
        textBox.setText(text);
        textBox.setAnchor(anchor);
    }
}
