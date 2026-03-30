package studio.one.application.attachment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import studio.one.application.attachment.domain.entity.ApplicationAttachment;
import studio.one.application.attachment.storage.FileStorage;
import studio.one.application.attachment.thumbnail.ThumbnailService;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.objecttype.service.ObjectTypeRuntimeService;
import studio.one.platform.objecttype.service.ValidateUploadCommand;
import studio.one.platform.objecttype.service.ValidateUploadResult;
import studio.one.platform.exception.PlatformRuntimeException;
import studio.one.platform.objecttype.error.ObjectTypeErrorCodes;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @Mock
    private studio.one.application.attachment.persistence.AttachmentRepository attachmentRepository;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private ObjectProvider<PrincipalResolver> principalResolverProvider;

    @Mock
    private ObjectProvider<ObjectTypeRuntimeService> objectTypeRuntimeServiceProvider;

    @Mock
    private ObjectProvider<ThumbnailService> thumbnailServiceProvider;

    @Mock
    private PrincipalResolver principalResolver;

    @Mock
    private ObjectTypeRuntimeService objectTypeRuntimeService;

    @Test
    void createAttachmentValidatesUploadPolicyAndPersistsCreator() {
        AttachmentServiceImpl service = service();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 });

        when(objectTypeRuntimeServiceProvider.getIfAvailable()).thenReturn(objectTypeRuntimeService);
        when(objectTypeRuntimeService.validateUpload(eq(1200), any(ValidateUploadCommand.class)))
                .thenReturn(new ValidateUploadResult(true, null));
        when(principalResolverProvider.getIfAvailable()).thenReturn(principalResolver);
        when(principalResolver.currentOrNull()).thenReturn(principal(7L, "USER"));
        when(attachmentRepository.save(any(ApplicationAttachment.class))).thenAnswer(invocation -> {
            ApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setAttachmentId(99L);
            attachment.setCreatedAt(Instant.parse("2026-03-30T00:00:00Z"));
            return attachment;
        });

        ApplicationAttachment created = (ApplicationAttachment) service.createAttachment(
                1200,
                3400L,
                "contract.pdf",
                "application/pdf",
                inputStream,
                4);

        ArgumentCaptor<ValidateUploadCommand> requestCaptor = ArgumentCaptor.forClass(ValidateUploadCommand.class);
        verify(objectTypeRuntimeService).validateUpload(eq(1200), requestCaptor.capture());
        ValidateUploadCommand request = requestCaptor.getValue();
        assertEquals("contract.pdf", request.fileName());
        assertEquals("application/pdf", request.contentType());
        assertEquals(4L, request.sizeBytes());

        ArgumentCaptor<ApplicationAttachment> attachmentCaptor = ArgumentCaptor.forClass(ApplicationAttachment.class);
        verify(attachmentRepository).save(attachmentCaptor.capture());
        ApplicationAttachment saved = attachmentCaptor.getValue();
        assertEquals(1200, saved.getObjectType());
        assertEquals(3400L, saved.getObjectId());
        assertEquals("contract.pdf", saved.getName());
        assertEquals("application/pdf", saved.getContentType());
        assertEquals(4L, saved.getSize());
        assertEquals(7L, saved.getCreatedBy());

        verify(fileStorage).save(created, inputStream);
        assertEquals(99L, created.getAttachmentId());
    }

    @Test
    void createAttachmentSkipsValidationWhenObjectTypeServiceAbsent() {
        AttachmentServiceImpl service = service();
        InputStream inputStream = new ByteArrayInputStream(new byte[0]);

        when(objectTypeRuntimeServiceProvider.getIfAvailable()).thenReturn(null);
        when(attachmentRepository.save(any(ApplicationAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createAttachment(1200, 3400L, "file.pdf", "application/pdf", inputStream, 0);

        verify(attachmentRepository).save(any(ApplicationAttachment.class));
        verify(fileStorage).save(any(ApplicationAttachment.class), eq(inputStream));
    }

    @Test
    void createAttachmentPropagatesObjectTypeValidationFailureBeforeSaving() {
        AttachmentServiceImpl service = service();
        InputStream inputStream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
        PlatformRuntimeException validationFailure = PlatformRuntimeException.of(
                ObjectTypeErrorCodes.POLICY_VIOLATION,
                "allowedExt");

        when(objectTypeRuntimeServiceProvider.getIfAvailable()).thenReturn(objectTypeRuntimeService);
        doThrow(validationFailure).when(objectTypeRuntimeService)
                .validateUpload(eq(1200), any(ValidateUploadCommand.class));

        PlatformRuntimeException thrown = assertThrows(PlatformRuntimeException.class, () -> service.createAttachment(
                1200,
                3400L,
                "blocked.pdf",
                "application/pdf",
                inputStream,
                3));

        assertSame(validationFailure, thrown);
        verify(attachmentRepository, never()).save(any(ApplicationAttachment.class));
        verify(fileStorage, never()).save(any(ApplicationAttachment.class), any(InputStream.class));
    }

    /**
     * Current implementation does not compensate for a previously saved DB row when file storage fails.
     * Update this test together with the production code once a compensation strategy is introduced.
     * Related: #162
     */
    @Test
    void createAttachmentPropagatesStorageFailureWithoutCompensation() {
        AttachmentServiceImpl service = service();
        RuntimeException storageFailure = new RuntimeException("storage failure");
        InputStream inputStream = new ByteArrayInputStream(new byte[] { 9, 8, 7 });

        when(attachmentRepository.save(any(ApplicationAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorage.save(any(ApplicationAttachment.class), any(InputStream.class))).thenThrow(storageFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createAttachment(
                2000,
                1L,
                "error.txt",
                "text/plain",
                inputStream,
                3));

        assertSame(storageFailure, thrown);
        InOrder inOrder = inOrder(attachmentRepository, fileStorage);
        inOrder.verify(attachmentRepository).save(any(ApplicationAttachment.class));
        inOrder.verify(fileStorage).save(any(ApplicationAttachment.class), eq(inputStream));
        verify(attachmentRepository, never()).delete(any(ApplicationAttachment.class));
    }

    private AttachmentServiceImpl service() {
        return new AttachmentServiceImpl(
                attachmentRepository,
                fileStorage,
                principalResolverProvider,
                objectTypeRuntimeServiceProvider,
                thumbnailServiceProvider);
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
