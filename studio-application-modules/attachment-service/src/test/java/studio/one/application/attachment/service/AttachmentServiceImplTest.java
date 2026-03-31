package studio.one.application.attachment.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import studio.one.application.attachment.domain.entity.ApplicationAttachment;
import studio.one.application.attachment.persistence.AttachmentRepository;
import studio.one.application.attachment.storage.FileStorage;
import studio.one.application.attachment.thumbnail.ThumbnailService;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private ObjectProvider<PrincipalResolver> principalResolverProvider;

    @Mock
    private ObjectProvider objectTypeRuntimeServiceProvider;

    @Mock
    private ObjectProvider<ThumbnailService> thumbnailServiceProvider;

    @Test
    void createAttachmentReadsFullStreamAndUsesExactSize() throws Exception {
        AttachmentServiceImpl service = service();
        byte[] payload = new byte[] { 1, 2, 3, 4 };
        InputStream input = new NoAvailableInputStream(payload);

        when(attachmentRepository.save(any())).thenAnswer(invocation -> {
            ApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setAttachmentId(99L);
            return attachment;
        });
        when(fileStorage.save(any(), any(InputStream.class))).thenAnswer(invocation -> {
            InputStream stored = invocation.getArgument(1);
            assertArrayEquals(payload, stored.readAllBytes());
            return "stored";
        });

        ApplicationAttachment result = (ApplicationAttachment) service.createAttachment(
                12,
                34L,
                "contract.pdf",
                "application/pdf",
                input);

        assertEquals(99L, result.getAttachmentId());
        assertEquals(4L, result.getSize());
    }

    @Test
    void createAttachmentDeletesSavedRecordWhenStorageSaveFails() {
        AttachmentServiceImpl service = service();
        byte[] payload = new byte[] { 9, 8, 7 };
        RuntimeException storageFailure = new RuntimeException("storage failed");

        when(attachmentRepository.save(any())).thenAnswer(invocation -> {
            ApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setAttachmentId(77L);
            return attachment;
        });
        when(fileStorage.save(any(), any(InputStream.class))).thenThrow(storageFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createAttachment(
                12,
                34L,
                "report.pdf",
                "application/pdf",
                new ByteArrayInputStream(payload)));

        assertSame(storageFailure, thrown);
        InOrder order = inOrder(attachmentRepository, fileStorage);
        order.verify(attachmentRepository).save(any(ApplicationAttachment.class));
        order.verify(fileStorage).save(any(ApplicationAttachment.class), any(InputStream.class));
        order.verify(fileStorage).delete(any(ApplicationAttachment.class));
        order.verify(attachmentRepository).delete(any(ApplicationAttachment.class));
    }

    @Test
    void createAttachmentStillDeletesMetadataWhenStorageCleanupFails() {
        AttachmentServiceImpl service = service();
        RuntimeException storageFailure = new RuntimeException("storage failed");

        when(attachmentRepository.save(any())).thenAnswer(invocation -> {
            ApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setAttachmentId(77L);
            return attachment;
        });
        doThrow(storageFailure).when(fileStorage).save(any(), any(InputStream.class));
        doThrow(new RuntimeException("cleanup failed")).when(fileStorage).delete(any(ApplicationAttachment.class));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createAttachment(
                12,
                34L,
                "report.pdf",
                "application/pdf",
                new ByteArrayInputStream(new byte[] { 9, 8, 7 })));

        assertSame(storageFailure, thrown);
        verify(attachmentRepository).delete(any(ApplicationAttachment.class));
    }

    @Test
    void createAttachmentStillAttemptsMetadataCleanupWhenDeleteUsesSavedEntity() {
        AttachmentServiceImpl service = service();
        RuntimeException storageFailure = new RuntimeException("storage failed");

        when(attachmentRepository.save(any())).thenAnswer(invocation -> {
            ApplicationAttachment attachment = invocation.getArgument(0);
            attachment.setAttachmentId(77L);
            return attachment;
        });
        doThrow(storageFailure).when(fileStorage).save(any(), any(InputStream.class));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createAttachment(
                12,
                34L,
                "report.pdf",
                "application/pdf",
                new ByteArrayInputStream(new byte[] { 9, 8, 7 })));

        assertSame(storageFailure, thrown);
        verify(fileStorage).delete(any(ApplicationAttachment.class));
        verify(attachmentRepository).delete(any(ApplicationAttachment.class));
    }

    private AttachmentServiceImpl service() {
        return new AttachmentServiceImpl(
                attachmentRepository,
                fileStorage,
                principalResolverProvider,
                objectTypeRuntimeServiceProvider,
                thumbnailServiceProvider);
    }

    private static final class NoAvailableInputStream extends ByteArrayInputStream {
        private NoAvailableInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public synchronized int available() {
            throw new AssertionError("available() must not be called");
        }
    }
}
