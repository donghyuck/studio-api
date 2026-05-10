package studio.one.application.mail.application.usecase;

import studio.one.application.mail.web.dto.response.MailSyncLogDto;

public interface MailSyncNotifier {

    void notifyLog(MailSyncLogDto dto);
}
