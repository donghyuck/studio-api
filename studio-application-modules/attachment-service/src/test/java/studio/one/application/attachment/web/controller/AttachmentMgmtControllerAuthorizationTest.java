package studio.one.application.attachment.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.application.result.AttachmentDownloadAuditResult;
import studio.one.application.attachment.application.usecase.AttachmentDownloadUrlService;
import studio.one.application.attachment.application.usecase.AttachmentDownloadAuditLogService;
import studio.one.application.attachment.application.result.AttachmentOwnerAccessAction;
import studio.one.application.attachment.application.usecase.AttachmentOwnerAccessAuthorizer;
import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.objecttype.application.WellKnownAttachmentObjectTypes;
import studio.one.application.attachment.application.usecase.ThumbnailService;
import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;

@ExtendWith(MockitoExtension.class)
class AttachmentMgmtControllerAuthorizationTest {

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private AttachmentDownloadUrlService downloadUrlService;

    @Mock
    private AttachmentDownloadAuditLogService downloadAuditLogService;

    @Mock
    private AttachmentUrlIssueRequestDetailsResolver requestDetailsResolver;

    @Mock
    private ObjectProvider<IdentityService> identityServiceProvider;

    @Mock
    private ObjectProvider<PrincipalResolver> principalResolverProvider;

    @Mock
    private ObjectProvider<FileContentExtractionService> textExtractionProvider;

    @Mock
    private ObjectProvider<ThumbnailService> thumbnailServiceProvider;

    @Mock
    private ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers;

    @Mock
    private PrincipalResolver principalResolver;

    @Test
    void getRejectsOtherUsersAttachmentForNonAdmin() throws Exception {
        AttachmentMgmtController controller = controller();
        Attachment attachment = mock(Attachment.class);

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal(7L, "USER"));
        when(attachmentService.getAttachmentById(10L)).thenReturn(attachment);
        when(attachment.getCreatedBy()).thenReturn(99L);

