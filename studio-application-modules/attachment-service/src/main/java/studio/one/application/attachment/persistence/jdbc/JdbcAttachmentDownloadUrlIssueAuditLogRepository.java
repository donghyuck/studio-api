package studio.one.application.attachment.persistence.jdbc;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.persistence.AttachmentDownloadUrlIssueAuditLogRepository;

@Repository
@RequiredArgsConstructor
public class JdbcAttachmentDownloadUrlIssueAuditLogRepository implements AttachmentDownloadUrlIssueAuditLogRepository {

    private static final String TABLE = "TB_APPLICATION_ATTACHMENT_URL_ISSUE_LOG";

    private final NamedParameterJdbcTemplate template;

    @Override
    public AttachmentDownloadUrlIssueAuditLog save(AttachmentDownloadUrlIssueAuditLog log) {
        String sql = """
                insert into %s (
                    ATTACHMENT_ID, OBJECT_TYPE, OBJECT_ID, ENDPOINT_KIND,
                    ISSUED_BY_USER_ID, ISSUED_BY_PRINCIPAL_NAME,
                    ISSUED_AT, EXPIRES_AT, TTL_SECONDS,
                    STORAGE_PROVIDER_ID, BUCKET, OBJECT_KEY_HASH,
                    CLIENT_IP, USER_AGENT
                ) values (
                    :attachmentId, :objectType, :objectId, :endpointKind,
                    :issuedByUserId, :issuedByPrincipalName,
                    :issuedAt, :expiresAt, :ttlSeconds,
                    :storageProviderId, :bucket, :objectKeyHash,
                    :clientIp, :userAgent
                )
                """.formatted(TABLE);
        template.update(sql, params(log));
        return log;
    }

    private Map<String, Object> params(AttachmentDownloadUrlIssueAuditLog log) {
        Map<String, Object> params = new HashMap<>();
        params.put("attachmentId", log.getAttachmentId());
        params.put("objectType", log.getObjectType());
        params.put("objectId", log.getObjectId());
        params.put("endpointKind", log.getEndpointKind());
        params.put("issuedByUserId", log.getIssuedByUserId());
        params.put("issuedByPrincipalName", log.getIssuedByPrincipalName());
        params.put("issuedAt", Timestamp.from(log.getIssuedAt()));
        params.put("expiresAt", Timestamp.from(log.getExpiresAt()));
        params.put("ttlSeconds", log.getTtlSeconds());
        params.put("storageProviderId", log.getStorageProviderId());
        params.put("bucket", log.getBucket());
        params.put("objectKeyHash", log.getObjectKeyHash());
        params.put("clientIp", log.getClientIp());
        params.put("userAgent", log.getUserAgent());
        return params;
    }
}
