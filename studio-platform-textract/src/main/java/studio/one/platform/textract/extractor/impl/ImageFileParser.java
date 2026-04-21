package studio.one.platform.textract.extractor.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
        this.tesseract = new  Tesseract();
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
            List<ParsedBlock> blocks = text == null || text.isBlank()
                    ? List.of()
                    : List.of(ParsedBlock.text(
                            "image/ocr",
                            BlockType.OCR_TEXT,
                            text,
                            null,
                            0,
                            Map.of()));
            ExtractedImage extractedImage = new ExtractedImage(
                    "image",
                    contentType,
                    filename,
                    image.getWidth(),
                    image.getHeight(),
                    Map.of("ocr", true));
            return new ParsedFile(
                    DocumentFormat.IMAGE,
                    text,
                    blocks,
                    metadata(contentType, filename),
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

    private Map<String, Object> metadata(String contentType, String filename) {
        if (filename == null || filename.isBlank()) {
            return contentType == null || contentType.isBlank()
                    ? Map.of()
                    : Map.of("contentType", contentType);
        }
        if (contentType == null || contentType.isBlank()) {
            return Map.of("filename", filename);
        }
        return Map.of("filename", filename, "contentType", contentType);
    }
}
