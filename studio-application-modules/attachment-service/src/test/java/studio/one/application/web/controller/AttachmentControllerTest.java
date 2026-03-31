package studio.one.application.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
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
}
