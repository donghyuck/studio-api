package studio.one.application.mail.web.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import studio.one.application.mail.service.MailAttachmentService;
import studio.one.application.mail.service.MailMessageService;
import studio.one.application.mail.service.MailSyncJobLauncher;
import studio.one.application.mail.service.MailSyncLogService;

class MailControllerAuthorizationTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listRejectsNonAdmin() {
        MailController controller = new MailController(
                mock(MailMessageService.class),
                mock(MailAttachmentService.class),
                mock(MailSyncLogService.class),
                mock(MailSyncJobLauncher.class));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThrows(AccessDeniedException.class, () -> controller.list(Pageable.unpaged(), null, null));
    }

    @Test
    void syncRejectsAnonymous() {
        MailController controller = new MailController(
                mock(MailMessageService.class),
                mock(MailAttachmentService.class),
                mock(MailSyncLogService.class),
                mock(MailSyncJobLauncher.class));

        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                controller::sync);
    }
}
