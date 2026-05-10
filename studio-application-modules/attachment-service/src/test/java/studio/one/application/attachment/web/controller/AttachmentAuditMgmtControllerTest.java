package studio.one.application.attachment.web.controller;

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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;

import studio.one.application.attachment.infrastructure.persistence.model.AttachmentDownloadAuditLog;
import studio.one.application.attachment.application.command.AttachmentDownloadAuditLogQuery;
import studio.one.application.attachment.application.usecase.AttachmentDownloadAuditLogService;
import studio.one.application.attachment.application.result.AttachmentDownloadAuditResult;
import studio.one.application.attachment.infrastructure.persistence.model.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.application.result.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.application.command.AttachmentDownloadUrlIssueAuditLogQuery;
import studio.one.application.attachment.application.usecase.AttachmentDownloadUrlIssueAuditLogQueryService;
import studio.one.application.attachment.web.dto.response.AttachmentDownloadAuditLogDto;
import studio.one.application.attachment.web.dto.response.AttachmentDownloadUrlIssueAuditLogDto;

@ExtendWith(MockitoExtension.class)
class AttachmentAuditMgmtControllerTest {

    @Mock
    private AttachmentDownloadUrlIssueAuditLogQueryService auditLogQueryService;

    @Mock
    private AttachmentDownloadAuditLogService downloadAuditLogService;

    @Test
    void listDownloadUrlIssueLogsMapsFiltersAndResponse() {
        AttachmentAuditMgmtController controller =
                new AttachmentAuditMgmtController(auditLogQueryService, downloadAuditLogService);
        var pageable = PageRequest.of(0, 20);
        AttachmentDownloadUrlIssueAuditLog log = auditLog();
        when(auditLogQueryService.find(any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(log), pageable, 1));
        when(downloadAuditLogService.countByIssueLogs(List.of(log))).thenReturn(Map.of(1L, 3L));

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
        assertThat(dto.linkType()).isEqualTo("APPLICATION_SIGNED");
        assertThat(dto.tokenHash()).isEqualTo("token-hash");
        assertThat(dto.downloadCount()).isEqualTo(3L);
        assertThat(dto.objectKeyHash()).isEqualTo("hash");
    }

    @Test
    void listDownloadUrlIssueLogsDefaultsMissingDownloadCountToZero() {
        AttachmentAuditMgmtController controller =
                new AttachmentAuditMgmtController(auditLogQueryService, downloadAuditLogService);
        var pageable = PageRequest.of(0, 20);
        AttachmentDownloadUrlIssueAuditLog log = auditLog();
        when(auditLogQueryService.find(any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(log), pageable, 1));
        when(downloadAuditLogService.countByIssueLogs(List.of(log))).thenReturn(Map.of());

        var response = controller.listDownloadUrlIssueLogs(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                pageable);

        AttachmentDownloadUrlIssueAuditLogDto dto = response.getBody().getData().getContent().get(0);
        assertThat(dto.downloadCount()).isZero();
    }

    @Test
    void listDownloadLogsMapsFiltersAndResponse() {
        AttachmentAuditMgmtController controller =
                new AttachmentAuditMgmtController(auditLogQueryService, downloadAuditLogService);
        var pageable = PageRequest.of(0, 20);
        AttachmentDownloadAuditLog log = downloadAuditLog();
        when(downloadAuditLogService.find(any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(log), pageable, 1));

        var response = controller.listDownloadLogs(
                10L,
                20,
                30L,
                "token-hash",
                AttachmentDownloadAuditResult.SUCCEEDED,
                OffsetDateTime.parse("2026-05-05T00:00:00+09:00"),
                OffsetDateTime.parse("2026-05-06T00:00:00+09:00"),
                "127.0.0.1",
                pageable);

        ArgumentCaptor<AttachmentDownloadAuditLogQuery> captor =
                ArgumentCaptor.forClass(AttachmentDownloadAuditLogQuery.class);
        verify(downloadAuditLogService).find(captor.capture(), eq(pageable));
        AttachmentDownloadAuditLogQuery query = captor.getValue();
        assertThat(query.attachmentId()).isEqualTo(10L);
        assertThat(query.objectType()).isEqualTo(20);
        assertThat(query.objectId()).isEqualTo(30L);
        assertThat(query.tokenHash()).isEqualTo("token-hash");
        assertThat(query.result()).isEqualTo(AttachmentDownloadAuditResult.SUCCEEDED);
        assertThat(query.from()).isEqualTo(Instant.parse("2026-05-04T15:00:00Z"));
        assertThat(query.to()).isEqualTo(Instant.parse("2026-05-05T15:00:00Z"));
        assertThat(query.clientIp()).isEqualTo("127.0.0.1");

        AttachmentDownloadAuditLogDto dto = response.getBody().getData().getContent().get(0);
        assertThat(dto.downloadLogId()).isEqualTo(2L);
        assertThat(dto.issueLogId()).isEqualTo(1L);
        assertThat(dto.tokenHash()).isEqualTo("token-hash");
        assertThat(dto.result()).isEqualTo("SUCCEEDED");
        assertThat(dto.downloadedBytes()).isEqualTo(123L);
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
    void listDownloadLogsUsesDedicatedAuditReadPermission() throws Exception {
        PreAuthorize annotation = AttachmentAuditMgmtController.class
                .getMethod(
                        "listDownloadLogs",
                        Long.class,
                        Integer.class,
                        Long.class,
                        String.class,
                        AttachmentDownloadAuditResult.class,
                        OffsetDateTime.class,
                        OffsetDateTime.class,
                        String.class,
                        org.springframework.data.domain.Pageable.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).contains("features','attachment_download_audit','read");
    }

    @Test
    void auditDtoDoesNotExposeRawSignedUrlOrObjectKey() {
        List<String> fieldNames = Arrays.stream(AttachmentDownloadUrlIssueAuditLogDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fieldNames).doesNotContain("url", "signedUrl", "downloadUrl", "objectKey", "token", "rawToken");
        assertThat(fieldNames).contains("objectKeyHash", "tokenHash", "linkType", "downloadCount");
    }

    @Test
    void downloadAuditDtoDoesNotExposeRawToken() {
        List<String> fieldNames = Arrays.stream(AttachmentDownloadAuditLogDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fieldNames).doesNotContain("url", "signedUrl", "downloadUrl", "token", "rawToken");
        assertThat(fieldNames).contains("tokenHash", "result", "downloadedBytes");
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
        log.setLinkType("APPLICATION_SIGNED");
        log.setTokenHash("token-hash");
        log.setStorageProviderId("main");
        log.setBucket("attachments");
        log.setObjectKeyHash("hash");
        log.setClientIp("127.0.0.1");
        log.setUserAgent("JUnit");
        return log;
    }

    private AttachmentDownloadAuditLog downloadAuditLog() {
        AttachmentDownloadAuditLog log = new AttachmentDownloadAuditLog();
        log.setDownloadLogId(2L);
        log.setIssueLogId(1L);
        log.setTokenHash("token-hash");
        log.setAttachmentId(10L);
        log.setObjectType(20);
        log.setObjectId(30L);
        log.setLinkType("APPLICATION_SIGNED");
        log.setRequestedAt(Instant.parse("2026-05-05T00:01:00Z"));
        log.setResult("SUCCEEDED");
        log.setHttpStatus(200);
        log.setDownloadedBytes(123L);
        log.setClientIp("127.0.0.1");
        log.setUserAgent("JUnit");
        return log;
    }
}
