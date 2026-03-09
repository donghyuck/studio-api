package studio.one.application.web.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.text.service.FileContentExtractionService;

@ExtendWith(MockitoExtension.class)
class AttachmentMgmtControllerAuthorizationTest {

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private ObjectProvider<IdentityService> identityServiceProvider;

    @Mock
    private ObjectProvider<PrincipalResolver> principalResolverProvider;

    @Mock
    private ObjectProvider<FileContentExtractionService> textExtractionProvider;

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

    private AttachmentMgmtController controller() {
        return new AttachmentMgmtController(
                attachmentService,
                identityServiceProvider,
                principalResolverProvider,
                textExtractionProvider);
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
