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

import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.service.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.service.AttachmentDownloadUrlIssueAuditLogQuery;

@ExtendWith(MockitoExtension.class)
class JdbcAttachmentDownloadUrlIssueAuditLogRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate template;

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
