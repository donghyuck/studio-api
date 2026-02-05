package studio.one.application.attachment.thumbnail;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;

@RequiredArgsConstructor
@Slf4j
public class ThumbnailServiceImpl implements ThumbnailService {

    private static final int MIN_SIZE = 16;
    private static final int MAX_SIZE = 512;

    private final AttachmentService attachmentService;
    private final ThumbnailStorage thumbnailStorage;
    private final int defaultSize;
    private final String defaultFormat;

    @Override
    public Optional<ThumbnailData> getOrCreate(Attachment attachment, int size, String format) {
        if (attachment == null) {
            return Optional.empty();
        }
        if (!isImage(attachment.getContentType())) {
            return Optional.empty();
        }
        int normalizedSize = normalizeSize(size);
        String normalizedFormat = normalizeFormat(format);
        ThumbnailKey key = new ThumbnailKey(
                attachment.getObjectType(),
                attachment.getAttachmentId(),
                normalizedSize,
                normalizedFormat);
        try (InputStream cached = thumbnailStorage.load(key)) {
            byte[] bytes = cached.readAllBytes();
            return Optional.of(new ThumbnailData(bytes, contentTypeFor(normalizedFormat)));
        } catch (RuntimeException | java.io.IOException ignored) {
            // cache miss or load error -> generate
        }

        try (InputStream source = attachmentService.getInputStream(attachment)) {
            BufferedImage original = ImageIO.read(source);
            if (original == null) {
                return Optional.empty();
            }
            BufferedImage scaled = scaleImage(original, normalizedSize);
            byte[] bytes = toBytes(scaled, normalizedFormat);
            thumbnailStorage.save(key, new ByteArrayInputStream(bytes));
            return Optional.of(new ThumbnailData(bytes, contentTypeFor(normalizedFormat)));
        } catch (Exception e) {
            log.warn("Thumbnail generate failed for id={}: {}", attachment.getAttachmentId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void deleteAll(Attachment attachment) {
        if (attachment == null) {
            return;
        }
        try {
            thumbnailStorage.deleteAll(attachment.getObjectType(), attachment.getAttachmentId());
        } catch (RuntimeException ex) {
            log.debug("Thumbnail deleteAll failed for id={}: {}", attachment.getAttachmentId(), ex.getMessage());
        }
    }

    private boolean isImage(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        return contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private int normalizeSize(int size) {
        int value = size > 0 ? size : defaultSize;
        if (value < MIN_SIZE) {
            return MIN_SIZE;
        }
        if (value > MAX_SIZE) {
            return MAX_SIZE;
        }
        return value;
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return defaultFormat;
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("png")) {
            return defaultFormat;
        }
        return normalized;
    }

    private String contentTypeFor(String format) {
        if ("png".equalsIgnoreCase(format)) {
            return "image/png";
        }
        return "image/png";
    }

    private BufferedImage scaleImage(BufferedImage original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        if (width <= maxSize && height <= maxSize) {
            return original;
        }
        float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
        int targetWidth = Math.max(1, Math.round(width * ratio));
        int targetHeight = Math.max(1, Math.round(height * ratio));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2d.dispose();
        }
        return scaled;
    }

    private byte[] toBytes(BufferedImage image, String format) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        }
    }
}
