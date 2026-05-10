package studio.one.application.mail.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.application.mail.application.usecase.MailSyncNotifier;
import studio.one.application.mail.domain.model.MailSyncLog;

class CompositeMailSyncNotifierTest {

    @Test
    void notifyLogContinuesWhenOneNotifierFails() {
        MailSyncNotifier failing = dto -> {
            throw new IllegalStateException("boom");
        };
        MailSyncNotifier succeeding = mock(MailSyncNotifier.class);
        CompositeMailSyncNotifier notifier = new CompositeMailSyncNotifier(List.of(failing, succeeding));
        MailSyncLog log = mock(MailSyncLog.class);

        assertThatCode(() -> notifier.notifyLog(log)).doesNotThrowAnyException();

        verify(succeeding).notifyLog(log);
    }

    @Test
    void notifyLogAllowsEmptyNotifierList() {
        CompositeMailSyncNotifier notifier = new CompositeMailSyncNotifier(List.of());
        MailSyncLog log = mock(MailSyncLog.class);

        assertThatCode(() -> notifier.notifyLog(log)).doesNotThrowAnyException();
    }
}
