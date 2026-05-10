package studio.one.application.attachment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import studio.one.application.attachment.application.command.AttachmentDownloadUrlIssueAuditLogQuery;
import studio.one.application.attachment.application.result.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.application.usecase.AttachmentDownloadUrlIssueAuditLogQueryService;
import studio.one.application.attachment.domain.port.AttachmentDownloadUrlIssueAuditLogRepository;
import studio.one.application.attachment.domain.model.AttachmentDownloadUrlIssueAuditLog;

@ExtendWith(MockitoExtension.class)
class AttachmentDownloadUrlIssueAuditLogQueryServiceImplTest {

    @Mock
    private AttachmentDownloadUrlIssueAuditLogRepository repository;

    @Test
    void appliesDefaultIssuedAtSortWhenPageableIsUnsorted() {
        AttachmentDownloadUrlIssueAuditLogQueryService service =
                new AttachmentDownloadUrlIssueAuditLogQueryServiceImpl(repository);
        when(repository.search(any(), any())).thenReturn(Page.empty());

        service.find(null, PageRequest.of(0, 20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(any(), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("issuedAt").isDescending()).isTrue();
        assertThat(captor.getValue().getSort().getOrderFor("logId").isDescending()).isTrue();
    }

    @Test
    void removesUnsupportedSortPropertiesBeforeRepositorySearch() {
        AttachmentDownloadUrlIssueAuditLogQueryService service =
                new AttachmentDownloadUrlIssueAuditLogQueryServiceImpl(repository);
        when(repository.search(any(), any())).thenReturn(Page.empty());
        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Order.asc("objectKey"), Sort.Order.desc("issuedAt")));

        service.find(new AttachmentDownloadUrlIssueAuditLogQuery(
                10L,
                20,
                30L,
                AttachmentDownloadUrlEndpointKind.MGMT,
                "admin",
                null,
                null), pageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(any(), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertThat(sort.getOrderFor("objectKey")).isNull();
        assertThat(sort.getOrderFor("issuedAt").isDescending()).isTrue();
    }
}
