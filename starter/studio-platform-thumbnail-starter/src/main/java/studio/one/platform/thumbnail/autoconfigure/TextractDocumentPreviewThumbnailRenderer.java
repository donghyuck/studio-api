package studio.one.platform.thumbnail.autoconfigure;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import studio.one.platform.textract.model.ParsedFile;
import studio.one.platform.textract.service.FileContentExtractionService;
import studio.one.platform.thumbnail.ThumbnailFormats;
import studio.one.platform.thumbnail.ThumbnailGenerationException;
import studio.one.platform.thumbnail.ThumbnailImages;
import studio.one.platform.thumbnail.ThumbnailOptions;
import studio.one.platform.thumbnail.ThumbnailRenderer;
import studio.one.platform.thumbnail.ThumbnailResult;
import studio.one.platform.thumbnail.ThumbnailSource;
import studio.one.platform.thumbnail.renderer.DocxThumbnailRenderer;
import studio.one.platform.thumbnail.renderer.HwpThumbnailRenderer;
import studio.one.platform.thumbnail.renderer.HwpxThumbnailRenderer;

class TextractDocumentPreviewThumbnailRenderer implements ThumbnailRenderer {

    private static final int CANVAS_WIDTH = 480;
    private static final int CANVAS_HEIGHT = 640;
    private static final int PADDING = 36;
    private static final int MAX_LINES = 14;
    private static final int MAX_CHARS = 1200;

    private final FileContentExtractionService extractionService;
    private final String label;
    private final Set<String> contentTypes;
    private final Set<String> extensions;

    protected TextractDocumentPreviewThumbnailRenderer(
            FileContentExtractionService extractionService,
            String label,
            Set<String> contentTypes,
            Set<String> extensions) {
        this.extractionService = extractionService;
        this.label = label;
        this.contentTypes = Set.copyOf(contentTypes);
        this.extensions = Set.copyOf(extensions);
    }

    static DocxThumbnailRenderer docx(FileContentExtractionService extractionService) {
        return new TextractDocxPreviewThumbnailRenderer(extractionService);
    }

    static HwpThumbnailRenderer hwp(FileContentExtractionService extractionService) {
        return new TextractHwpPreviewThumbnailRenderer(extractionService);
    }

    static HwpxThumbnailRenderer hwpx(FileContentExtractionService extractionService) {
        return new TextractHwpxPreviewThumbnailRenderer(extractionService);
    }

    @Override
    public boolean supports(ThumbnailSource source) {
        String contentType = source.contentType().toLowerCase(Locale.ROOT);
        if (contentTypes.contains(contentType)) {
            return true;
        }
        String filename = source.filename().toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(filename::endsWith);
    }

    @Override
    public ThumbnailResult render(ThumbnailSource source, ThumbnailOptions options) {
        try {
            ParsedFile parsed = extractionService.parseStructured(
                    source.contentType(),
                    source.filename(),
                    new ByteArrayInputStream(source.bytes()));
            BufferedImage preview = drawPreview(source.filename(), parsed);
            BufferedImage scaled = ThumbnailImages.scale(preview, options.size());
            byte[] bytes = ThumbnailImages.write(scaled, options.format());
            return new ThumbnailResult(bytes, ThumbnailFormats.contentType(options.format()), options.format());
        } catch (Exception ex) {
            throw new ThumbnailGenerationException("Failed to render document preview thumbnail source", ex);
        }
    }

    private BufferedImage drawPreview(String filename, ParsedFile parsed) {
        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(new Color(248, 250, 252));
            graphics.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
            graphics.setColor(new Color(45, 65, 89));
            graphics.fillRect(0, 0, CANVAS_WIDTH, 88);

            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            graphics.drawString(label, PADDING, 55);

            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            graphics.drawString(trim(filename, 36), PADDING + 110, 55);

            graphics.setColor(new Color(226, 232, 240));
            graphics.fillRect(PADDING, 118, CANVAS_WIDTH - (PADDING * 2), 2);

            graphics.setColor(new Color(15, 23, 42));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            graphics.drawString(trim(title(filename, parsed), 34), PADDING, 158);

            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
            graphics.setColor(new Color(51, 65, 85));
            FontMetrics metrics = graphics.getFontMetrics();
            int y = 200;
            for (String line : previewLines(parsed.plainText(), metrics, CANVAS_WIDTH - (PADDING * 2))) {
                graphics.drawString(line, PADDING, y);
                y += 28;
            }

            graphics.setColor(new Color(100, 116, 139));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            String footer = parsed.blocks().size() + " blocks / " + parsed.tables().size()
                    + " tables / " + parsed.images().size() + " images";
            graphics.drawString(footer, PADDING, CANVAS_HEIGHT - 34);
            return image;
        } finally {
            graphics.dispose();
        }
    }

    private String title(String filename, ParsedFile parsed) {
        return parsed.plainText().lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(filename == null || filename.isBlank() ? label + " document" : filename);
    }

    private List<String> previewLines(String text, FontMetrics metrics, int maxWidth) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.length() > MAX_CHARS) {
            normalized = normalized.substring(0, MAX_CHARS);
        }
        if (normalized.isBlank()) {
            return List.of("No preview text extracted.");
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : normalized.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (metrics.stringWidth(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
                if (lines.size() == MAX_LINES) {
                    return lines;
                }
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (!current.isEmpty() && lines.size() < MAX_LINES) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static final class TextractDocxPreviewThumbnailRenderer
            extends TextractDocumentPreviewThumbnailRenderer implements DocxThumbnailRenderer {

        private TextractDocxPreviewThumbnailRenderer(FileContentExtractionService extractionService) {
            super(
                    extractionService,
                    "DOCX",
                    Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    Set.of(".docx"));
        }
    }

    private static final class TextractHwpPreviewThumbnailRenderer
            extends TextractDocumentPreviewThumbnailRenderer implements HwpThumbnailRenderer {

        private TextractHwpPreviewThumbnailRenderer(FileContentExtractionService extractionService) {
            super(
                    extractionService,
                    "HWP",
                    Set.of("application/x-hwp", "application/haansofthwp", "application/vnd.hancom.hwp"),
                    Set.of(".hwp"));
        }
    }

    private static final class TextractHwpxPreviewThumbnailRenderer
            extends TextractDocumentPreviewThumbnailRenderer implements HwpxThumbnailRenderer {

        private TextractHwpxPreviewThumbnailRenderer(FileContentExtractionService extractionService) {
            super(
                    extractionService,
                    "HWPX",
                    Set.of("application/x-hwpx", "application/vnd.hancom.hwpx", "application/hwpx"),
                    Set.of(".hwpx"));
        }
    }
}
