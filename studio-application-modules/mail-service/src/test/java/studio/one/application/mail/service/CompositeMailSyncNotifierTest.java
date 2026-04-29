package studio.one.application.mail.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.application.mail.web.dto.MailSyncLogDto;

class CompositeMailSyncNotifierTest {

    @Test
    void notifyLogContinuesWhenOneNotifierFails() {
        MailSyncNotifier failing = dto -> {
            throw new IllegalStateException("boom");
        };
        MailSyncNotifier succeeding = mock(MailSyncNotifier.class);
        CompositeMailSyncNotifier notifier = new CompositeMailSyncNotifier(List.of(failing, succeeding));
        MailSyncLogDto dto = MailSyncLogDto.builder().logId(1L).status("SUCCEEDED").build();

        assertThatCode(() -> notifier.notifyLog(dto)).doesNotThrowAnyException();

        verify(succeeding).notifyLog(dto);
    }

    @Test
    void notifyLogAllowsEmptyNotifierList() {
        CompositeMailSyncNotifier notifier = new CompositeMailSyncNotifier(List.of());
        MailSyncLogDto dto = MailSyncLogDto.builder().logId(1L).status("SUCCEEDED").build();

        assertThatCode(() -> notifier.notifyLog(dto)).doesNotThrowAnyException();
    }
}
