package studio.one.application.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.attachment.thumbnail.ThumbnailData;
import studio.one.application.attachment.thumbnail.ThumbnailService;
import studio.one.application.web.dto.AttachmentDto;
import studio.one.platform.web.dto.ApiResponse;

@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ObjectProvider<ThumbnailService> thumbnailServiceProvider;

    @Test
    void uploadSanitizesFilenameAndNormalizesContentType() throws Exception {
        AttachmentController controller = new AttachmentController(attachmentService, thumbnailServiceProvider);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "C:\\temp\\contract.pdf",
                "invalid content type",
                new byte[] { 1, 2, 3 });
        Attachment saved = mock(Attachment.class);

        when(attachmentService.createAttachment(
                eq(12),
                eq(34L),
                eq("contract.pdf"),
                eq("application/octet-stream"),
                any(InputStream.class),
                eq(3))).thenReturn(saved);

        ResponseEntity<ApiResponse<AttachmentDto>> response = controller.upload(12, 34L, file);

        assertEquals(200, response.getStatusCode().value());
        verify(attachmentService).createAttachment(
                eq(12),
                eq(34L),
                eq("contract.pdf"),
                eq("application/octet-stream"),
                any(InputStream.class),
                eq(3));
    }

    @Test
    void downloadBuildsAttachmentHeaders() throws Exception {
        AttachmentController controller = new AttachmentController(attachmentService, thumbnailServiceProvider);
        Attachment attachment = mock(Attachment.class);

        when(attachmentService.getAttachmentById(88L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2 }));
        when(attachment.getContentType()).thenReturn("application/pdf");
        when(attachment.getSize()).thenReturn(2L);
        when(attachment.getName()).thenReturn("report.pdf");

        ResponseEntity<?> response = controller.download(88L);

        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals(2L, response.getHeaders().getContentLength());
        assertEquals("attachment; filename=\"report.pdf\"",
                response.getHeaders().getFirst("Content-Disposition"));
    }

    @Test
    void thumbnailReturnsSameResponseShape() throws Exception {
        AttachmentController controller = new AttachmentController(attachmentService, thumbnailServiceProvider);
        Attachment attachment = mock(Attachment.class);
        ThumbnailService thumbnailService = mock(ThumbnailService.class);

        when(thumbnailServiceProvider.getIfAvailable()).thenReturn(thumbnailService);
        when(attachmentService.getAttachmentById(88L)).thenReturn(attachment);
        when(thumbnailService.getOrCreate(attachment, 128, "png"))
                .thenReturn(Optional.of(new ThumbnailData(new byte[] { 1, 2 }, "image/png")));

        ResponseEntity<?> response = controller.thumbnail(88L, 128, "png");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertEquals("ready", response.getHeaders().getFirst("X-Thumbnail-Status"));
        assertEquals(2L, response.getHeaders().getContentLength());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ((org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody) response.getBody())
                .writeTo(out);
        assertEquals(2, out.toByteArray().length);
    }

    @Test
    void thumbnailPendingReturnsImageWithNoStoreAndRetryAfter() throws Exception {
        AttachmentController controller = new AttachmentController(attachmentService, thumbnailServiceProvider);
        Attachment attachment = mock(Attachment.class);
        ThumbnailService thumbnailService = mock(ThumbnailService.class);

        when(thumbnailServiceProvider.getIfAvailable()).thenReturn(thumbnailService);
        when(attachmentService.getAttachmentById(88L)).thenReturn(attachment);
        when(thumbnailService.getOrCreate(attachment, 128, "png"))
                .thenReturn(Optional.of(new ThumbnailData(new byte[] { 1, 2 }, "image/png", "pending")));

        ResponseEntity<?> response = controller.thumbnail(88L, 128, "png");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("pending", response.getHeaders().getFirst("X-Thumbnail-Status"));
        assertEquals("3", response.getHeaders().getFirst("Retry-After"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }

    @Test
    void thumbnailOmittedSizeAndFormatUseServiceDefaults() throws Exception {
        AttachmentController controller = new AttachmentController(attachmentService, thumbnailServiceProvider);
        Attachment attachment = mock(Attachment.class);
        ThumbnailService thumbnailService = mock(ThumbnailService.class);

        when(thumbnailServiceProvider.getIfAvailable()).thenReturn(thumbnailService);
        when(attachmentService.getAttachmentById(88L)).thenReturn(attachment);
        when(thumbnailService.getOrCreate(attachment, 0, null))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.thumbnail(88L, null, null);

        assertEquals(204, response.getStatusCode().value());
        assertEquals("unavailable", response.getHeaders().getFirst("X-Thumbnail-Status"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
        verify(thumbnailService).getOrCreate(attachment, 0, null);
    }
}
