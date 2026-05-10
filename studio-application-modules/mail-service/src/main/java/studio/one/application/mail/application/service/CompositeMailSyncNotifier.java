package studio.one.application.mail.application.service;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import studio.one.application.mail.application.usecase.MailSyncNotifier;
import studio.one.application.mail.domain.model.MailSyncLog;

@Slf4j
public class CompositeMailSyncNotifier implements MailSyncNotifier {

    private final List<MailSyncNotifier> notifiers;

    public CompositeMailSyncNotifier(List<MailSyncNotifier> notifiers) {
        this.notifiers = List.copyOf(notifiers);
    }

    @Override
    public void notifyLog(MailSyncLog syncLog) {
        for (MailSyncNotifier notifier : notifiers) {
            try {
                notifier.notifyLog(syncLog);
            } catch (Exception ex) {
                log.debug("Mail sync notifier {} failed: {}", notifier.getClass().getName(), ex.getMessage());
            }
        }
    }
}
