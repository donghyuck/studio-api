package studio.one.application.attachment.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.sql.Blob;
import java.util.Optional;

import javax.sql.rowset.serial.SerialBlob;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import studio.one.application.attachment.domain.model.ApplicationAttachment;
import studio.one.application.attachment.domain.model.ApplicationAttachmentData;
import studio.one.application.attachment.infrastructure.persistence.jpa.AttachmentDataJpaRepository;

@ExtendWith(MockitoExtension.class)
class JpaFileStoreTest {

    @Mock
    private AttachmentDataJpaRepository attachmentDataRepository;

    @Test
    void savePersistsBlobPayload() throws Exception {
        JpaFileStore store = new JpaFileStore(attachmentDataRepository);
        ApplicationAttachment attachment = attachment(91L);

        store.save(attachment, new ByteArrayInputStream(new byte[] { 1, 2, 3 }));

        ArgumentCaptor<ApplicationAttachmentData> captor = ArgumentCaptor.forClass(ApplicationAttachmentData.class);
        verify(attachmentDataRepository).save(captor.capture());
        ApplicationAttachmentData saved = captor.getValue();
        try (var in = saved.getBlob().getBinaryStream()) {
            assertArrayEquals(new byte[] { 1, 2, 3 }, in.readAllBytes());
        }
    }

    @Test
    void loadReadsStoredBlob() throws Exception {
        JpaFileStore store = new JpaFileStore(attachmentDataRepository);
        ApplicationAttachment attachment = attachment(91L);
        Blob blob = org.mockito.Mockito.mock(Blob.class);

        when(blob.length()).thenReturn(3L);
        when(blob.getBytes(1, 3)).thenReturn(new byte[] { 4, 5, 6 });

        when(attachmentDataRepository.findById(91L))
                .thenReturn(Optional.of(new ApplicationAttachmentData(91L, blob)));

        try (var in = store.load(attachment)) {
            assertArrayEquals(new byte[] { 4, 5, 6 }, in.readAllBytes());
        }
        verify(blob, never()).getBinaryStream();
    }

    private ApplicationAttachment attachment(long attachmentId) {
        ApplicationAttachment attachment = new ApplicationAttachment();
        attachment.setAttachmentId(attachmentId);
        return attachment;
    }
}
