package studio.one.application.attachment.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.platform.thumbnail.ThumbnailGenerationOptions;
import studio.one.platform.thumbnail.ThumbnailGenerationService;
import studio.one.platform.thumbnail.ThumbnailOptions;
import studio.one.platform.thumbnail.ThumbnailRenderer;
import studio.one.platform.thumbnail.ThumbnailRendererFactory;
import studio.one.platform.thumbnail.ThumbnailSource;
import studio.one.platform.thumbnail.ThumbnailGenerationException;
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
        assertThat(result.get().getStatus()).isEqualTo("pending");
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
        assertThat(result.get().getStatus()).isEqualTo("pending");
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

        var result = service.getOrCreate(attachment, 64, "png");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("pending");
        verify(storage, never()).save(any(), any());
    }

    @Test
    void rendererFailureReturnsEmptyWithoutWritingStorage() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        ThumbnailGenerationService generationService = new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(new FailingRenderer())),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000));
        ThumbnailServiceImpl service = new ThumbnailServiceImpl(attachmentService, storage, generationService);
        Attachment attachment = attachment(14L, "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream("docx".getBytes()));

        var result = service.getOrCreate(attachment, 64, "png");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("pending");
        verify(storage, never()).save(any(), any());
    }

    @Test
    void deterministicRendererFailureIsMemoizedAfterCacheMiss() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        ThumbnailGenerationService generationService = new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(new FailingRenderer())),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000));
        ThumbnailServiceImpl service = new ThumbnailServiceImpl(attachmentService, storage, generationService);
        Attachment attachment = attachment(15L, "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream("docx".getBytes()));

        assertThat(service.getOrCreate(attachment, 64, "png")).isPresent();
        assertThat(service.getOrCreate(attachment, 64, "png")).isEmpty();
        assertThat(service.getOrCreate(attachment, 96, "png")).isEmpty();

        verify(attachmentService, times(1)).getInputStream(attachment);
        verify(storage, never()).save(any(), any());
    }

    @Test
    void sourceLoadFailureIsMemoizedAfterBackgroundAttempt() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        ThumbnailServiceImpl service = newService(attachmentService, storage);
        Attachment attachment = attachment(17L, "sample.png", "image/png");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenThrow(new IOException("source missing"));

        assertThat(service.getOrCreate(attachment, 64, "png")).isPresent();
        assertThat(service.getOrCreate(attachment, 64, "png")).isEmpty();

        verify(attachmentService, times(1)).getInputStream(attachment);
        verify(storage, never()).save(any(), any());
    }

    @Test
    void asyncExecutorReturnsPendingBeforeGenerationRuns() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        CapturingExecutor executor = new CapturingExecutor();
        ThumbnailGenerationService generationService = new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(new ImageThumbnailRenderer())),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000));
        ThumbnailServiceImpl service = new ThumbnailServiceImpl(
                attachmentService,
                storage,
                generationService,
                executor);
        Attachment attachment = attachment(18L, "sample.png", "image/png");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream(imageBytes()));

        var result = service.getOrCreate(attachment, 64, "png");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("pending");
        verify(attachmentService, never()).getInputStream(attachment);

        executor.runNext();

        verify(attachmentService, times(1)).getInputStream(attachment);
        verify(storage).save(any(ThumbnailKey.class), any(InputStream.class));
    }

    @Test
    void concurrentRequestsForDifferentSizesShareOneBackgroundJob() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        CapturingExecutor executor = new CapturingExecutor();
        ThumbnailGenerationService generationService = new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(new ImageThumbnailRenderer())),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000));
        ThumbnailServiceImpl service = new ThumbnailServiceImpl(
                attachmentService,
                storage,
                generationService,
                executor);
        Attachment attachment = attachment(20L, "sample.png", "image/png");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream(imageBytes()));

        assertThat(service.getOrCreate(attachment, 64, "png")).isPresent();
        assertThat(service.getOrCreate(attachment, 96, "png")).isPresent();

        assertThat(executor.taskCount()).isEqualTo(1);
        verify(attachmentService, never()).getInputStream(attachment);

        executor.runNext();

        verify(attachmentService, times(1)).getInputStream(attachment);
        verify(storage).save(any(ThumbnailKey.class), any(InputStream.class));
    }

    @Test
    void rejectedAsyncGenerationDoesNotRunOnRequestThread() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        ThumbnailServiceImpl service = new ThumbnailServiceImpl(
                attachmentService,
                storage,
                new ThumbnailGenerationService(
                        new ThumbnailRendererFactory(List.of(new ImageThumbnailRenderer())),
                        new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000)),
                runnable -> {
                    throw new RejectedExecutionException("queue full");
                });
        Attachment attachment = attachment(19L, "sample.png", "image/png");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));

        assertThat(service.getOrCreate(attachment, 64, "png")).isEmpty();

        verify(attachmentService, never()).getInputStream(attachment);
        verify(storage, never()).save(any(), any());
    }

    @Test
    void deleteAllPreventsQueuedBackgroundTaskFromSavingStaleThumbnail() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        CapturingExecutor executor = new CapturingExecutor();
        ThumbnailServiceImpl service = new ThumbnailServiceImpl(
                attachmentService,
                storage,
                new ThumbnailGenerationService(
                        new ThumbnailRendererFactory(List.of(new ImageThumbnailRenderer())),
                        new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000)),
                executor);
        Attachment attachment = attachment(21L, "sample.png", "image/png");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream(imageBytes()));

        assertThat(service.getOrCreate(attachment, 64, "png")).isPresent();

        service.deleteAll(attachment);
        executor.runNext();

        verify(storage).deleteAll(2001, 21L);
        verify(storage, never()).save(any(), any());
    }

    @Test
    void unsupportedAttachmentIsMemoizedAfterCacheMiss() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        ThumbnailStorage storage = mock(ThumbnailStorage.class);
        ThumbnailServiceImpl service = newService(attachmentService, storage);
        Attachment attachment = attachment(16L, "sample.txt", "text/plain");

        when(storage.load(any())).thenThrow(new IllegalStateException("miss"));
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream("text".getBytes()));

        assertThat(service.getOrCreate(attachment, 64, "png")).isPresent();
        assertThat(service.getOrCreate(attachment, 64, "png")).isEmpty();

        verify(attachmentService, times(1)).getInputStream(attachment);
        verify(storage, never()).save(any(), any());
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
        assertThat(result.get().getStatus()).isEqualTo("pending");
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

    private static final class FailingRenderer implements ThumbnailRenderer {
        @Override
        public boolean supports(ThumbnailSource source) {
            return true;
        }

        @Override
        public studio.one.platform.thumbnail.ThumbnailResult render(
                ThumbnailSource source,
                ThumbnailOptions options) {
            throw new ThumbnailGenerationException("conversion failed");
        }
    }

    private static final class CapturingExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() {
            tasks.remove().run();
        }

        private int taskCount() {
            return tasks.size();
        }
    }
}
