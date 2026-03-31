package studio.one.application.attachment.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import studio.one.application.attachment.domain.entity.ApplicationAttachment;

class LocalFileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveLoadAndDeleteRoundTrip() throws Exception {
        LocalFileStore store = new LocalFileStore(tempDir.toString());
        ApplicationAttachment attachment = attachment(12, 91L);

        store.save(attachment, new ByteArrayInputStream(new byte[] { 1, 2, 3 }));

        try (var in = store.load(attachment)) {
            assertArrayEquals(new byte[] { 1, 2, 3 }, in.readAllBytes());
        }

        store.delete(attachment);

        assertThrows(RuntimeException.class, () -> store.load(attachment));
        assertFalse(Files.exists(tempDir.resolve("12").resolve("91")));
    }

    private ApplicationAttachment attachment(int objectType, long attachmentId) {
        ApplicationAttachment attachment = new ApplicationAttachment();
        attachment.setObjectType(objectType);
        attachment.setAttachmentId(attachmentId);
        return attachment;
    }
}
