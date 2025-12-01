package studio.one.platform.text.extractor.impl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import studio.one.platform.text.extractor.FileParseException;

@Slf4j 
public class ImageFileParser extends AbstractFileParser {

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
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new FileParseException("Unsupported or corrupt image: " + safeFilename(filename));
            }
            return cleanText(tesseract.doOCR(image));
        } catch (TesseractException | IOException e) {
            throw new FileParseException("Failed to parse image: " + safeFilename(filename), e);
        }
    }
}
