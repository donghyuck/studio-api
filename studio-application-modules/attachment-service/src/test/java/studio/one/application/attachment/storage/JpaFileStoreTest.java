package studio.one.application.attachment.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

import studio.one.application.attachment.domain.entity.ApplicationAttachment;
import studio.one.application.attachment.domain.entity.ApplicationAttachmentData;
import studio.one.application.attachment.persistence.jpa.AttachmentDataJpaRepository;

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
        Blob blob = new SerialBlob(new byte[] { 4, 5, 6 });

        when(attachmentDataRepository.findById(91L))
                .thenReturn(Optional.of(new ApplicationAttachmentData(91L, blob)));

        try (var in = store.load(attachment)) {
            assertArrayEquals(new byte[] { 4, 5, 6 }, in.readAllBytes());
        }
    }

    private ApplicationAttachment attachment(long attachmentId) {
        ApplicationAttachment attachment = new ApplicationAttachment();
        attachment.setAttachmentId(attachmentId);
        return attachment;
    }
}
