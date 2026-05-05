package studio.one.application.attachment.persistence.jdbc;

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

import studio.one.application.attachment.domain.entity.AttachmentDownloadAuditLog;
import studio.one.application.attachment.persistence.AttachmentDownloadAuditLogCount;
import studio.one.application.attachment.service.AttachmentDownloadAuditLogQuery;
import studio.one.application.attachment.service.AttachmentDownloadAuditResult;

@ExtendWith(MockitoExtension.class)
class JdbcAttachmentDownloadAuditLogRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate template;

    @Test
    void savePersistsDownloadAuditFields() {
        JdbcAttachmentDownloadAuditLogRepository repository =
                new JdbcAttachmentDownloadAuditLogRepository(template);
        AttachmentDownloadAuditLog log = new AttachmentDownloadAuditLog();
        log.setIssueLogId(1L);
        log.setTokenHash("token-hash");
        log.setAttachmentId(10L);
        log.setObjectType(20);
        log.setObjectId(30L);
        log.setLinkType("APPLICATION_SIGNED");
        log.setRequestedAt(Instant.parse("2026-05-05T00:00:00Z"));
        log.setResult("SUCCEEDED");
        log.setHttpStatus(200);
        log.setDownloadedBytes(123L);
        log.setClientIp("127.0.0.1");
        log.setUserAgent("JUnit");

        repository.save(log);

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(template).update(anyString(), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue())
                .containsEntry("issueLogId", 1L)
                .containsEntry("tokenHash", "token-hash")
                .containsEntry("result", "SUCCEEDED")
                .containsEntry("downloadedBytes", 123L);
    }

    @Test
    void searchBuildsFiltersAndWhitelistedOrderOnly() {
        JdbcAttachmentDownloadAuditLogRepository repository =
                new JdbcAttachmentDownloadAuditLogRepository(template);
        when(template.queryForObject(anyString(), anyParams(), eq(Long.class))).thenReturn(1L);
        when(template.query(anyString(), anyParams(), anyRowMapper())).thenReturn(List.of());
        var query = new AttachmentDownloadAuditLogQuery(
                10L,
                20,
                30L,
                "token-hash",
                AttachmentDownloadAuditResult.FAILED,
                Instant.parse("2026-05-05T00:00:00Z"),
                Instant.parse("2026-05-06T00:00:00Z"),
                "127.0.0.1");
        var pageable = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Order.asc("rawToken"), Sort.Order.desc("requestedAt")));

        repository.search(query, pageable);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(template).query(sqlCaptor.capture(), anyParams(), anyRowMapper());
        String sql = sqlCaptor.getValue();
        assertThat(sql)
                .contains("ATTACHMENT_ID = :attachmentId")
                .contains("OBJECT_TYPE = :objectType")
                .contains("OBJECT_ID = :objectId")
                .contains("TOKEN_HASH = :tokenHash")
                .contains("RESULT = :result")
                .contains("REQUESTED_AT >= :from")
                .contains("REQUESTED_AT < :to")
                .contains("CLIENT_IP = :clientIp")
                .contains("order by REQUESTED_AT desc")
                .doesNotContain("rawToken");
    }

    @Test
    void countByIssueLogIdsOrTokenHashesUsesBulkGroupQuery() {
        JdbcAttachmentDownloadAuditLogRepository repository =
                new JdbcAttachmentDownloadAuditLogRepository(template);
        when(template.query(anyString(), anyParams(), anyCountRowMapper())).thenReturn(List.of());

        repository.countByIssueLogIdsOrTokenHashes(List.of(1L, 2L), List.of("token-a"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(template).query(sqlCaptor.capture(), paramsCaptor.capture(), anyCountRowMapper());
        assertThat(sqlCaptor.getValue())
                .contains("ISSUE_LOG_ID in (:issueLogIds)")
                .contains("TOKEN_HASH in (:tokenHashes)")
                .contains("group by ISSUE_LOG_ID, TOKEN_HASH");
        assertThat(paramsCaptor.getValue())
                .containsEntry("issueLogIds", List.of(1L, 2L))
                .containsEntry("tokenHashes", List.of("token-a"));
    }

    private Map<String, ?> anyParams() {
        return org.mockito.ArgumentMatchers.any();
    }

    private RowMapper<AttachmentDownloadAuditLog> anyRowMapper() {
        return org.mockito.ArgumentMatchers.any();
    }

    private RowMapper<AttachmentDownloadAuditLogCount> anyCountRowMapper() {
        return org.mockito.ArgumentMatchers.any();
    }
}
