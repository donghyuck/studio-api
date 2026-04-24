package studio.one.platform.textract.extractor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ParsedBlock;

class ImageFileParserTest {

    @Test
    void ocrLineBlocksSplitTextIntoLineLevelBlocks() {
        List<ParsedBlock> blocks = new ImageFileParser("/tmp", "kor+eng").ocrLineBlocks("""
                첫 줄
                둘째 줄

                셋째 줄
                """);

        assertEquals(3, blocks.size());
        assertTrue(blocks.stream().allMatch(block -> block.blockType() == BlockType.OCR_TEXT));
        assertEquals("image/ocr/line[0]", blocks.get(0).sourceRef());
        assertEquals("line", blocks.get(0).metadata().get(ExtractedImage.KEY_OCR_UNIT));
        assertEquals(false, blocks.get(0).metadata().get(ExtractedImage.KEY_CONFIDENCE_AVAILABLE));
    }
}
