package studio.one.platform.textract.extractor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

class DocxFileParserTest {

    @Test
    void parseStructuredReturnsParagraphTextAndTableCells() throws Exception {
        byte[] bytes = docxWithParagraphAndTable();

        ParsedFile result = new DocxFileParser()
                .parseStructured(bytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "sample.docx");

        assertEquals(DocumentFormat.DOCX, result.format());
        assertTrue(result.plainText().contains("본문 문단"));
        assertTrue(result.plainText().contains("A1 | B1"));
        assertTrue(result.blocks().stream().anyMatch(block -> block.type() == BlockType.TABLE));
        assertEquals(1, result.tables().size());
        assertEquals(4, result.tables().get(0).cells().size());
        assertEquals("A1", result.tables().get(0).cells().get(0).text());
        assertEquals("| A1 | B1 |", result.tables().get(0).markdown().split("\\n")[0]);
        assertEquals("body/element[1]", result.tables().get(0).sourceRef());
        assertEquals("docx", result.tables().get(0).format());
        assertEquals("body/element[0]", result.blocks().get(0).sourceRef());
        assertEquals(0, result.blocks().get(0).order());
        assertEquals(1, result.blocks().get(1).order());
    }

    @Test
    void parseStructuredAssignsHeaderBlockTypeAndProvenance() throws Exception {
        byte[] bytes = docxWithHeader();

        ParsedFile result = new DocxFileParser()
                .parseStructured(bytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "header.docx");

        assertTrue(result.blocks().stream().anyMatch(block -> block.blockType() == BlockType.HEADER));
        ParsedBlock headerBlock = result.blocks().stream()
                .filter(block -> block.blockType() == BlockType.HEADER)
                .findFirst()
                .orElseThrow();
        assertTrue(headerBlock.sourceRef().startsWith("header[0]/element[0]"));
        assertTrue(headerBlock.order() != null && headerBlock.order() >= 0);
    }

    @Test
    void parseStructuredSkipsBlankParagraphsWithoutLeavingOrderGaps() throws Exception {
        byte[] bytes = docxWithBlankParagraph();

        ParsedFile result = new DocxFileParser()
                .parseStructured(bytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "blank.docx");

        assertEquals(2, result.blocks().size());
        assertEquals(0, result.blocks().get(0).order());
        assertEquals(1, result.blocks().get(1).order());
    }

    private byte[] docxWithParagraphAndTable() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("본문 문단");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("A1");
            table.getRow(0).getCell(1).setText("B1");
            table.getRow(1).getCell(0).setText("A2");
            table.getRow(1).getCell(1).setText("B2");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] docxWithHeader() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFHeader header = document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            header.createParagraph().createRun().setText("문서 헤더");
            document.createParagraph().createRun().setText("본문 문단");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] docxWithBlankParagraph() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("첫 문단");
            document.createParagraph();
            document.createParagraph().createRun().setText("둘째 문단");
            document.write(out);
            return out.toByteArray();
        }
    }
}
