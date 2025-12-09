package studio.one.application.mail.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.application.mail.service.MailAttachmentService;
import studio.one.application.mail.service.MailMessageService;
import studio.one.application.mail.service.MailSyncService;
import studio.one.application.mail.service.MailSyncLogService;
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
    private final MailSyncService mailSyncService;
    private final MailSyncLogService mailSyncLogService;

    @GetMapping("/{mailId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:mail','read')")
    public ResponseEntity<ApiResponse<MailMessageDto>> get(@PathVariable long mailId) {
        var message = mailMessageService.get(mailId);
        var attachments = mailAttachmentService.findByMailId(mailId);
        MailMessageDto dto = MailMessageDto.from(message, () -> attachments);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping("/sync")
    @PreAuthorize("@endpointAuthz.can('features:mail','write')")
    public ResponseEntity<ApiResponse<Integer>> sync() {
        int processed = mailSyncService.sync();
        return ResponseEntity.ok(ApiResponse.ok(processed));
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
}
