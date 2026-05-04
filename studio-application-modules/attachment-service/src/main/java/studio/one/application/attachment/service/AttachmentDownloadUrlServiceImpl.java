package studio.one.application.attachment.service;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.exception.AttachmentDownloadUrlUnavailableException;
import studio.one.application.attachment.persistence.AttachmentDownloadUrlIssueAuditLogRepository;
import studio.one.application.attachment.storage.AttachmentContentDisposition;
import studio.one.application.attachment.storage.AttachmentFileStorageResolver;
import studio.one.application.attachment.storage.AttachmentStorageMetadata;
import studio.one.application.attachment.storage.AttachmentStorageType;
import studio.one.application.attachment.storage.FileStorage;
import studio.one.application.attachment.storage.SignedDownloadUrlFileStorage;
import studio.one.platform.storage.exception.ObjectStorageNotFoundException;

@RequiredArgsConstructor
@Transactional
public class AttachmentDownloadUrlServiceImpl implements AttachmentDownloadUrlService {

    private static final long DEFAULT_TTL_SECONDS = 300L;
    private static final long MIN_TTL_SECONDS = 1L;
    private static final long MAX_TTL_SECONDS = 3600L;

    private final AttachmentFileStorageResolver storageResolver;
    private final AttachmentDownloadUrlIssueAuditLogRepository auditLogRepository;

    @Override
    public AttachmentDownloadUrl issueDownloadUrl(
            Attachment attachment,
            Long ttlSeconds,
            AttachmentDownloadUrlEndpointKind endpointKind,
            AttachmentDownloadUrlIssueActor actor,
            String clientIp,
            String userAgent) {
        long resolvedTtl = resolveTtl(ttlSeconds);
        AttachmentStorageMetadata.ObjectStorageLocation location =
                AttachmentStorageMetadata.explicitObjectStorageLocation(attachment)
                        .orElseThrow(AttachmentDownloadUrlUnavailableException::new);
        FileStorage fileStorage = storageResolver.resolve(AttachmentStorageType.objectstorage)
                .orElseThrow(AttachmentDownloadUrlUnavailableException::new);
        if (!(fileStorage instanceof SignedDownloadUrlFileStorage signedStorage)) {
            throw new AttachmentDownloadUrlUnavailableException();
        }

        Instant issuedAt = Instant.now();
        Duration ttl = Duration.ofSeconds(resolvedTtl);
        Instant expiresAt = issuedAt.plus(ttl);
        String contentDisposition = AttachmentContentDisposition.attachment(attachment);
        URL url;
        try {
            url = signedStorage.createSignedDownloadUrl(attachment, ttl, contentDisposition);
        } catch (ObjectStorageNotFoundException | UnsupportedOperationException ex) {
            throw new AttachmentDownloadUrlUnavailableException();
        }
        if (url == null) {
            throw new AttachmentDownloadUrlUnavailableException();
        }

        auditLogRepository.save(toAuditLog(
                attachment,
                endpointKind,
                actor,
                clientIp,
                userAgent,
                issuedAt,
                expiresAt,
                resolvedTtl,
                location));
        return new AttachmentDownloadUrl(url.toString(), expiresAt);
    }

    private long resolveTtl(Long ttlSeconds) {
        long resolved = ttlSeconds == null ? DEFAULT_TTL_SECONDS : ttlSeconds;
        if (resolved < MIN_TTL_SECONDS || resolved > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("ttlSeconds must be between 1 and 3600");
        }
        return resolved;
    }

    private AttachmentDownloadUrlIssueAuditLog toAuditLog(
            Attachment attachment,
            AttachmentDownloadUrlEndpointKind endpointKind,
            AttachmentDownloadUrlIssueActor actor,
            String clientIp,
            String userAgent,
            Instant issuedAt,
            Instant expiresAt,
            long ttlSeconds,
            AttachmentStorageMetadata.ObjectStorageLocation location) {
        AttachmentDownloadUrlIssueAuditLog log = new AttachmentDownloadUrlIssueAuditLog();
        log.setAttachmentId(attachment.getAttachmentId());
        log.setObjectType(attachment.getObjectType());
        log.setObjectId(attachment.getObjectId());
        log.setEndpointKind(endpointKind.name());
        if (actor != null) {
            log.setIssuedByUserId(actor.userId());
            log.setIssuedByPrincipalName(actor.principalName());
        }
        log.setIssuedAt(issuedAt);
        log.setExpiresAt(expiresAt);
        log.setTtlSeconds(ttlSeconds);
        log.setStorageProviderId(location.providerId());
        log.setBucket(location.bucket());
        log.setObjectKeyHash(objectKeyHash(location));
        log.setClientIp(clientIp);
        log.setUserAgent(userAgent);
        return log;
    }

    private static String objectKeyHash(AttachmentStorageMetadata.ObjectStorageLocation location) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((location.providerId() + ":" + location.bucket() + ":" + location.key())
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
