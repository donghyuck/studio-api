package studio.one.application.attachment.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.application.attachment.domain.model.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.application.result.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.application.command.AttachmentDownloadUrlIssueAuditLogQuery;

@ExtendWith(MockitoExtension.class)
class JdbcAttachmentDownloadUrlIssueAuditLogRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate template;

    @Test
    void savePersistsApplicationSignedAuditFields() {
        JdbcAttachmentDownloadUrlIssueAuditLogRepository repository =
                new JdbcAttachmentDownloadUrlIssueAuditLogRepository(template);
        AttachmentDownloadUrlIssueAuditLog log = new AttachmentDownloadUrlIssueAuditLog();
        log.setAttachmentId(10L);
        log.setObjectType(20);
        log.setObjectId(30L);
        log.setEndpointKind("SERVICE");
        log.setIssuedAt(Instant.parse("2026-05-05T00:00:00Z"));
        log.setExpiresAt(Instant.parse("2026-05-05T00:05:00Z"));
        log.setTtlSeconds(300L);
        log.setLinkType("APPLICATION_SIGNED");
        log.setTokenHash("token-hash");

        repository.save(log);

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(template).update(anyString(), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue())
                .containsEntry("linkType", "APPLICATION_SIGNED")
                .containsEntry("tokenHash", "token-hash")
                .containsEntry("storageProviderId", null)
                .containsEntry("bucket", null)
                .containsEntry("objectKeyHash", null);
    }

    @Test
    void searchBuildsFiltersAndWhitelistedOrderOnly() {
        JdbcAttachmentDownloadUrlIssueAuditLogRepository repository =
                new JdbcAttachmentDownloadUrlIssueAuditLogRepository(template);
        when(template.queryForObject(anyString(), anyParams(), eq(Long.class))).thenReturn(1L);
        when(template.query(anyString(), anyParams(), anyRowMapper())).thenReturn(List.of());
        var query = new AttachmentDownloadUrlIssueAuditLogQuery(
                10L,
                20,
                30L,
                AttachmentDownloadUrlEndpointKind.SERVICE,
                "Admin",
                Instant.parse("2026-05-05T00:00:00Z"),
                Instant.parse("2026-05-06T00:00:00Z"));
        var pageable = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Order.asc("objectKey"), Sort.Order.desc("issuedAt")));

        repository.search(query, pageable);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(template).query(sqlCaptor.capture(), anyParams(), anyRowMapper());
        String sql = sqlCaptor.getValue();
        assertThat(sql)
                .contains("ATTACHMENT_ID = :attachmentId")
                .contains("OBJECT_TYPE = :objectType")
                .contains("OBJECT_ID = :objectId")
                .contains("ENDPOINT_KIND = :endpointKind")
                .contains("lower(ISSUED_BY_PRINCIPAL_NAME) like :issuedByPrincipalName")
                .contains("ISSUED_AT >= :from")
                .contains("ISSUED_AT < :to")
                .contains("order by ISSUED_AT desc")
                .doesNotContain("objectKey");
    }

    private Map<String, ?> anyParams() {
        return org.mockito.ArgumentMatchers.any();
    }

    private RowMapper<AttachmentDownloadUrlIssueAuditLog> anyRowMapper() {
        return org.mockito.ArgumentMatchers.any();
    }
}
