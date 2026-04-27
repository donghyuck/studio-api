package studio.one.platform.textract.extractor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ParseWarning;
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

    @Test
    void ocrLineBlocksIncludeConfidenceBboxAndWordMetadataWhenTokensAreAvailable() {
        List<ParsedBlock> blocks = new ImageFileParser("/tmp", "kor+eng").ocrLineBlocks(List.of(
                new ImageFileParser.OcrToken("첫", 0.91d, new Rectangle(10, 10, 8, 10)),
                new ImageFileParser.OcrToken("줄", 0.81d, new Rectangle(24, 10, 8, 10)),
                new ImageFileParser.OcrToken("둘째", 0.72d, new Rectangle(10, 40, 18, 10))));

        assertEquals(2, blocks.size());
        assertEquals("첫 줄", blocks.get(0).text());
        assertEquals(0.86d, blocks.get(0).confidence(), 0.0001d);
        assertEquals(true, blocks.get(0).metadata().get(ExtractedImage.KEY_CONFIDENCE_AVAILABLE));
        assertEquals(Map.of("x", 10, "y", 10, "width", 22, "height", 10), blocks.get(0).metadata().get(ImageFileParser.KEY_BBOX));
        assertEquals(2, blocks.get(0).metadata().get(ImageFileParser.KEY_WORD_COUNT));
        List<?> words = (List<?>) blocks.get(0).metadata().get(ImageFileParser.KEY_WORDS);
        assertEquals(2, words.size());
    }

    @Test
    void ocrLineBlocksKeepBboxAndWordsWhenTokenConfidenceIsUnavailable() {
        List<ParsedBlock> blocks = new ImageFileParser("/tmp", "kor+eng").ocrLineBlocks(List.of(
                new ImageFileParser.OcrToken("첫", null, new Rectangle(10, 10, 8, 10)),
                new ImageFileParser.OcrToken("줄", null, new Rectangle(24, 10, 8, 10))));

        assertEquals(1, blocks.size());
        assertEquals(false, blocks.get(0).metadata().get(ExtractedImage.KEY_CONFIDENCE_AVAILABLE));
        assertEquals(Map.of("x", 10, "y", 10, "width", 22, "height", 10), blocks.get(0).metadata().get(ImageFileParser.KEY_BBOX));
        assertEquals(2, blocks.get(0).metadata().get(ImageFileParser.KEY_WORD_COUNT));
        List<?> words = (List<?>) blocks.get(0).metadata().get(ImageFileParser.KEY_WORDS);
        assertEquals(2, words.size());
    }

    @Test
    void ocrWarningsReportLowConfidenceWithoutTesseractDependency() {
        ImageFileParser parser = new ImageFileParser("/tmp", "kor+eng");
        List<ParsedBlock> blocks = parser.ocrLineBlocks(List.of(
                new ImageFileParser.OcrToken("흐림", 0.42d, new Rectangle(10, 10, 20, 10))));

        List<ParseWarning> warnings = parser.ocrWarnings(blocks);

        assertEquals(1, warnings.size());
        assertEquals("OCR_LOW_CONFIDENCE", warnings.get(0).canonicalCode());
        assertTrue(warnings.get(0).metadata().containsKey(ImageFileParser.KEY_MIN_CONFIDENCE));
    }

    @Test
    void ocrWarningsUseWordMinimumConfidenceInsteadOfLineAverage() {
        ImageFileParser parser = new ImageFileParser("/tmp", "kor+eng");
        List<ParsedBlock> blocks = parser.ocrLineBlocks(List.of(
                new ImageFileParser.OcrToken("흐림", 0.42d, new Rectangle(10, 10, 20, 10)),
                new ImageFileParser.OcrToken("선명", 0.92d, new Rectangle(40, 10, 20, 10))));

        List<ParseWarning> warnings = parser.ocrWarnings(blocks);

        assertEquals(0.67d, blocks.get(0).confidence(), 0.0001d);
        assertEquals(1, warnings.size());
        assertEquals(0.42d, warnings.get(0).metadata().get(ImageFileParser.KEY_MIN_CONFIDENCE));
    }
}
