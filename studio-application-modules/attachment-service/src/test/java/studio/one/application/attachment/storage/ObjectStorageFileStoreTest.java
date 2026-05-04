package studio.one.application.attachment.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.storage.service.CloudObjectStorage;
import studio.one.platform.storage.service.ObjectStorageRegistry;

class ObjectStorageFileStoreTest {

    @Test
    void saveWritesUuidKeyAndReturnsStorageMetadata() {
        CloudObjectStorage objectStorage = mock(CloudObjectStorage.class);
        when(objectStorage.name()).thenReturn("main");
        ObjectStorageFileStore store = new ObjectStorageFileStore(
                new ObjectStorageRegistry(List.of(objectStorage)),
                "main",
                "bucket",
                "/attachments/");
        Attachment attachment = mock(Attachment.class);
        when(attachment.getSize()).thenReturn(3L);
        when(attachment.getContentType()).thenReturn("text/plain");

        FileStorageSaveResult result = store.saveWithResult(
                attachment,
                new ByteArrayInputStream(new byte[] { 1, 2, 3 }));

        verify(objectStorage).put(
                eq("bucket"),
                matches("attachments/[0-9a-fA-F\\-]{36}"),
                any(),
                eq(3L),
                eq("text/plain"),
                eq(Map.of()));
        assertEquals("objectstorage", result.properties().get(AttachmentStorageMetadata.STORAGE_TYPE));
        assertEquals("main", result.properties().get(AttachmentStorageMetadata.STORAGE_PROVIDER));
        assertEquals("bucket", result.properties().get(AttachmentStorageMetadata.STORAGE_BUCKET));
        assertTrue(result.properties().get(AttachmentStorageMetadata.STORAGE_KEY).startsWith("attachments/"));
    }
}
