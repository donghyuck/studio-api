package studio.one.platform.text.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import studio.one.platform.text.extractor.FileParseException;

public class PptxFileParser extends AbstractFileParser {

    @Override
    public boolean supports(String contentType, String filename) {
        if (isContentType(contentType,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
            return true;
        return hasExtension(filename, ".pptx");
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             XMLSlideShow ppt = new XMLSlideShow(in)) {

            StringBuilder sb = new StringBuilder();

            ppt.getSlides().forEach(slide -> {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text).append("\n");
                        }
                    }
                }
            });

            return sb.toString();

        } catch (IOException e) {
            throw new FileParseException("Failed to parse PPTX file: " + safeFilename(filename), e);
        }
    }
}