package studio.one.application.attachment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import studio.one.application.attachment.domain.entity.AttachmentDownloadAuditLog;
import studio.one.application.attachment.domain.entity.AttachmentDownloadUrlIssueAuditLog;
import studio.one.application.attachment.persistence.AttachmentDownloadAuditLogCount;
import studio.one.application.attachment.persistence.AttachmentDownloadAuditLogRepository;
import studio.one.application.attachment.persistence.AttachmentDownloadUrlIssueAuditLogRepository;

class AttachmentDownloadAuditLogServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-05-05T00:00:00Z");

    @Test
    void recordEnrichesFromIssueLogAndDoesNotStoreRawToken() {
        AttachmentDownloadAuditLogRepository downloadRepository =
                Mockito.mock(AttachmentDownloadAuditLogRepository.class);
        AttachmentDownloadUrlIssueAuditLogRepository issueRepository =
                Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class);
        when(downloadRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(issueRepository.findByTokenHash("token-hash")).thenReturn(Optional.of(issueLog()));
        AttachmentDownloadAuditLogServiceImpl service = new AttachmentDownloadAuditLogServiceImpl(
                downloadRepository,
                issueRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.record(new AttachmentDownloadAuditLogCommand(
                "token-hash",
                null,
                null,
                null,
                null,
                null,
                AttachmentDownloadAuditResult.EXPIRED,
                401,
                null,
                "127.0.0.1",
                "JUnit",
                "TOKEN_EXPIRED"));

        ArgumentCaptor<AttachmentDownloadAuditLog> captor =
                ArgumentCaptor.forClass(AttachmentDownloadAuditLog.class);
        verify(downloadRepository).save(captor.capture());
        AttachmentDownloadAuditLog log = captor.getValue();
        assertThat(log.getIssueLogId()).isEqualTo(1L);
        assertThat(log.getAttachmentId()).isEqualTo(10L);
        assertThat(log.getObjectType()).isEqualTo(20);
        assertThat(log.getObjectId()).isEqualTo(30L);
        assertThat(log.getLinkType()).isEqualTo("APPLICATION_SIGNED");
        assertThat(log.getRequestedAt()).isEqualTo(NOW);
        assertThat(log.getTokenHash()).isEqualTo("token-hash");
        assertThat(log.getResult()).isEqualTo("EXPIRED");
        assertThat(log.getErrorCode()).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void findSanitizesSortAndUsesDefaultWhenInvalid() {
        AttachmentDownloadAuditLogRepository downloadRepository =
                Mockito.mock(AttachmentDownloadAuditLogRepository.class);
        AttachmentDownloadUrlIssueAuditLogRepository issueRepository =
                Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class);
        when(downloadRepository.search(any(), any())).thenReturn(Page.empty());
        AttachmentDownloadAuditLogServiceImpl service = new AttachmentDownloadAuditLogServiceImpl(
                downloadRepository,
                issueRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.find(
                new AttachmentDownloadAuditLogQuery(null, null, null, null, null, null, null, null),
                PageRequest.of(0, 20, Sort.by("rawToken")));

        ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(downloadRepository).search(any(), captor.capture());
        assertThat(captor.getValue().getSort().toString()).contains("requestedAt: DESC", "downloadLogId: DESC");
    }

    @Test
    void countByIssueLogsAggregatesByIssueLogIdAndTokenHashFallback() {
        AttachmentDownloadAuditLogRepository downloadRepository =
                Mockito.mock(AttachmentDownloadAuditLogRepository.class);
        AttachmentDownloadUrlIssueAuditLogRepository issueRepository =
                Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class);
        when(downloadRepository.countByIssueLogIdsOrTokenHashes(any(), any())).thenReturn(List.of(
                new AttachmentDownloadAuditLogCount(1L, "token-a", 2L),
                new AttachmentDownloadAuditLogCount(null, "token-b", 3L)));
        AttachmentDownloadAuditLogServiceImpl service = new AttachmentDownloadAuditLogServiceImpl(
                downloadRepository,
                issueRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        Map<Long, Long> counts = service.countByIssueLogs(List.of(
                issueLog(1L, "token-a"),
                issueLog(2L, "token-b")));

        assertThat(counts).containsEntry(1L, 2L).containsEntry(2L, 3L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Long>> issueIdsCaptor = ArgumentCaptor.forClass(Set.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> tokenHashesCaptor = ArgumentCaptor.forClass(Set.class);
        verify(downloadRepository).countByIssueLogIdsOrTokenHashes(
                issueIdsCaptor.capture(),
                tokenHashesCaptor.capture());
        assertThat(issueIdsCaptor.getValue()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(tokenHashesCaptor.getValue()).containsExactlyInAnyOrder("token-a", "token-b");
    }

    @Test
    void countByIssueLogsReturnsEmptyForEmptyPage() {
        AttachmentDownloadAuditLogRepository downloadRepository =
                Mockito.mock(AttachmentDownloadAuditLogRepository.class);
        AttachmentDownloadUrlIssueAuditLogRepository issueRepository =
                Mockito.mock(AttachmentDownloadUrlIssueAuditLogRepository.class);
        AttachmentDownloadAuditLogServiceImpl service = new AttachmentDownloadAuditLogServiceImpl(
                downloadRepository,
                issueRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.countByIssueLogs(List.of())).isEmpty();

        Mockito.verifyNoInteractions(downloadRepository);
    }

    private AttachmentDownloadUrlIssueAuditLog issueLog() {
        AttachmentDownloadUrlIssueAuditLog log = new AttachmentDownloadUrlIssueAuditLog();
        log.setLogId(1L);
        log.setAttachmentId(10L);
        log.setObjectType(20);
        log.setObjectId(30L);
        log.setLinkType("APPLICATION_SIGNED");
        log.setTokenHash("token-hash");
        return log;
    }

    private AttachmentDownloadUrlIssueAuditLog issueLog(long logId, String tokenHash) {
        AttachmentDownloadUrlIssueAuditLog log = issueLog();
        log.setLogId(logId);
        log.setTokenHash(tokenHash);
        return log;
    }
}
