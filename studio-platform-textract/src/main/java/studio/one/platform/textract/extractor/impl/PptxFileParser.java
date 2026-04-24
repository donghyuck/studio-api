package studio.one.platform.textract.extractor.impl;

import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

public class PptxFileParser extends AbstractFileParser implements StructuredFileParser {

    @Override
    public boolean supports(String contentType, String filename) {
        if (isContentType(contentType,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
            return true;
        return hasExtension(filename, ".pptx");
    }

    @Override
    public ParsedFile parseStructured(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             XMLSlideShow ppt = new XMLSlideShow(in)) {

            StringBuilder sb = new StringBuilder();
            List<ParsedBlock> blocks = new ArrayList<>();
            List<ParsedBlock> pages = new ArrayList<>();
            Dimension pageSize = ppt.getPageSize();
            int order = 0;

            for (int slideIndex = 0; slideIndex < ppt.getSlides().size(); slideIndex++) {
                StringBuilder slideText = new StringBuilder();
                boolean titleSeen = false;
                XSLFSlide slide = ppt.getSlides().get(slideIndex);
                int shapeIndex = 0;
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = cleanText(textShape.getText());
                        if (text != null && !text.isBlank()) {
                            String path = "slide[" + (slideIndex + 1) + "]/shape[" + shapeIndex + "]";
                            BlockType blockType = resolveTextShapeType(textShape, pageSize, titleSeen);
                            if (blockType == BlockType.TITLE) {
                                titleSeen = true;
                            }
                            blocks.add(ParsedBlock.text(
                                    path,
                                    blockType,
                                    text,
                                    null,
                                    order,
                                    blockMetadata(path, slideIndex + 1, order)));
                            sb.append(text).append("\n");
                            slideText.append(text).append("\n");
                            order++;
                        }
                    }
                    shapeIndex++;
                }
                String cleanedSlideText = cleanText(slideText.toString());
                if (cleanedSlideText != null && !cleanedSlideText.isBlank()) {
                    String path = "slide[" + (slideIndex + 1) + "]";
                    pages.add(ParsedBlock.text(
                            path,
                            BlockType.PAGE,
                            cleanedSlideText,
                            null,
                            pages.size(),
                            blockMetadata(path, slideIndex + 1, pages.size())));
                }
            }

            return new ParsedFile(
                    DocumentFormat.PPTX,
                    cleanText(sb.toString()),
                    blocks,
                    fileMetadata(contentType, filename),
                    List.of(),
                    pages,
                    List.of(),
                    List.of(),
                    false);

        } catch (IOException e) {
            throw new FileParseException("Failed to parse PPTX file: " + safeFilename(filename), e);
        }
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return parseStructured(bytes, contentType, filename).plainText();
    }

    private BlockType resolveTextShapeType(XSLFTextShape textShape, Dimension pageSize, boolean titleSeen) {
        Rectangle2D anchor = textShape.getAnchor();
        if (anchor != null && pageSize != null && anchor.getY() > pageSize.getHeight() * 0.80d) {
            return BlockType.FOOTER;
        }
        return titleSeen ? BlockType.PARAGRAPH : BlockType.TITLE;
    }

    private Map<String, Object> blockMetadata(String path, int slide, int order) {
        Map<String, Object> metadata = new LinkedHashMap<>(blockMetadata(path, Integer.valueOf(order)));
        metadata.put(ParsedBlock.KEY_SLIDE, slide);
        return metadata;
    }
}
