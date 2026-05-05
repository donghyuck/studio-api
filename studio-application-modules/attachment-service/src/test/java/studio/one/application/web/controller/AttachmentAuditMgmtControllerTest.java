package studio.one.application.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;

import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.service.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.service.AttachmentDownloadUrlIssueAuditLogQuery;
import studio.one.application.attachment.service.AttachmentDownloadUrlIssueAuditLogQueryService;
import studio.one.application.web.dto.AttachmentDownloadUrlIssueAuditLogDto;

@ExtendWith(MockitoExtension.class)
class AttachmentAuditMgmtControllerTest {

    @Mock
    private AttachmentDownloadUrlIssueAuditLogQueryService auditLogQueryService;

    @Test
    void listDownloadUrlIssueLogsMapsFiltersAndResponse() {
        AttachmentAuditMgmtController controller = new AttachmentAuditMgmtController(auditLogQueryService);
        var pageable = PageRequest.of(0, 20);
        AttachmentDownloadUrlIssueAuditLog log = auditLog();
        when(auditLogQueryService.find(any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        var response = controller.listDownloadUrlIssueLogs(
                10L,
                20,
                30L,
                AttachmentDownloadUrlEndpointKind.MGMT,
                "admin",
                OffsetDateTime.parse("2026-05-05T00:00:00+09:00"),
                OffsetDateTime.parse("2026-05-06T00:00:00+09:00"),
                pageable);

        ArgumentCaptor<AttachmentDownloadUrlIssueAuditLogQuery> captor =
                ArgumentCaptor.forClass(AttachmentDownloadUrlIssueAuditLogQuery.class);
        verify(auditLogQueryService).find(captor.capture(), eq(pageable));
        AttachmentDownloadUrlIssueAuditLogQuery query = captor.getValue();
        assertThat(query.attachmentId()).isEqualTo(10L);
        assertThat(query.objectType()).isEqualTo(20);
        assertThat(query.objectId()).isEqualTo(30L);
        assertThat(query.endpointKind()).isEqualTo(AttachmentDownloadUrlEndpointKind.MGMT);
        assertThat(query.issuedByPrincipalName()).isEqualTo("admin");
        assertThat(query.from()).isEqualTo(Instant.parse("2026-05-04T15:00:00Z"));
        assertThat(query.to()).isEqualTo(Instant.parse("2026-05-05T15:00:00Z"));

        AttachmentDownloadUrlIssueAuditLogDto dto = response.getBody().getData().getContent().get(0);
        assertThat(dto.logId()).isEqualTo(1L);
        assertThat(dto.objectKeyHash()).isEqualTo("hash");
    }

    @Test
    void listDownloadUrlIssueLogsUsesDedicatedAuditReadPermission() throws Exception {
        PreAuthorize annotation = AttachmentAuditMgmtController.class
                .getMethod(
                        "listDownloadUrlIssueLogs",
                        Long.class,
                        Integer.class,
                        Long.class,
                        AttachmentDownloadUrlEndpointKind.class,
                        String.class,
                        OffsetDateTime.class,
                        OffsetDateTime.class,
                        org.springframework.data.domain.Pageable.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).contains("features','attachment_download_url_issue_audit','read");
    }

    @Test
    void auditDtoDoesNotExposeRawSignedUrlOrObjectKey() {
        List<String> fieldNames = Arrays.stream(AttachmentDownloadUrlIssueAuditLogDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fieldNames).doesNotContain("url", "signedUrl", "downloadUrl", "objectKey");
        assertThat(fieldNames).contains("objectKeyHash");
    }

    private AttachmentDownloadUrlIssueAuditLog auditLog() {
        AttachmentDownloadUrlIssueAuditLog log = new AttachmentDownloadUrlIssueAuditLog();
        log.setLogId(1L);
        log.setAttachmentId(10L);
        log.setObjectType(20);
        log.setObjectId(30L);
        log.setEndpointKind("MGMT");
        log.setIssuedByUserId(40L);
        log.setIssuedByPrincipalName("admin");
        log.setIssuedAt(Instant.parse("2026-05-05T00:00:00Z"));
        log.setExpiresAt(Instant.parse("2026-05-05T00:05:00Z"));
        log.setTtlSeconds(300L);
        log.setStorageProviderId("main");
        log.setBucket("attachments");
        log.setObjectKeyHash("hash");
        log.setClientIp("127.0.0.1");
        log.setUserAgent("JUnit");
        return log;
    }
}
