package studio.one.application.attachment.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.application.result.AttachmentOwnerAccessAction;
import studio.one.application.attachment.application.usecase.AttachmentOwnerAccessAuthorizer;
import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.attachment.web.dto.response.AttachmentDto;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.objecttype.application.WellKnownAttachmentObjectTypes;
import studio.one.platform.textract.service.FileContentExtractionService;
import studio.one.platform.web.dto.ApiResponse;

@ExtendWith(MockitoExtension.class)
class MeAttachmentControllerTest {

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ObjectProvider<IdentityService> identityServiceProvider;

    @Mock
    private ObjectProvider<FileContentExtractionService> textExtractionProvider;

    @Mock
    private ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerAccessAuthorizers;

    @Mock
    private ObjectProvider<PrincipalResolver> principalResolverProvider;

    @Mock
    private PrincipalResolver principalResolver;

    @Test
    void getReturnsForbiddenWhenOwnerMismatch() throws Exception {
        MeAttachmentController controller = new MeAttachmentController(
                attachmentService,
                identityServiceProvider,
                textExtractionProvider,
                ownerAccessAuthorizers,
                principalResolverProvider);
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
                textExtractionProvider,
                ownerAccessAuthorizers,
                principalResolverProvider);
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

    @Test
    void wellKnownAttachmentUsesResolvedPrincipalForOwnerAuthorizer() throws Exception {
        MeAttachmentController controller = new MeAttachmentController(
                attachmentService,
                identityServiceProvider,
                textExtractionProvider,
                ownerAccessAuthorizers,
                principalResolverProvider);
        Attachment attachment = mock(Attachment.class);
        AttachmentOwnerAccessAuthorizer authorizer = mock(AttachmentOwnerAccessAuthorizer.class);
        ApplicationPrincipal principal = principal(7L, "alice", "WORKSPACE_MEMBER");

        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal);
        when(ownerAccessAuthorizers.orderedStream()).thenReturn(Stream.of(authorizer));
        when(authorizer.supports(WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT)).thenReturn(true);
        when(authorizer.canAccess(
                WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT,
                20L,
                principal,
                AttachmentOwnerAccessAction.READ)).thenReturn(true);
        when(attachmentService.getAttachmentById(44L)).thenReturn(attachment);
        when(attachment.getCreatedBy()).thenReturn(7L);
        when(attachment.getObjectType()).thenReturn(WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT);
        when(attachment.getObjectId()).thenReturn(20L);

        ResponseEntity<ApiResponse<AttachmentDto>> response = controller.get(44L, 7L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authorizer).canAccess(
                WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT,
                20L,
                principal,
                AttachmentOwnerAccessAction.READ);
    }

    private ApplicationPrincipal principal(Long userId, String username, String role) {
        return new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public Set<String> getRoles() {
                return Set.of(role);
            }
        };
    }
}
