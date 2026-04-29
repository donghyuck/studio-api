package studio.one.application.attachment.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.platform.thumbnail.ThumbnailGenerationOptions;
import studio.one.platform.thumbnail.ThumbnailGenerationService;
import studio.one.platform.thumbnail.ThumbnailRendererFactory;
import studio.one.platform.thumbnail.renderer.ImageThumbnailRenderer;
import studio.one.platform.thumbnail.renderer.PdfThumbnailRenderer;

class ThumbnailServiceImplTest {

    @Test
    void imageAttachmentUsesPlatformGenerationService() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        ThumbnailServiceImpl service = newService(attachmentService, storage);
        Attachment attachment = attachment(10L, "sample.png", "image/png");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream(imageBytes()));

        var result = service.getOrCreate(attachment, 64, "png");

        assertThat(result).isPresent();
        assertThat(result.get().getContentType()).isEqualTo("image/png");
        verify(storage).save(any(ThumbnailKey.class), any(InputStream.class));
    }

    @Test
    void pdfAttachmentUsesPlatformGenerationServiceWhenPdfboxIsAvailable() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        ThumbnailServiceImpl service = newService(attachmentService, storage);
        Attachment attachment = attachment(11L, "sample.pdf", "application/pdf");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream(pdfBytes()));

        var result = service.getOrCreate(attachment, 64, "png");

        assertThat(result).isPresent();
        assertThat(result.get().getContentType()).isEqualTo("image/png");
        verify(storage).save(any(ThumbnailKey.class), any(InputStream.class));
    }

    @Test
    void unsupportedAttachmentReturnsEmptyWithoutWritingStorage() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        ThumbnailServiceImpl service = newService(attachmentService, storage);
        Attachment attachment = attachment(12L, "sample.txt", "text/plain");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream("text".getBytes()));

        assertThat(service.getOrCreate(attachment, 64, "png")).isEmpty();
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyConstructorStillSupportsImageThumbnails() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        ThumbnailServiceImpl service = new ThumbnailServiceImpl(attachmentService, storage, 128, "png");
        Attachment attachment = attachment(13L, "sample.png", "image/png");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream(imageBytes()));

        var result = service.getOrCreate(attachment, 64, "png");

        assertThat(result).isPresent();
        verify(storage).save(any(ThumbnailKey.class), any(InputStream.class));
    }

    private static ThumbnailServiceImpl newService(AttachmentService attachmentService, ThumbnailStorage storage) {
        ThumbnailGenerationService generationService = new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(new ImageThumbnailRenderer(), new PdfThumbnailRenderer(0))),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000));
        return new ThumbnailServiceImpl(attachmentService, storage, generationService);
    }

    private static Attachment attachment(long id, String name, String contentType) {
        Attachment attachment = mock(Attachment.class);
        when(attachment.getAttachmentId()).thenReturn(id);
        when(attachment.getObjectType()).thenReturn(2001);
        when(attachment.getName()).thenReturn(name);
        when(attachment.getContentType()).thenReturn(contentType);
        return attachment;
    }

    private static byte[] imageBytes() throws IOException {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 200, 100);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] pdfBytes() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }
}
