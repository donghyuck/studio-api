package studio.one.application.attachment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import studio.one.application.attachment.domain.entity.ApplicationAttachment;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.persistence.AttachmentRepository;
import studio.one.application.attachment.storage.AttachmentFileStorageResolver;
import studio.one.application.attachment.storage.AttachmentStorageMetadata;
import studio.one.application.attachment.storage.FileStorage;
import studio.one.application.attachment.storage.FileStorageSaveResult;
import studio.one.application.attachment.storage.FileStorageSaveResultCapable;
import studio.one.application.attachment.thumbnail.ThumbnailService;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.objecttype.application.usecase.ObjectTypeKeyRuntimeService;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private ObjectProvider<AttachmentFileStorageResolver> fileStorageResolverProvider;

    @Mock
    private ObjectProvider<PrincipalResolver> principalResolverProvider;

    @Mock
    private ObjectProvider<ObjectTypeRuntimeService> objectTypeRuntimeServiceProvider;

    @Mock
    private ObjectProvider<AttachmentObjectTypeResolver> objectTypeResolverProvider;

    @Mock
    private ObjectProvider<ThumbnailService> thumbnailServiceProvider;

    @Test
    void createAttachmentBuffersStreamInsteadOfUsingAvailable() {
        AttachmentServiceImpl service = service();
        AtomicInteger savedSize = new AtomicInteger();
        AtomicInteger storedBytes = new AtomicInteger();

        when(attachmentRepository.save(any(ApplicationAttachment.class))).thenAnswer(invocation -> {
            ApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setAttachmentId(91L);
            savedSize.set((int) attachment.getSize());
            return attachment;
        });
        when(fileStorage.save(any(Attachment.class), any(InputStream.class))).thenAnswer(invocation -> {
            try (InputStream in = invocation.getArgument(1)) {
                storedBytes.set(in.readAllBytes().length);
            }
            return "91";
        });

        Attachment saved = service.createAttachment(
                12,
                34L,
                "report.pdf",
                "application/pdf",
                new ZeroAvailableInputStream(new byte[] { 1, 2, 3, 4 }));

        assertEquals(4, savedSize.get());
        assertEquals(4, storedBytes.get());
        assertEquals(4, saved.getSize());
    }

    @Test
    void createAttachmentCleansUpPartialBinaryWhenFileSaveFails() {
        AttachmentServiceImpl service = service();
        RuntimeException storageFailure = new RuntimeException("storage failed");

        when(attachmentRepository.save(any(ApplicationAttachment.class))).thenAnswer(invocation -> {
            ApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setAttachmentId(33L);
            return attachment;
        });
        when(fileStorage.save(any(Attachment.class), any(InputStream.class))).thenThrow(storageFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createAttachment(
                12,
                34L,
                "report.pdf",
                "application/pdf",
                new ByteArrayInputStream(new byte[] { 1, 2, 3 }),
                3));

        assertSame(storageFailure, thrown);
        InOrder inOrder = inOrder(attachmentRepository, fileStorage);
        inOrder.verify(attachmentRepository).save(any(ApplicationAttachment.class));
        inOrder.verify(fileStorage).save(any(Attachment.class), any(InputStream.class));
        inOrder.verify(fileStorage).delete(any(Attachment.class));
        verify(attachmentRepository, never()).delete(any(ApplicationAttachment.class));
    }

    @Test
    void createAttachmentDoesNotTouchStorageWhenMetadataSaveFails() {
        AttachmentServiceImpl service = service();
        RuntimeException metadataFailure = new RuntimeException("metadata failed");

        when(attachmentRepository.save(any(ApplicationAttachment.class))).thenThrow(metadataFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createAttachment(
                12,
                34L,
                "report.pdf",
                "application/pdf",
                new ByteArrayInputStream(new byte[] { 1, 2, 3 }),
                3));

        assertSame(metadataFailure, thrown);
        verify(fileStorage, never()).save(any(Attachment.class), any(InputStream.class));
        verify(fileStorage, never()).delete(any(Attachment.class));
    }

    @Test
    void createAttachmentWithExplicitSizePreservesProvidedSize() {
        AttachmentServiceImpl service = service();

        when(attachmentRepository.save(any(ApplicationAttachment.class))).thenAnswer(invocation -> {
            ApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setAttachmentId(44L);
            return attachment;
        });

        Attachment saved = service.createAttachment(
                12,
                34L,
                "report.pdf",
                "application/pdf",
                new ByteArrayInputStream(new byte[] { 1, 2, 3 }),
                3);

        assertEquals(3L, saved.getSize());
        verify(fileStorage).save(any(Attachment.class), any(InputStream.class));
    }

    @Test
    void createAttachmentByObjectTypeKeyResolvesWellKnownType() {
        AttachmentServiceImpl service = service();
        ObjectTypeKeyRuntimeService runtimeService = org.mockito.Mockito.mock(ObjectTypeKeyRuntimeService.class);
        when(objectTypeRuntimeServiceProvider.getIfAvailable()).thenReturn(runtimeService);
        when(runtimeService.objectTypeByKey("workspace-attachment")).thenReturn(2103);
        when(attachmentRepository.save(any(ApplicationAttachment.class))).thenAnswer(invocation -> {
            ApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setAttachmentId(45L);
            return attachment;
        });

        Attachment saved = service.createAttachment(
                "workspace-attachment",
                34L,
                "report.pdf",
                "application/pdf",
                new ByteArrayInputStream(new byte[] { 1, 2, 3 }),
                3);

        assertEquals(2103, saved.getObjectType());
        assertEquals(34L, saved.getObjectId());
        verify(runtimeService).objectTypeByKey(eq("workspace-attachment"));
        verify(runtimeService).validateUpload(eq(2103), any());
    }

    @Test
    void objectTypeResolverRequiresRuntimeService() {
        when(objectTypeRuntimeServiceProvider.getIfAvailable()).thenReturn(null);
        AttachmentObjectTypeResolver resolver = new AttachmentObjectTypeResolver(objectTypeRuntimeServiceProvider);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolveRequired("workspace-attachment"));

        assertTrue(ex.getMessage().contains("ObjectTypeRuntimeService is required"));
    }

    @Test
    void createAttachmentCleansUpObjectWhenMetadataUpdateFailsAfterStorageSave() {
        ResultFileStorage resultStorage = new ResultFileStorage();
        AttachmentServiceImpl service = new AttachmentServiceImpl(
                attachmentRepository,
                resultStorage,
                fileStorageResolverProvider,
                principalResolverProvider,
                objectTypeRuntimeServiceProvider,
                objectTypeResolverProvider,
                thumbnailServiceProvider);
        RuntimeException metadataFailure = new RuntimeException("metadata failed");
        AtomicInteger saves = new AtomicInteger();

        when(attachmentRepository.save(any(ApplicationAttachment.class))).thenAnswer(invocation -> {
            ApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setAttachmentId(55L);
            if (saves.incrementAndGet() == 2) {
                throw metadataFailure;
            }
            return attachment;
        });

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createAttachment(
                12,
                34L,
                "report.pdf",
                "application/pdf",
                new ByteArrayInputStream(new byte[] { 1, 2, 3 }),
                3));

        assertSame(metadataFailure, thrown);
        assertTrue(resultStorage.deleted);
        assertEquals("secret-key", resultStorage.deletedAttachment.getProperties()
                .get(AttachmentStorageMetadata.STORAGE_KEY));
    }

    private AttachmentServiceImpl service() {
        return new AttachmentServiceImpl(
                attachmentRepository,
                fileStorage,
                fileStorageResolverProvider,
                principalResolverProvider,
                objectTypeRuntimeServiceProvider,
                objectTypeResolverProvider,
                thumbnailServiceProvider);
    }

    private static final class ZeroAvailableInputStream extends ByteArrayInputStream {

        private ZeroAvailableInputStream(byte[] data) {
            super(data);
        }

        @Override
        public synchronized int available() {
            return 0;
        }
    }

    private static final class ResultFileStorage implements FileStorage, FileStorageSaveResultCapable {

        private boolean deleted;
        private Attachment deletedAttachment;

        @Override
        public String save(Attachment attachment, InputStream input) {
            return "objectstorage";
        }

        @Override
        public FileStorageSaveResult saveWithResult(Attachment attachment, InputStream input) {
            return new FileStorageSaveResult("objectstorage", Map.of(
                    AttachmentStorageMetadata.STORAGE_TYPE, "objectstorage",
                    AttachmentStorageMetadata.STORAGE_PROVIDER, "main",
                    AttachmentStorageMetadata.STORAGE_BUCKET, "bucket",
                    AttachmentStorageMetadata.STORAGE_KEY, "secret-key"));
        }

        @Override
        public InputStream load(Attachment attachment) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void delete(Attachment attachment) {
            this.deleted = true;
            this.deletedAttachment = attachment;
        }
    }
}
