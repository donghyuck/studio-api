package studio.one.platform.textract.extractor.impl;

import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ParseWarning;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

@Slf4j
public class ImageFileParser extends AbstractFileParser implements StructuredFileParser {

    static final String KEY_BBOX = "bbox";
    static final String KEY_WORDS = "words";
    static final String KEY_MIN_CONFIDENCE = "minConfidence";
    static final String KEY_AVERAGE_CONFIDENCE = "averageConfidence";
    static final String KEY_WORD_COUNT = "wordCount";
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.60d;

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
            List<OcrToken> tokens = ocrTokens(image);
            List<ParsedBlock> blocks = tokens.isEmpty() ? ocrLineBlocks(text) : ocrLineBlocks(tokens);
            OcrQuality quality = OcrQuality.fromBlocks(blocks);
            ExtractedImage extractedImage = new ExtractedImage(
                    "image",
                    contentType,
                    filename,
                    image.getWidth(),
                    image.getHeight(),
                    imageMetadata(blocks.size(), quality.confidenceAvailable()));
            return new ParsedFile(
                    DocumentFormat.IMAGE,
                    text,
                    blocks,
                    fileMetadata(contentType, filename),
                    ocrWarnings(blocks),
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

    private List<OcrToken> ocrTokens(BufferedImage image) {
        return tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD).stream()
                .map(word -> new OcrToken(word.getText(), normalizeConfidence(word.getConfidence()), word.getBoundingBox()))
                .filter(token -> token.text() != null && !token.text().isBlank())
                .toList();
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
            blocks.add(ParsedBlock.text(
                    path,
                    BlockType.OCR_TEXT,
                    cleanedLine,
                    null,
                    order,
                    ocrBlockMetadata(path, order, null, List.of())));
        }
        return blocks;
    }

    List<ParsedBlock> ocrLineBlocks(List<OcrToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        List<ParsedBlock> blocks = new ArrayList<>();
        for (List<OcrToken> lineTokens : groupLineTokens(tokens)) {
            String text = lineTokens.stream()
                    .map(OcrToken::text)
                    .collect(Collectors.joining(" "));
            String cleanedLine = cleanText(text);
            if (cleanedLine == null || cleanedLine.isBlank()) {
                continue;
            }
            int order = blocks.size();
            String path = "image/ocr/line[" + order + "]";
            Double confidence = averageConfidence(lineTokens);
            blocks.add(ParsedBlock.text(
                    path,
                    BlockType.OCR_TEXT,
                    cleanedLine,
                    null,
                    order,
                    ocrBlockMetadata(path, order, confidence, lineTokens)));
        }
        return blocks;
    }

    private Map<String, Object> ocrBlockMetadata(String path, int order, Double confidence, List<OcrToken> tokens) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ParsedBlock.KEY_SOURCE_REF, path);
        metadata.put(ParsedBlock.KEY_ORDER, order);
        metadata.put(ExtractedImage.KEY_OCR_APPLIED, true);
        metadata.put(ExtractedImage.KEY_OCR_UNIT, "line");
        metadata.put(ExtractedImage.KEY_CONFIDENCE_AVAILABLE, confidence != null);
        if (confidence != null) {
            metadata.put(ParsedBlock.KEY_CONFIDENCE, confidence);
            metadata.put(KEY_WORD_COUNT, tokens.size());
            metadata.put(KEY_BBOX, bbox(union(tokens.stream()
                    .map(OcrToken::bbox)
                    .toList())));
            metadata.put(KEY_WORDS, tokens.stream()
                    .map(this::wordMetadata)
                    .toList());
        }
        return metadata;
    }

    private Map<String, Object> imageMetadata(int ocrLineCount, boolean confidenceAvailable) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ExtractedImage.KEY_SOURCE_REF, "image");
        metadata.put(ExtractedImage.KEY_OCR_APPLIED, true);
        metadata.put(ExtractedImage.KEY_OCR_LINE_COUNT, ocrLineCount);
        metadata.put(ExtractedImage.KEY_CONFIDENCE_AVAILABLE, confidenceAvailable);
        return metadata;
    }

    List<ParseWarning> ocrWarnings(List<ParsedBlock> blocks) {
        OcrQuality quality = OcrQuality.fromBlocks(blocks);
        if (!quality.confidenceAvailable() || quality.minConfidence() >= LOW_CONFIDENCE_THRESHOLD) {
            return List.of();
        }
        return List.of(ParseWarning.warning(
                "OCR_LOW_CONFIDENCE",
                "OCR result contains low-confidence text.",
                "image",
                Map.of(
                        KEY_MIN_CONFIDENCE, quality.minConfidence(),
                        KEY_AVERAGE_CONFIDENCE, quality.averageConfidence())));
    }

    private List<List<OcrToken>> groupLineTokens(List<OcrToken> tokens) {
        List<List<OcrToken>> lines = new ArrayList<>();
        for (OcrToken token : tokens) {
            if (token.bbox() == null) {
                addTokenToLine(lines, token);
                continue;
            }
            List<OcrToken> target = null;
            for (List<OcrToken> line : lines) {
                Rectangle existing = union(line.stream().map(OcrToken::bbox).toList());
                if (existing != null && verticallyOverlaps(existing, token.bbox())) {
                    target = line;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                lines.add(target);
            }
            target.add(token);
        }
        for (List<OcrToken> line : lines) {
            line.sort((left, right) -> Integer.compare(x(left.bbox()), x(right.bbox())));
        }
        return lines;
    }

    private void addTokenToLine(List<List<OcrToken>> lines, OcrToken token) {
        if (lines.isEmpty()) {
            lines.add(new ArrayList<>());
        }
        lines.get(lines.size() - 1).add(token);
    }

    private boolean verticallyOverlaps(Rectangle left, Rectangle right) {
        int top = Math.max(left.y, right.y);
        int bottom = Math.min(left.y + left.height, right.y + right.height);
        int overlap = bottom - top;
        return overlap > 0 && overlap >= Math.min(left.height, right.height) / 2;
    }

    private int x(Rectangle bbox) {
        return bbox == null ? 0 : bbox.x;
    }

    private Double averageConfidence(List<OcrToken> tokens) {
        List<Double> values = tokens.stream()
                .map(OcrToken::confidence)
                .filter(confidence -> confidence != null)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
    }

    private Double normalizeConfidence(float confidence) {
        if (confidence < 0) {
            return null;
        }
        return confidence > 1 ? confidence / 100d : (double) confidence;
    }

    private Rectangle union(List<Rectangle> boxes) {
        Rectangle union = null;
        for (Rectangle bbox : boxes) {
            if (bbox == null) {
                continue;
            }
            union = union == null ? new Rectangle(bbox) : union.union(bbox);
        }
        return union;
    }

    private Map<String, Object> wordMetadata(OcrToken token) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("text", token.text());
        if (token.confidence() != null) {
            metadata.put(ParsedBlock.KEY_CONFIDENCE, token.confidence());
        }
        if (token.bbox() != null) {
            metadata.put(KEY_BBOX, bbox(token.bbox()));
        }
        return metadata;
    }

    private Map<String, Object> bbox(Rectangle bbox) {
        if (bbox == null) {
            return Map.of();
        }
        return Map.of(
                "x", bbox.x,
                "y", bbox.y,
                "width", bbox.width,
                "height", bbox.height);
    }

    record OcrToken(String text, Double confidence, Rectangle bbox) {
    }

    private record OcrQuality(boolean confidenceAvailable, double minConfidence, double averageConfidence) {

        static OcrQuality fromBlocks(List<ParsedBlock> blocks) {
            List<Double> values = blocks.stream()
                    .map(ParsedBlock::confidence)
                    .filter(confidence -> confidence != null)
                    .toList();
            if (values.isEmpty()) {
                return new OcrQuality(false, 1d, 1d);
            }
            return new OcrQuality(
                    true,
                    values.stream().mapToDouble(Double::doubleValue).min().orElse(1d),
                    values.stream().mapToDouble(Double::doubleValue).average().orElse(1d));
        }
    }
}
