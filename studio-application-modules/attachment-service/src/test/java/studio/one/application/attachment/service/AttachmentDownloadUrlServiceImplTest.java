package studio.one.application.attachment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.exception.AttachmentDownloadUrlUnavailableException;
import studio.one.application.attachment.persistence.AttachmentDownloadUrlIssueAuditLogRepository;
import studio.one.application.attachment.storage.AttachmentFileStorageResolver;
import studio.one.application.attachment.storage.AttachmentStorageMetadata;
import studio.one.application.attachment.storage.AttachmentStorageType;
import studio.one.application.attachment.storage.FileStorage;
import studio.one.application.attachment.storage.SignedDownloadUrlFileStorage;

class AttachmentDownloadUrlServiceImplTest {

    @Test
    void issueDownloadUrlPersistsAuditWithoutRawKeyOrUrl() throws Exception {
        AttachmentFileStorageResolver resolver = mock(AttachmentFileStorageResolver.class);
        AttachmentDownloadUrlIssueAuditLogRepository auditRepository =
                mock(AttachmentDownloadUrlIssueAuditLogRepository.class);
        SignedFileStorage storage = new SignedFileStorage("https://signed.example/download");
        Attachment attachment = attachment();

        when(resolver.resolve(AttachmentStorageType.objectstorage)).thenReturn(Optional.of(storage));

        AttachmentDownloadUrlServiceImpl service = new AttachmentDownloadUrlServiceImpl(resolver, auditRepository);
        AttachmentDownloadUrl result = service.issueDownloadUrl(
                attachment,
                null,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                new AttachmentDownloadUrlIssueActor(7L, "user-7"),
                "127.0.0.1",
                "JUnit");

        assertEquals("https://signed.example/download", result.url());
        ArgumentCaptor<AttachmentDownloadUrlIssueAuditLog> captor =
                ArgumentCaptor.forClass(AttachmentDownloadUrlIssueAuditLog.class);
        verify(auditRepository).save(captor.capture());
        AttachmentDownloadUrlIssueAuditLog log = captor.getValue();
        assertEquals("SERVICE", log.getEndpointKind());
        assertEquals(300L, log.getTtlSeconds());
        assertEquals("main", log.getStorageProviderId());
        assertEquals("bucket", log.getBucket());
        assertEquals(sha256("main:bucket:secret-key"), log.getObjectKeyHash());
        assertEquals("127.0.0.1", log.getClientIp());
        assertEquals("JUnit", log.getUserAgent());
        assertEquals("attachment; filename=\"__.pdf\"; filename*=UTF-8''%ED%95%9C%EA%B8%80.pdf",
                storage.contentDisposition);
    }

    @Test
    void invalidTtlIsRejectedBeforeAudit() {
        AttachmentDownloadUrlIssueAuditLogRepository auditRepository =
                mock(AttachmentDownloadUrlIssueAuditLogRepository.class);
        AttachmentDownloadUrlServiceImpl service = new AttachmentDownloadUrlServiceImpl(
                mock(AttachmentFileStorageResolver.class),
                auditRepository);

        assertThrows(IllegalArgumentException.class, () -> service.issueDownloadUrl(
                attachment(),
                0L,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                null,
                null,
                null));

        verify(auditRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingExplicitObjectStorageMetadataReturnsUnavailable() {
        Attachment attachment = mock(Attachment.class);
        when(attachment.getProperties()).thenReturn(Map.of());
        AttachmentDownloadUrlIssueAuditLogRepository auditRepository =
                mock(AttachmentDownloadUrlIssueAuditLogRepository.class);
        AttachmentDownloadUrlServiceImpl service = new AttachmentDownloadUrlServiceImpl(
                mock(AttachmentFileStorageResolver.class),
                auditRepository);

        assertThrows(AttachmentDownloadUrlUnavailableException.class, () -> service.issueDownloadUrl(
                attachment,
                null,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                null,
                null,
                null));

        verify(auditRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Attachment attachment() {
        Attachment attachment = mock(Attachment.class);
        when(attachment.getAttachmentId()).thenReturn(11L);
        when(attachment.getObjectType()).thenReturn(12);
        when(attachment.getObjectId()).thenReturn(34L);
        when(attachment.getName()).thenReturn("한글.pdf");
        when(attachment.getContentType()).thenReturn("application/pdf");
        when(attachment.getProperties()).thenReturn(Map.of(
                AttachmentStorageMetadata.STORAGE_TYPE, "objectstorage",
                AttachmentStorageMetadata.STORAGE_PROVIDER, "main",
                AttachmentStorageMetadata.STORAGE_BUCKET, "bucket",
                AttachmentStorageMetadata.STORAGE_KEY, "secret-key"));
        return attachment;
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class SignedFileStorage implements FileStorage, SignedDownloadUrlFileStorage {

        private final URL url;
        private String contentDisposition;

        private SignedFileStorage(String url) throws MalformedURLException {
            this.url = new URL(url);
        }

        @Override
        public String save(Attachment attachment, java.io.InputStream input) {
            return null;
        }

        @Override
        public java.io.InputStream load(Attachment attachment) {
            return null;
        }

        @Override
        public void delete(Attachment attachment) {
        }

        @Override
        public URL createSignedDownloadUrl(
                Attachment attachment,
                java.time.Duration ttl,
                String contentDisposition) {
            this.contentDisposition = contentDisposition;
            return url;
        }
    }
}
