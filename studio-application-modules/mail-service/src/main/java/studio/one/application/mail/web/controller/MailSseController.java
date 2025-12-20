package studio.one.application.mail.web.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.RequiredArgsConstructor;
import studio.one.application.mail.service.SseMailSyncNotifier;
import studio.one.platform.constant.PropertyKeys;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".mail.web.base-path:/api/mgmt/mail}")
@RequiredArgsConstructor
@Validated
public class MailSseController {
    
    private final SseMailSyncNotifier mailSyncNotifier;

    @GetMapping("/sync/stream")
    @PreAuthorize("@endpointAuthz.can('features:mail','read')")
    public SseEmitter stream() {
        // default timeout 30 minutes
        return mailSyncNotifier.register(30 * 60 * 1000L);
    }
}
