package studio.one.application.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.application.web.dto.AttachmentDto;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.text.service.FileContentExtractionService;
import studio.one.platform.web.dto.ApiResponse;

@ExtendWith(MockitoExtension.class)
class MeAttachmentControllerTest {

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ObjectProvider<IdentityService> identityServiceProvider;

    @Mock
    private ObjectProvider<FileContentExtractionService> textExtractionProvider;

    @Test
    void getReturnsForbiddenWhenOwnerMismatch() throws Exception {
        MeAttachmentController controller = new MeAttachmentController(
                attachmentService,
                identityServiceProvider,
                textExtractionProvider);
        Attachment attachment = mock(Attachment.class);

        when(attachmentService.getAttachmentById(44L)).thenReturn(attachment);
        when(attachment.getCreatedBy()).thenReturn(99L);

        ResponseEntity<ApiResponse<AttachmentDto>> response = controller.get(44L, 7L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void uploadSanitizesFilenameAndNormalizesContentType() throws Exception {
        MeAttachmentController controller = new MeAttachmentController(
                attachmentService,
                identityServiceProvider,
                textExtractionProvider);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "/tmp/report.txt",
                "invalid content type",
                new byte[] { 1, 2, 3, 4 });
        Attachment saved = mock(Attachment.class);

        when(attachmentService.createAttachment(
                eq(10),
                eq(20L),
                eq("report.txt"),
                eq("application/octet-stream"),
                any(),
                eq(4))).thenReturn(saved);

        ResponseEntity<ApiResponse<AttachmentDto>> response = controller.upload(10, 20L, file, 7L);

        assertEquals(200, response.getStatusCode().value());
        verify(attachmentService).createAttachment(
                eq(10),
                eq(20L),
                eq("report.txt"),
                eq("application/octet-stream"),
                any(),
                eq(4));
    }
}
