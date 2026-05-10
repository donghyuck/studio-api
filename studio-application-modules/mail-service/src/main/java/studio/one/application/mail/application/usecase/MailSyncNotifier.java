package studio.one.application.mail.application.usecase;

import studio.one.application.mail.domain.model.MailSyncLog;

public interface MailSyncNotifier {

    void notifyLog(MailSyncLog log);
}
