package studio.one.application.attachment.service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.exception.AttachmentDownloadTokenInvalidException;
import studio.one.application.attachment.exception.AttachmentDownloadUrlUnavailableException;
import studio.one.application.attachment.persistence.AttachmentDownloadUrlIssueAuditLogRepository;

@Transactional
public class AttachmentDownloadUrlServiceImpl implements AttachmentDownloadUrlService {

    private static final long DEFAULT_TTL_SECONDS = 300L;
    private static final long MIN_TTL_SECONDS = 1L;
    private static final long MAX_TTL_SECONDS = 3600L;
    private static final String LINK_TYPE_APPLICATION_SIGNED = "APPLICATION_SIGNED";

    private final String publicBaseUrl;
    private final String signingSecret;
    private final String downloadPath;
    private final AttachmentDownloadUrlIssueAuditLogRepository auditLogRepository;
    private final Clock clock;

    public AttachmentDownloadUrlServiceImpl(
            String publicBaseUrl,
            String signingSecret,
            String attachmentBasePath,
            AttachmentDownloadUrlIssueAuditLogRepository auditLogRepository) {
        this(publicBaseUrl, signingSecret, attachmentBasePath, auditLogRepository, Clock.systemUTC());
    }

    AttachmentDownloadUrlServiceImpl(
            String publicBaseUrl,
            String signingSecret,
            String attachmentBasePath,
            AttachmentDownloadUrlIssueAuditLogRepository auditLogRepository,
            Clock clock) {
        this.publicBaseUrl = publicBaseUrl;
        this.signingSecret = signingSecret;
        this.downloadPath = normalizeDownloadPath(attachmentBasePath);
        this.auditLogRepository = auditLogRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public AttachmentDownloadUrl issueDownloadUrl(
            Attachment attachment,
            Long ttlSeconds,
            AttachmentDownloadUrlEndpointKind endpointKind,
            AttachmentDownloadUrlIssueActor actor,
            String clientIp,
            String userAgent) {
        long resolvedTtl = resolveTtl(ttlSeconds);
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(resolvedTtl);
        AttachmentDownloadTokenCodec codec = issueTokenCodec();
        String token = codec.issue(attachment.getAttachmentId(), issuedAt, expiresAt);
        String url = signedDownloadUrl(token);

        auditLogRepository.save(toAuditLog(
                attachment,
                endpointKind,
                actor,
                clientIp,
                userAgent,
                issuedAt,
                expiresAt,
                resolvedTtl,
                token,
                codec));
        return new AttachmentDownloadUrl(url, expiresAt);
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentDownloadTokenClaims verifyDownloadToken(String token) {
        AttachmentDownloadTokenInspection inspection = inspectDownloadToken(token);
        if (inspection.status() != AttachmentDownloadTokenInspectionStatus.VALID) {
            throw new AttachmentDownloadTokenInvalidException();
        }
        return inspection.claims();
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentDownloadTokenInspection inspectDownloadToken(String token) {
        String tokenHash = StringUtils.hasText(token) ? AttachmentDownloadTokenCodec.sha256HexValue(token) : null;
        try {
            return new AttachmentDownloadTokenCodec(signingSecret, clock).inspect(token);
        } catch (IllegalStateException ex) {
            return AttachmentDownloadTokenInspection.invalid(tokenHash);
        }
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
            String token,
            AttachmentDownloadTokenCodec codec) {
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
        log.setLinkType(LINK_TYPE_APPLICATION_SIGNED);
        log.setTokenHash(codec.sha256Hex(token));
        log.setClientIp(clientIp);
        log.setUserAgent(userAgent);
        return log;
    }

    private AttachmentDownloadTokenCodec issueTokenCodec() {
        try {
            return new AttachmentDownloadTokenCodec(signingSecret, clock);
        } catch (IllegalStateException ex) {
            throw new AttachmentDownloadUrlUnavailableException();
        }
    }

    private String signedDownloadUrl(String token) {
        return UriComponentsBuilder.fromUriString(normalizePublicBaseUrl(publicBaseUrl))
                .path(downloadPath)
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private static String normalizeDownloadPath(String attachmentBasePath) {
        String basePath = StringUtils.hasText(attachmentBasePath) ? attachmentBasePath.trim() : "/api/attachments";
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        while (basePath.endsWith("/") && basePath.length() > 1) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        return basePath + "/signed-download";
    }

    private static String normalizePublicBaseUrl(String publicBaseUrl) {
        if (!StringUtils.hasText(publicBaseUrl)) {
            throw new AttachmentDownloadUrlUnavailableException();
        }
        String normalized = trimTrailingSlash(publicBaseUrl.trim());
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            throw new AttachmentDownloadUrlUnavailableException();
        }
        if (!uri.isAbsolute()
                || !StringUtils.hasText(uri.getScheme())
                || !StringUtils.hasText(uri.getHost())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new AttachmentDownloadUrlUnavailableException();
        }
        return normalized;
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
