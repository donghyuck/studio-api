package studio.one.application.attachment.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.application.error.AttachmentDownloadUrlUnavailableException;
import studio.one.application.attachment.application.error.AttachmentNotFoundException;
import studio.one.application.attachment.application.usecase.AttachmentDownloadAuditLogService;
import studio.one.application.attachment.application.result.AttachmentDownloadAuditResult;
import studio.one.application.attachment.application.result.AttachmentDownloadTokenClaims;
import studio.one.application.attachment.application.result.AttachmentDownloadTokenInspection;
import studio.one.application.attachment.application.result.AttachmentDownloadUrl;
import studio.one.application.attachment.application.result.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.application.usecase.AttachmentDownloadUrlService;
import studio.one.application.attachment.application.usecase.AttachmentOwnerAccessAuthorizer;
import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.attachment.application.result.ThumbnailData;
import studio.one.application.attachment.application.usecase.ThumbnailService;
import studio.one.application.attachment.web.dto.response.AttachmentDownloadUrlDto;
import studio.one.application.attachment.web.dto.request.AttachmentDownloadUrlIssueRequestDto;
import studio.one.application.attachment.web.dto.response.AttachmentDto;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.web.dto.ApiResponse;

@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private AttachmentDownloadUrlService downloadUrlService;

    @Mock
    private AttachmentDownloadAuditLogService downloadAuditLogService;

    @Mock
    private AttachmentUrlIssueRequestDetailsResolver requestDetailsResolver;

    @Mock
    private ObjectProvider<ThumbnailService> thumbnailServiceProvider;

    @Mock
    private ObjectProvider<PrincipalResolver> principalResolverProvider;

    @Mock
    private ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers;

    @Test
    void uploadSanitizesFilenameAndNormalizesContentType() throws Exception {
        AttachmentController controller = controller();
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
        AttachmentController controller = controller();
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
        AttachmentController controller = controller();
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
        AttachmentController controller = controller();
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
        AttachmentController controller = controller();
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

    @Test
    void issueDownloadUrlReturnsNoStoreResponse() throws Exception {
        AttachmentController controller = controller();
        Attachment attachment = mock(Attachment.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        AttachmentUrlIssueRequestDetails details = new AttachmentUrlIssueRequestDetails("10.0.0.1", "JUnit");
        java.time.Instant expiresAt = java.time.Instant.parse("2026-05-04T00:05:00Z");

        when(attachmentService.getAttachmentById(88L)).thenReturn(attachment);
        when(requestDetailsResolver.resolve(request)).thenReturn(details);
        when(downloadUrlService.issueDownloadUrl(
                eq(attachment),
                eq(120L),
                eq(AttachmentDownloadUrlEndpointKind.SERVICE),
                any(),
                eq("10.0.0.1"),
                eq("JUnit"))).thenReturn(new AttachmentDownloadUrl("https://signed.example/download", expiresAt));

        ResponseEntity<ApiResponse<AttachmentDownloadUrlDto>> response = controller.issueDownloadUrl(
                88L,
                new AttachmentDownloadUrlIssueRequestDto(120L),
                request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("https://signed.example/download", response.getBody().getData().url());
        assertEquals(expiresAt, response.getBody().getData().expiresAt());
    }

    @Test
    void issueDownloadUrlMapsInvalidTtlToBadRequest() throws Exception {
        AttachmentController controller = controller();
        Attachment attachment = mock(Attachment.class);
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(attachmentService.getAttachmentById(88L)).thenReturn(attachment);
        when(requestDetailsResolver.resolve(request)).thenReturn(new AttachmentUrlIssueRequestDetails(null, null));
        when(downloadUrlService.issueDownloadUrl(eq(attachment), eq(0L), eq(AttachmentDownloadUrlEndpointKind.SERVICE),
                any(), eq(null), eq(null))).thenThrow(new IllegalArgumentException("ttlSeconds must be between 1 and 3600"));

        ResponseEntity<ApiResponse<AttachmentDownloadUrlDto>> response = controller.issueDownloadUrl(
                88L,
                new AttachmentDownloadUrlIssueRequestDto(0L),
                request);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void issueDownloadUrlMapsUnavailableStorageToConflict() throws Exception {
        AttachmentController controller = controller();
        Attachment attachment = mock(Attachment.class);
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(attachmentService.getAttachmentById(88L)).thenReturn(attachment);
        when(requestDetailsResolver.resolve(request)).thenReturn(new AttachmentUrlIssueRequestDetails(null, null));
        when(downloadUrlService.issueDownloadUrl(eq(attachment), eq(null), eq(AttachmentDownloadUrlEndpointKind.SERVICE),
                any(), eq(null), eq(null))).thenThrow(new AttachmentDownloadUrlUnavailableException());

        ResponseEntity<ApiResponse<AttachmentDownloadUrlDto>> response = controller.issueDownloadUrl(88L, null, request);

        assertEquals(409, response.getStatusCode().value());
    }

    @Test
    void signedDownloadStreamsAttachmentThroughAttachmentService() throws Exception {
        AttachmentController controller = controller();
        Attachment attachment = mock(Attachment.class);
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[] { 1, 2, 3 });

        when(attachmentService.getAttachmentById(88L)).thenReturn(attachment);
        when(attachment.getContentType()).thenReturn("application/pdf");
        when(attachment.getSize()).thenReturn(3L);
        when(attachment.getName()).thenReturn("sample.pdf");
        when(attachment.getAttachmentId()).thenReturn(88L);
        when(attachment.getObjectType()).thenReturn(12);
        when(attachment.getObjectId()).thenReturn(34L);
        when(attachmentService.getInputStream(attachment)).thenReturn(input);
        when(requestDetailsResolver.resolve(any())).thenReturn(new AttachmentUrlIssueRequestDetails("10.0.0.1", "JUnit"));
        when(downloadUrlService.inspectDownloadToken("signed-token"))
                .thenReturn(AttachmentDownloadTokenInspection.valid(
                        new AttachmentDownloadTokenClaims(
                                88L,
                                java.time.Instant.parse("2026-05-05T00:00:00Z"),
                                java.time.Instant.parse("2026-05-05T00:05:00Z"),
                                "nonce"),
                        "token-hash"));

        ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> response =
                controller.signedDownload("signed-token", new MockHttpServletRequest());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"sample.pdf\"", response.getHeaders().getFirst("Content-Disposition"));
        assertEquals(3, out.toByteArray().length);
        verify(downloadAuditLogService).record(org.mockito.ArgumentMatchers.argThat(command ->
                command.result() == AttachmentDownloadAuditResult.SUCCEEDED
                        && command.downloadedBytes() == 3L
                        && command.httpStatus() == 200
                        && "token-hash".equals(command.tokenHash())));
    }

    @Test
    void signedDownloadRejectsInvalidTokenWithUnauthorized() throws Exception {
        AttachmentController controller = controller();
        when(requestDetailsResolver.resolve(any())).thenReturn(new AttachmentUrlIssueRequestDetails("10.0.0.1", "JUnit"));
        when(downloadUrlService.inspectDownloadToken("bad-token"))
                .thenReturn(AttachmentDownloadTokenInspection.invalid("bad-token-hash"));

        ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> response =
                controller.signedDownload("bad-token", new MockHttpServletRequest());

        assertEquals(401, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        verify(downloadAuditLogService).record(org.mockito.ArgumentMatchers.argThat(command ->
                command.result() == AttachmentDownloadAuditResult.INVALID_TOKEN
                        && command.httpStatus() == 401
                        && "TOKEN_INVALID".equals(command.errorCode())
                        && "bad-token-hash".equals(command.tokenHash())));
    }

    @Test
    void signedDownloadRecordsExpiredTokenWithUnauthorized() throws Exception {
        AttachmentController controller = controller();
        when(requestDetailsResolver.resolve(any())).thenReturn(new AttachmentUrlIssueRequestDetails(null, null));
        when(downloadUrlService.inspectDownloadToken("expired-token"))
                .thenReturn(AttachmentDownloadTokenInspection.expired(
                        new AttachmentDownloadTokenClaims(
                                88L,
                                java.time.Instant.parse("2026-05-05T00:00:00Z"),
                                java.time.Instant.parse("2026-05-05T00:05:00Z"),
                                "nonce"),
                        "expired-hash"));

        ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> response =
                controller.signedDownload("expired-token", new MockHttpServletRequest());

        assertEquals(401, response.getStatusCode().value());
        verify(downloadAuditLogService).record(org.mockito.ArgumentMatchers.argThat(command ->
                command.result() == AttachmentDownloadAuditResult.EXPIRED
                        && command.attachmentId() == 88L
                        && command.httpStatus() == 401
                        && "TOKEN_EXPIRED".equals(command.errorCode())));
    }

    @Test
    void signedDownloadPropagatesNotFoundForDeletedAttachment() throws Exception {
        AttachmentController controller = controller();
        when(requestDetailsResolver.resolve(any())).thenReturn(new AttachmentUrlIssueRequestDetails(null, null));
        when(downloadUrlService.inspectDownloadToken("signed-token"))
                .thenReturn(AttachmentDownloadTokenInspection.valid(
                        new AttachmentDownloadTokenClaims(
                                88L,
                                java.time.Instant.parse("2026-05-05T00:00:00Z"),
                                java.time.Instant.parse("2026-05-05T00:05:00Z"),
                                "nonce"),
                        "token-hash"));
        when(attachmentService.getAttachmentById(88L)).thenThrow(AttachmentNotFoundException.byId(88L));

        assertThrows(AttachmentNotFoundException.class,
                () -> controller.signedDownload("signed-token", new MockHttpServletRequest()));
        verify(downloadAuditLogService).record(org.mockito.ArgumentMatchers.argThat(command ->
                command.result() == AttachmentDownloadAuditResult.FAILED
                        && command.httpStatus() == 404
                        && "ATTACHMENT_NOT_FOUND".equals(command.errorCode())));
    }

    @Test
    void signedDownloadRecordsStreamFailureAndPropagates() throws Exception {
        AttachmentController controller = controller();
        Attachment attachment = mock(Attachment.class);
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };

        when(requestDetailsResolver.resolve(any())).thenReturn(new AttachmentUrlIssueRequestDetails(null, null));
        when(downloadUrlService.inspectDownloadToken("signed-token"))
                .thenReturn(AttachmentDownloadTokenInspection.valid(
                        new AttachmentDownloadTokenClaims(
                                88L,
                                java.time.Instant.parse("2026-05-05T00:00:00Z"),
                                java.time.Instant.parse("2026-05-05T00:05:00Z"),
                                "nonce"),
                        "token-hash"));
        when(attachmentService.getAttachmentById(88L)).thenReturn(attachment);
        when(attachment.getContentType()).thenReturn("application/pdf");
        when(attachment.getSize()).thenReturn(3L);
        when(attachment.getName()).thenReturn("sample.pdf");
        when(attachment.getAttachmentId()).thenReturn(88L);
        when(attachment.getObjectType()).thenReturn(12);
        when(attachment.getObjectId()).thenReturn(34L);
        when(attachmentService.getInputStream(attachment)).thenReturn(failing);

        ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> response =
                controller.signedDownload("signed-token", new MockHttpServletRequest());

        assertThrows(IOException.class, () -> response.getBody().writeTo(new ByteArrayOutputStream()));
        verify(downloadAuditLogService).record(org.mockito.ArgumentMatchers.argThat(command ->
                command.result() == AttachmentDownloadAuditResult.FAILED
                        && command.httpStatus() == 200
                        && command.downloadedBytes() == 0L
                        && "STREAM_FAILED".equals(command.errorCode())));
    }

    private AttachmentController controller() {
        return new AttachmentController(
                attachmentService,
                downloadUrlService,
                downloadAuditLogService,
                requestDetailsResolver,
                thumbnailServiceProvider,
                principalResolverProvider,
                ownerAccessAuthorizers,
                objectType -> java.util.Optional.empty());
    }
}