        assertThrows(AccessDeniedException.class, () -> controller.get(10L));
        verify(attachmentService).getAttachmentById(10L);
    }

    @Test
    void listUsesCreatorScopedQueryForNonAdmin() {
        AttachmentMgmtController controller = controller();

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal(7L, "USER"));
        when(attachmentService.findAttachmentsByCreator(7L, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        controller.list(null, null, null, Pageable.unpaged());

        verify(attachmentService).findAttachmentsByCreator(7L, Pageable.unpaged());
    }

    @Test
    void listByObjectUsesGlobalQueryForAdmin() {
        AttachmentMgmtController controller = controller();

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal(1L, "ADMIN"));
        when(attachmentService.getAttachments(12, 34L)).thenReturn(Collections.emptyList());

        controller.listByObject(12, 34L);

        verify(attachmentService).getAttachments(12, 34L);
    }

    @Test
    void listByObjectUsesGlobalQueryForRoleAdmin() {
        AttachmentMgmtController controller = controller();

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal(1L, "ROLE_ADMIN"));
        when(attachmentService.getAttachments(12, 34L)).thenReturn(Collections.emptyList());

        controller.listByObject(12, 34L);

        verify(attachmentService).getAttachments(12, 34L);
    }

    @Test
    void listByObjectRejectsWellKnownDomainAttachmentWithoutOwnerAuthorizer() {
        AttachmentMgmtController controller = controller();

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal(7L, "USER"));
        when(ownerAccessAuthorizers.orderedStream()).thenReturn(Stream.empty());

        assertThrows(AccessDeniedException.class, () -> controller.listByObject(
                WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT,
                34L));
    }

    @Test
    void listByObjectUsesDomainAuthorizerForWellKnownAttachment() {
        AttachmentMgmtController controller = controller();
        AttachmentOwnerAccessAuthorizer authorizer = mock(AttachmentOwnerAccessAuthorizer.class);

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal(7L, "USER"));
        when(ownerAccessAuthorizers.orderedStream()).thenReturn(Stream.of(authorizer));
        when(authorizer.supports(WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT)).thenReturn(true);
        when(authorizer.canAccess(
                WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT,
                34L,
                principalResolver.currentOrNull(),
                AttachmentOwnerAccessAction.LIST)).thenReturn(true);
        when(attachmentService.getAttachments(WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT, 34L))
                .thenReturn(Collections.emptyList());

        controller.listByObject(WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT, 34L);

        verify(attachmentService).getAttachments(WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT, 34L);
    }

    @Test
    void extractTextPropagatesLimitFailure() throws Exception {
        AttachmentMgmtController controller = controller();
        Attachment attachment = mock(Attachment.class);
        FileContentExtractionService extractor = new FileContentExtractionService(
                mock(studio.one.platform.textract.application.usecase.FileParserFactory.class), 4);

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal(1L, "ROLE_ADMIN"));
        when(textExtractionProvider.getIfAvailable()).thenReturn(extractor);
        when(attachmentService.getAttachmentById(10L)).thenReturn(attachment);
        when(attachment.getName()).thenReturn("large.txt");
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4, 5 }));

        assertThrows(FileParseException.class, () -> controller.extractText(10L));
    }

    @Test
    void downloadRecordsManagementAuditLog() throws Exception {
        AttachmentMgmtController controller = controller();
        Attachment attachment = mock(Attachment.class);
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal(1L, "ROLE_ADMIN"));
        when(requestDetailsResolver.resolve(request)).thenReturn(new AttachmentUrlIssueRequestDetails("10.0.0.3", "JUnit"));
        when(attachmentService.getAttachmentById(10L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment)).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
        when(attachment.getAttachmentId()).thenReturn(10L);
        when(attachment.getObjectType()).thenReturn(20);
        when(attachment.getObjectId()).thenReturn(30L);
        when(attachment.getContentType()).thenReturn("application/pdf");
        when(attachment.getName()).thenReturn("report.pdf");
        when(attachment.getSize()).thenReturn(3L);

        ResponseEntity<StreamingResponseBody> response = controller.download(10L, request);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);

        assertEquals(3, out.toByteArray().length);
        verify(downloadAuditLogService).record(org.mockito.ArgumentMatchers.argThat(command ->
                command.result() == AttachmentDownloadAuditResult.SUCCEEDED
                        && command.httpStatus() == 200
                        && command.downloadedBytes() == 3L
                        && command.tokenHash() == null
                        && "MGMT_DIRECT".equals(command.linkType())
                        && command.attachmentId() == 10L
                        && command.objectType() == 20
                        && command.objectId() == 30L
                        && "10.0.0.3".equals(command.clientIp())
                        && "JUnit".equals(command.userAgent())));
    }

    @Test
    void downloadRecordsAccessDeniedAuditLog() throws Exception {
        AttachmentMgmtController controller = controller();
        Attachment attachment = mock(Attachment.class);
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal(7L, "USER"));
        when(requestDetailsResolver.resolve(request)).thenReturn(new AttachmentUrlIssueRequestDetails("10.0.0.5", "JUnit"));
        when(attachmentService.getAttachmentById(10L)).thenReturn(attachment);
        when(attachment.getAttachmentId()).thenReturn(10L);
        when(attachment.getObjectType()).thenReturn(20);
        when(attachment.getObjectId()).thenReturn(30L);
        when(attachment.getCreatedBy()).thenReturn(99L);

        assertThrows(AccessDeniedException.class, () -> controller.download(10L, request));

        verify(downloadAuditLogService).record(org.mockito.ArgumentMatchers.argThat(command ->
                command.result() == AttachmentDownloadAuditResult.FAILED
                        && command.httpStatus() == 403
                        && command.downloadedBytes() == null
                        && command.tokenHash() == null
                        && "MGMT_DIRECT".equals(command.linkType())
                        && command.attachmentId() == 10L
                        && command.objectType() == 20
                        && command.objectId() == 30L
                        && "10.0.0.5".equals(command.clientIp())
                        && "JUnit".equals(command.userAgent())
                        && "ACCESS_DENIED".equals(command.errorCode())));
    }

    private AttachmentMgmtController controller() {
        return new AttachmentMgmtController(
                attachmentService,
                downloadUrlService,
                downloadAuditLogService,
                requestDetailsResolver,
                identityServiceProvider,
                principalResolverProvider,
                textExtractionProvider,
                thumbnailServiceProvider,
                ownerAccessAuthorizers,
                objectType -> java.util.Optional.empty());
    }

    private ApplicationPrincipal principal(Long userId, String role) {
        return new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getUsername() {
                return "user-" + userId;
            }

            @Override
            public Set<String> getRoles() {
                return Set.of(role);
            }
        };
    }
}
