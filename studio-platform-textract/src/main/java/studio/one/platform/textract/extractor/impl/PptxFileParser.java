package studio.one.platform.textract.extractor.impl;

import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.util.Units;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ParseWarning;
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
            List<ExtractedImage> images = new ArrayList<>();
            List<ParseWarning> warnings = new ArrayList<>();
            Dimension pageSize = ppt.getPageSize();
            int order = 0;

            for (int slideIndex = 0; slideIndex < ppt.getSlides().size(); slideIndex++) {
                StringBuilder slideText = new StringBuilder();
                boolean titleSeen = false;
                XSLFSlide slide = ppt.getSlides().get(slideIndex);
                List<TextCandidate> textCandidates = new ArrayList<>();
                List<PictureCandidate> pictureCandidates = new ArrayList<>();
                int shapeIndex = 0;
                for (XSLFShape shape : slide.getShapes()) {
                    String path = "slide[" + (slideIndex + 1) + "]/shape[" + shapeIndex + "]";
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = cleanText(textShape.getText());
                        if (text != null && !text.isBlank()) {
                            BlockType blockType = resolveTextShapeType(textShape, pageSize, titleSeen);
                            if (blockType == BlockType.TITLE) {
                                titleSeen = true;
                            }
                            textCandidates.add(new TextCandidate(text, textShape.getAnchor()));
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
                    } else if (shape instanceof XSLFPictureShape pictureShape) {
                        pictureCandidates.add(new PictureCandidate(pictureShape, path));
                    }
                    shapeIndex++;
                }
                appendSlideImages(pictureCandidates, images, warnings, textCandidates);
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
                    warnings,
                    pages,
                    List.of(),
                    images,
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

    private void appendSlideImages(
            List<PictureCandidate> pictureCandidates,
            List<ExtractedImage> images,
            List<ParseWarning> warnings,
            List<TextCandidate> textCandidates) {
        for (PictureCandidate candidate : pictureCandidates) {
            if (candidate.pictureShape().isExternalLinkedPicture()) {
                warnings.add(ParseWarning.partial(
                        "PPTX_LINKED_IMAGE_PARTIAL",
                        "Linked PPTX image metadata is partially supported.",
                        candidate.sourceRef(),
                        Map.of()));
            }
            images.add(toExtractedImage(candidate.pictureShape(), candidate.sourceRef(), textCandidates));
        }
    }

    private ExtractedImage toExtractedImage(
            XSLFPictureShape pictureShape,
            String sourceRef,
            List<TextCandidate> textCandidates) {
        XSLFPictureData data = pictureShape.getPictureData();
        Rectangle2D anchor = pictureShape.getAnchor();
        Dimension dimension = data == null ? null : data.getImageDimensionInPixels();
        String filename = data == null ? "" : data.getFileName();
        String contentType = data == null ? null : data.getContentType();
        Map<String, Object> metadata = new LinkedHashMap<>(imageMetadata(sourceRef));
        if (data != null) {
            metadata.put(ExtractedImage.KEY_BIN_DATA_REF, filename);
        }
        String shapeName = cleanText(pictureShape.getShapeName());
        if (shapeName != null && !shapeName.isBlank()) {
            metadata.put(ExtractedImage.KEY_ALT_TEXT, shapeName);
        }
        String caption = nearestLowerText(anchor, textCandidates);
        if (caption != null && !caption.isBlank()) {
            metadata.put(ExtractedImage.KEY_CAPTION, caption);
        }
        return new ExtractedImage(
                sourceRef,
                contentType,
                filename,
                imageWidth(dimension, anchor),
                imageHeight(dimension, anchor),
                metadata);
    }

    private String nearestLowerText(Rectangle2D imageAnchor, List<TextCandidate> textCandidates) {
        if (imageAnchor == null) {
            return "";
        }
        TextCandidate nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        double imageBottom = imageAnchor.getMaxY();
        for (TextCandidate candidate : textCandidates) {
            Rectangle2D textAnchor = candidate.anchor();
            if (textAnchor == null || textAnchor.getY() < imageBottom) {
                continue;
            }
            double distance = textAnchor.getY() - imageBottom;
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest == null ? "" : nearest.text();
    }

    private Integer imageWidth(Dimension dimension, Rectangle2D anchor) {
        if (dimension != null && dimension.width > 0) {
            return dimension.width;
        }
        return anchor == null || anchor.getWidth() <= 0 ? null : Units.pointsToPixel(anchor.getWidth());
    }

    private Integer imageHeight(Dimension dimension, Rectangle2D anchor) {
        if (dimension != null && dimension.height > 0) {
            return dimension.height;
        }
        return anchor == null || anchor.getHeight() <= 0 ? null : Units.pointsToPixel(anchor.getHeight());
    }

    private Map<String, Object> blockMetadata(String path, int slide, int order) {
        Map<String, Object> metadata = new LinkedHashMap<>(blockMetadata(path, Integer.valueOf(order)));
        metadata.put(ParsedBlock.KEY_SLIDE, slide);
        return metadata;
    }

    private record TextCandidate(String text, Rectangle2D anchor) {
    }

    private record PictureCandidate(XSLFPictureShape pictureShape, String sourceRef) {
    }
}
