package studio.one.application.mail.web.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
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
        requireAdmin();
        // default timeout 30 minutes
        return mailSyncNotifier.register(30 * 60 * 1000L);
    }

    private void requireAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        boolean isAdmin = auth.getAuthorities() != null && auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ADMIN".equals(role));
        if (!isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("Admin privileges required");
        }
    }
}
