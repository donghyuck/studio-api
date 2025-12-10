package studio.one.application.mail.web.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;
import studio.one.application.mail.service.MailAttachmentService;
import studio.one.application.mail.service.MailMessageService;
import studio.one.application.mail.service.MailSyncLogService;
import studio.one.application.mail.service.MailSyncService;
import studio.one.application.mail.service.MailSyncJobLauncher;
import studio.one.application.mail.service.MailSyncNotifier;
import studio.one.application.mail.web.dto.MailMessageDto;
import studio.one.application.mail.web.dto.MailSyncLogDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".mail.web.base-path:/api/mgmt/mail}")
@RequiredArgsConstructor
@Validated
public class MailController {

    private final MailMessageService mailMessageService;
    private final MailAttachmentService mailAttachmentService;
    private final MailSyncLogService mailSyncLogService;
    private final MailSyncJobLauncher mailSyncJobLauncher;
    private final MailSyncNotifier mailSyncNotifier;

    @GetMapping("/{mailId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:mail','read')")
    public ResponseEntity<ApiResponse<MailMessageDto>> get(@PathVariable long mailId) {
        var message = mailMessageService.get(mailId);
        var attachments = mailAttachmentService.findByMailId(mailId);
        MailMessageDto dto = MailMessageDto.from(message, () -> attachments);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:mail','read')")
    public ResponseEntity<ApiResponse<Page<MailMessageDto>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<MailMessageDto> dtoPage = mailMessageService.page(pageable)
                .map(m -> MailMessageDto.from(m, List.of()));
        return ResponseEntity.ok(ApiResponse.ok(dtoPage));
    }

    @PostMapping("/sync")
    @PreAuthorize("@endpointAuthz.can('features:mail','write')")
    public ResponseEntity<ApiResponse<Long>> sync() {
        var log = mailSyncLogService.start("manual");
        mailSyncJobLauncher.launch(log.getLogId());
        return ResponseEntity.ok(ApiResponse.ok(log.getLogId()));
    }

    @GetMapping("/sync/logs")
    @PreAuthorize("@endpointAuthz.can('features:mail','read')")
    public ResponseEntity<ApiResponse<List<MailSyncLogDto>>> logs(
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit) {
        List<MailSyncLogDto> logs = mailSyncLogService.recent(limit).stream()
                .map(MailSyncLogDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }

    @GetMapping("/sync/logs/page")
    @PreAuthorize("@endpointAuthz.can('features:mail','read')")
    public ResponseEntity<ApiResponse<Page<MailSyncLogDto>>> pagedLogs(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<MailSyncLogDto> dtoPage = mailSyncLogService.page(pageable).map(MailSyncLogDto::from);
        return ResponseEntity.ok(ApiResponse.ok(dtoPage));
    }

    @GetMapping("/sync/stream")
    @PreAuthorize("@endpointAuthz.can('features:mail','read')")
    public SseEmitter stream() {
        // default timeout 30 minutes
        return mailSyncNotifier.register(30 * 60 * 1000L);
    }
}
