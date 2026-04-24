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
                        new ExtractedTableCell(0, 0, 1, 1, "A1", Map.of()),
                        new ExtractedTableCell(0, 1, 1, 1, "B1", Map.of()),
                        new ExtractedTableCell(1, 0, 1, 1, "A2", Map.of())),
                Map.of(
                        ExtractedTable.KEY_SOURCE_REF, "body/table[0]",
                        ExtractedTable.KEY_FORMAT, "docx"));

        assertEquals("body/table[0]", table.sourceRef());
        assertEquals("docx", table.format());
        assertEquals(2, table.rowCount());
        assertEquals(3, table.cellCount());
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
                        ExtractedImage.KEY_PACKAGE_ID, "image1"));

        assertEquals("image/png", image.mimeType());
        assertEquals("section[0]/pic[1]", image.sourceRef());
        assertEquals(List.of("section[0]/pic[1]"), image.sourceRefs());
        assertEquals("Contents/BinData/image1.png", image.binDataRef());
        assertEquals("image1", image.packageId());
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
}
