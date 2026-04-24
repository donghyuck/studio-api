package studio.one.platform.textract.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExtractedStructureTest {

    @Test
    void extractedTableExposesStructureHelpers() {
        ExtractedTable table = new ExtractedTable(
                "body/table[0]",
                "| A1 | B1 |",
                List.of(
                        new ExtractedTableCell(0, 0, 1, 1, "A1", Map.of(
                                ExtractedTableCell.KEY_SOURCE_REF, "body/table[0]/row[0]/cell[0]",
                                ExtractedTableCell.KEY_HEADER, true)),
                        new ExtractedTableCell(0, 1, 1, 1, "B1", Map.of()),
                        new ExtractedTableCell(1, 0, 1, 1, "A2", Map.of())),
                Map.of(
                        ExtractedTable.KEY_SOURCE_REF, "body/table[0]",
                        ExtractedTable.KEY_FORMAT, "docx",
                        ExtractedTable.KEY_VECTOR_TEXT, "A1 B1\nA2",
                        ExtractedTable.KEY_HEADER_ROW_COUNT, 1));

        assertEquals("body/table[0]", table.sourceRef());
        assertEquals("docx", table.format());
        assertEquals("A1 B1\nA2", table.vectorText());
        assertEquals(1, table.headerRowCount());
        assertEquals(2, table.rowCount());
        assertEquals(3, table.cellCount());
        assertEquals("body/table[0]/row[0]/cell[0]", table.cells().get(0).sourceRef());
        assertEquals(true, table.cells().get(0).header());
    }

    @Test
    void extractedTableFallsBackToMarkdownVectorTextAndZeroHeaderRows() {
        ExtractedTable table = new ExtractedTable(
                "body/table[0]",
                "| A1 | B1 |",
                List.of(),
                Map.of());

        assertEquals("| A1 | B1 |", table.vectorText());
        assertEquals(0, table.headerRowCount());
    }

    @Test
    void extractedImageExposesSourceAndBinaryReferences() {
        ExtractedImage image = new ExtractedImage(
                "bindata/image1",
                "image/png",
                "image1.png",
                null,
                null,
                Map.of(
                        ExtractedImage.KEY_SOURCE_REF, "section[0]/pic[1]",
                        ExtractedImage.KEY_BIN_DATA_REF, "Contents/BinData/image1.png",
                        ExtractedImage.KEY_PACKAGE_ID, "image1",
                        ExtractedImage.KEY_CAPTION, "그림 설명",
                        ExtractedImage.KEY_SRC, "media/image1.png",
                        ExtractedImage.KEY_ALT_TEXT, "대체 텍스트",
                        ExtractedImage.KEY_OCR_TEXT, "OCR 텍스트",
                        ExtractedImage.KEY_OCR_APPLIED, true,
                        ExtractedImage.KEY_OCR_UNIT, "line",
                        ExtractedImage.KEY_CONFIDENCE_AVAILABLE, false));

        assertEquals("image/png", image.mimeType());
        assertEquals("section[0]/pic[1]", image.sourceRef());
        assertEquals(List.of("section[0]/pic[1]"), image.sourceRefs());
        assertEquals("Contents/BinData/image1.png", image.binDataRef());
        assertEquals("image1", image.packageId());
        assertEquals("그림 설명", image.caption());
        assertEquals("media/image1.png", image.src());
        assertEquals("대체 텍스트", image.altText());
        assertEquals("OCR 텍스트", image.ocrText());
        assertEquals(true, image.ocrApplied());
        assertEquals("line", image.ocrUnit());
        assertEquals(false, image.confidenceAvailable());
    }

    @Test
    void extractedImageReturnsAllSourceRefsWhenMultipleMappingsExist() {
        ExtractedImage image = new ExtractedImage(
                "bindata/image1",
                "image/png",
                "image1.png",
                null,
                null,
                Map.of(
                        ExtractedImage.KEY_SOURCE_REFS, List.of("section[0]/pic[0]", "section[2]/pic[1]"),
                        ExtractedImage.KEY_BIN_DATA_REF, "Contents/BinData/image1.png"));

        assertEquals("bindata/image1", image.sourceRef());
        assertEquals(List.of("section[0]/pic[0]", "section[2]/pic[1]"), image.sourceRefs());
    }

    @Test
    void extractedImageIgnoresNonStringSourceRefsWithoutUncheckedCast() {
        ExtractedImage image = new ExtractedImage(
                "bindata/image1",
                "image/png",
                "image1.png",
                null,
                null,
                Map.of(ExtractedImage.KEY_SOURCE_REFS, List.of("section[0]/pic[0]", 10, true)));

        assertEquals(List.of("section[0]/pic[0]"), image.sourceRefs());
    }
}
