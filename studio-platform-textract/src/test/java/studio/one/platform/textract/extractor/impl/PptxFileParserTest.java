package studio.one.platform.textract.extractor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ParseWarningSeverity;
import studio.one.platform.textract.model.ParsedFile;

class PptxFileParserTest {

    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

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

    @Test
    void parseStructuredExtractsPictureShapeWithCaptionAndSourceRef() throws Exception {
        byte[] bytes = pptxWithPictureAndCaption();

        ParsedFile result = new PptxFileParser().parseStructured(
                bytes,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "image.pptx");

        assertEquals(1, result.images().size());
        ExtractedImage image = result.images().get(0);
        assertEquals("image/png", image.mimeType());
        assertEquals("image1.png", image.filename());
        assertEquals("slide[1]/shape[1]", image.sourceRef());
        assertEquals("그림 설명", image.caption());
        assertEquals(160, image.width());
        assertEquals(107, image.height());
        assertTrue(result.plainText().contains("그림 설명"));
    }

    @Test
    void parseStructuredUsesNearestLowerTextAsPictureCaption() throws Exception {
        byte[] bytes = pptxWithPictureAndCompetingText();

        ParsedFile result = new PptxFileParser().parseStructured(
                bytes,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "caption-boundary.pptx");

        assertEquals("가까운 아래 설명", result.images().get(0).caption());
    }

    @Test
    void parseStructuredWarnsForLinkedPicture() throws Exception {
        byte[] bytes = linkedPicturePptx();

        ParsedFile result = new PptxFileParser().parseStructured(
                bytes,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "linked.pptx");

        assertEquals(1, result.images().size());
        assertEquals(1, result.warnings().size());
        assertEquals("PPTX_LINKED_IMAGE_PARTIAL", result.warnings().get(0).canonicalCode());
        assertEquals(ParseWarningSeverity.WARNING, result.warnings().get(0).severity());
        assertEquals(result.images().get(0).sourceRef(), result.warnings().get(0).sourceRef());
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

    private byte[] pptxWithPictureAndCaption() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(new java.awt.Dimension(640, 480));
            XSLFSlide slide = ppt.createSlide();
            addTextBox(slide, "슬라이드 제목", new Rectangle(20, 20, 500, 50));
            XSLFPictureData pictureData = ppt.addPicture(PNG_BYTES, PictureData.PictureType.PNG);
            XSLFPictureShape picture = slide.createPicture(pictureData);
            picture.setAnchor(new Rectangle(20, 100, 120, 80));
            addTextBox(slide, "그림 설명", new Rectangle(20, 190, 500, 40));
            ppt.write(out);
            return out.toByteArray();
        }
    }

    private byte[] pptxWithPictureAndCompetingText() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(new java.awt.Dimension(640, 480));
            XSLFSlide slide = ppt.createSlide();
            addTextBox(slide, "위쪽 텍스트", new Rectangle(20, 20, 500, 40));
            addTextBox(slide, "겹치는 텍스트", new Rectangle(20, 150, 500, 40));
            XSLFPictureData pictureData = ppt.addPicture(PNG_BYTES, PictureData.PictureType.PNG);
            XSLFPictureShape picture = slide.createPicture(pictureData);
            picture.setAnchor(new Rectangle(20, 140, 120, 80));
            addTextBox(slide, "먼 아래 설명", new Rectangle(20, 320, 500, 40));
            addTextBox(slide, "가까운 아래 설명", new Rectangle(20, 230, 500, 40));
            ppt.write(out);
            return out.toByteArray();
        }
    }

    private byte[] linkedPicturePptx() throws Exception {
        return rewritePictureAsExternalLink(pptxWithPictureAndCaption());
    }

    private void addTextBox(XSLFSlide slide, String text, Rectangle anchor) {
        XSLFTextBox textBox = slide.createTextBox();
        textBox.setText(text);
        textBox.setAnchor(anchor);
    }

    private byte[] rewritePictureAsExternalLink(byte[] pptxBytes) throws Exception {
        Map<String, byte[]> entries = unzip(pptxBytes);
        String slideXml = new String(entries.get("ppt/slides/slide1.xml"), StandardCharsets.UTF_8)
                .replaceFirst("r:embed=\"rId[0-9]+\"", "r:link=\"rIdLinkedImage\"");
        String relsPath = "ppt/slides/_rels/slide1.xml.rels";
        String relsXml = new String(entries.get(relsPath), StandardCharsets.UTF_8)
                .replace(
                        "</Relationships>",
                        "<Relationship Id=\"rIdLinkedImage\" "
                                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" "
                                + "Target=\"https://example.com/linked.png\" TargetMode=\"External\"/>"
                                + "</Relationships>");
        entries.put("ppt/slides/slide1.xml", slideXml.getBytes(StandardCharsets.UTF_8));
        entries.put(relsPath, relsXml.getBytes(StandardCharsets.UTF_8));
        return zip(entries);
    }

    private Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
