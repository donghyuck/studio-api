package studio.one.platform.textract.extractor.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

@Slf4j
public class ImageFileParser extends AbstractFileParser implements StructuredFileParser {

    private final Tesseract tesseract; // Spring Bean 으로 주입받는 것을 권장

    public ImageFileParser(String tesseractDataPath, String language) {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(tesseractDataPath);
        this.tesseract.setLanguage(language);
    }

    @Override
    public boolean supports(String contentType, String filename) {
        String name = lower(filename);
        if (isContentType(contentType, "image/")) return true;
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp");
    }

    @Override
    public ParsedFile parseStructured(byte[] bytes, String contentType, String filename)
            throws FileParseException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new FileParseException("Unsupported or corrupt image: " + safeFilename(filename));
            }
            String text = cleanText(tesseract.doOCR(image));
            List<ParsedBlock> blocks = ocrLineBlocks(text);
            ExtractedImage extractedImage = new ExtractedImage(
                    "image",
                    contentType,
                    filename,
                    image.getWidth(),
                    image.getHeight(),
                    imageMetadata(blocks.size()));
            return new ParsedFile(
                    DocumentFormat.IMAGE,
                    text,
                    blocks,
                    fileMetadata(contentType, filename),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(extractedImage),
                    true);
        } catch (TesseractException | IOException e) {
            throw new FileParseException("Failed to parse image: " + safeFilename(filename), e);
        }
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return parseStructured(bytes, contentType, filename).plainText();
    }

    List<ParsedBlock> ocrLineBlocks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ParsedBlock> blocks = new ArrayList<>();
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String cleanedLine = cleanText(line);
            if (cleanedLine == null || cleanedLine.isBlank()) {
                continue;
            }
            int order = blocks.size();
            String path = "image/ocr/line[" + order + "]";
            blocks.add(ParsedBlock.text(path, BlockType.OCR_TEXT, cleanedLine, null, order, ocrBlockMetadata(path, order)));
        }
        return blocks;
    }

    private Map<String, Object> ocrBlockMetadata(String path, int order) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ParsedBlock.KEY_SOURCE_REF, path);
        metadata.put(ParsedBlock.KEY_ORDER, order);
        metadata.put(ExtractedImage.KEY_OCR_APPLIED, true);
        metadata.put(ExtractedImage.KEY_OCR_UNIT, "line");
        metadata.put(ExtractedImage.KEY_CONFIDENCE_AVAILABLE, false);
        return metadata;
    }

    private Map<String, Object> imageMetadata(int ocrLineCount) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ExtractedImage.KEY_SOURCE_REF, "image");
        metadata.put(ExtractedImage.KEY_OCR_APPLIED, true);
        metadata.put("ocrLineCount", ocrLineCount);
        metadata.put(ExtractedImage.KEY_CONFIDENCE_AVAILABLE, false);
        return metadata;
    }

}
