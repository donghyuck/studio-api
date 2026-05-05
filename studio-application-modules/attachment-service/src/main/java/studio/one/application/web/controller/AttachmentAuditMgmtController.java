package studio.one.application.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.service.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.service.AttachmentDownloadUrlIssueAuditLogQuery;
import studio.one.application.attachment.service.AttachmentDownloadUrlIssueAuditLogQueryService;
import studio.one.application.web.dto.AttachmentDownloadUrlIssueAuditLogDto;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("/api/mgmt/audit")
@RequiredArgsConstructor
@Validated
public class AttachmentAuditMgmtController {

    private final AttachmentDownloadUrlIssueAuditLogQueryService auditLogQueryService;

    @GetMapping("/attachment-download-url-issues")
    @PreAuthorize("@endpointAuthz.can('features','attachment_download_url_issue_audit','read')")
    public ResponseEntity<ApiResponse<Page<AttachmentDownloadUrlIssueAuditLogDto>>> listDownloadUrlIssueLogs(
            @RequestParam(required = false) Long attachmentId,
            @RequestParam(required = false) Integer objectType,
            @RequestParam(required = false) Long objectId,
            @RequestParam(required = false) AttachmentDownloadUrlEndpointKind endpointKind,
            @RequestParam(required = false) String issuedByPrincipalName,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {
        var query = new AttachmentDownloadUrlIssueAuditLogQuery(
                attachmentId,
                objectType,
                objectId,
                endpointKind,
                issuedByPrincipalName,
                from == null ? null : from.toInstant(),
                to == null ? null : to.toInstant());
        return ok(ApiResponse.ok(auditLogQueryService.find(query, pageable)
                .map(AttachmentDownloadUrlIssueAuditLogDto::from)));
    }
}
