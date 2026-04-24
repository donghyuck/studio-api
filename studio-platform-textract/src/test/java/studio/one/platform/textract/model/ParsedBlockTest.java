package studio.one.platform.textract.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ParsedBlockTest {

    @Test
    void textFactoryAddsProvenanceMetadata() {
        ParsedBlock block = ParsedBlock.text("body/element[0]", BlockType.PARAGRAPH, "본문", null, 3, Map.of());

        assertEquals(BlockType.PARAGRAPH, block.blockType());
        assertEquals(3, block.order());
        assertEquals("body/element[0]", block.sourceRef());
        assertEquals("", block.parentBlockId());
        assertNull(block.confidence());
        assertNull(block.slide());
    }

    @Test
    void confidenceRemainsNullWhenMetadataDoesNotProvideIt() {
        ParsedBlock block = ParsedBlock.text("image/ocr", BlockType.OCR_TEXT, "텍스트", null, 0, Map.of());

        assertNull(block.confidence());
    }

    @Test
    void helperAccessorsReadMetadataBack() {
        ParsedBlock block = new ParsedBlock(
                "block-1",
                BlockType.IMAGE,
                "slides/0/image[0]",
                "diagram",
                2,
                java.util.List.of(),
                Map.of(
                        ParsedBlock.KEY_SOURCE_REF, "slides/0",
                        ParsedBlock.KEY_ORDER, 7,
                        ParsedBlock.KEY_PARENT_BLOCK_ID, "slide-0",
                        ParsedBlock.KEY_CONFIDENCE, 0.82d,
                        ParsedBlock.KEY_SLIDE, 1));

        assertEquals(BlockType.IMAGE, block.blockType());
        assertEquals(7, block.order());
        assertEquals("slides/0", block.sourceRef());
        assertEquals("slide-0", block.parentBlockId());
        assertEquals(0.82d, block.confidence());
        assertEquals(1, block.slide());
    }
}
