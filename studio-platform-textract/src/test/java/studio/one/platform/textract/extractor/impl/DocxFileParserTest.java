package studio.one.platform.textract.extractor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Base64;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

class DocxFileParserTest {

    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

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

    @Test
    void parseStructuredAssignsListItemBlockTypeFromNumbering() throws Exception {
        byte[] bytes = docxWithNumberedParagraph();

        ParsedFile result = new DocxFileParser()
                .parseStructured(bytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "list.docx");

        ParsedBlock listBlock = result.blocks().stream()
                .filter(block -> block.blockType() == BlockType.LIST_ITEM)
                .findFirst()
                .orElseThrow();
        assertEquals("목록 항목", listBlock.text());
        assertEquals(0, listBlock.order());
    }

    @Test
    void parseStructuredAssignsFootnoteBlockTypeAndProvenance() throws Exception {
        byte[] bytes = docxWithFootnote();

        ParsedFile result = new DocxFileParser()
                .parseStructured(bytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "footnote.docx");

        ParsedBlock footnoteBlock = result.blocks().stream()
                .filter(block -> block.blockType() == BlockType.FOOTNOTE)
                .findFirst()
                .orElseThrow();
        assertEquals("각주 본문", footnoteBlock.text());
        assertTrue(footnoteBlock.sourceRef().startsWith("footnote["));
        assertTrue(footnoteBlock.order() != null && footnoteBlock.order() > 0);
    }

    @Test
    void parseStructuredExtractsEmbeddedImageWithCaptionAndSourceRef() throws Exception {
        byte[] bytes = docxWithEmbeddedImage();

        ParsedFile result = new DocxFileParser()
                .parseStructured(bytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image.docx");

        assertEquals(1, result.images().size());
        ExtractedImage image = result.images().get(0);
        assertEquals("image/png", image.mimeType());
        assertEquals("image1.png", image.filename());
        assertEquals("body/element[0]/run[1]/picture[0]", image.sourceRef());
        assertEquals("그림 1 설명", image.caption());
        assertEquals("image1.png", image.binDataRef());
        assertEquals(1, image.width());
        assertEquals(1, image.height());
        assertTrue(result.plainText().contains("그림 1 설명"));
    }

    @Test
    void parseStructuredExtractsTableCellEmbeddedImageWithCellSourceRef() throws Exception {
        byte[] bytes = docxWithTableCellImage();

        ParsedFile result = new DocxFileParser()
                .parseStructured(bytes, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "cell-image.docx");

        assertEquals(1, result.images().size());
        ExtractedImage image = result.images().get(0);
        assertEquals("body/element[0]/row[0]/cell[0]/paragraph[0]/run[1]/picture[0]", image.sourceRef());
        assertEquals("셀 이미지 설명", image.caption());
        assertEquals("image1.png", image.binDataRef());
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

    private byte[] docxWithNumberedParagraph() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setNumID(BigInteger.ONE);
            paragraph.createRun().setText("목록 항목");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] docxWithFootnote() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("본문 문단");
            XWPFFootnote footnote = document.createFootnote();
            footnote.createParagraph().createRun().setText("각주 본문");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] docxWithEmbeddedImage() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("그림 1 설명");
            XWPFRun imageRun = paragraph.createRun();
            imageRun.addPicture(
                    new ByteArrayInputStream(PNG_BYTES),
                    Document.PICTURE_TYPE_PNG,
                    "inline.png",
                    Units.pixelToEMU(24),
                    Units.pixelToEMU(12));
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] docxWithTableCellImage() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFTable table = document.createTable(1, 1);
            XWPFParagraph paragraph = table.getRow(0).getCell(0).getParagraphs().get(0);
            paragraph.createRun().setText("셀 이미지 설명");
            XWPFRun imageRun = paragraph.createRun();
            imageRun.addPicture(
                    new ByteArrayInputStream(PNG_BYTES),
                    Document.PICTURE_TYPE_PNG,
                    "cell.png",
                    Units.pixelToEMU(10),
                    Units.pixelToEMU(10));
            document.write(out);
            return out.toByteArray();
        }
    }
}
