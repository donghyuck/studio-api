package studio.one.application.mail.service;

import studio.one.application.mail.web.dto.MailSyncLogDto;

public interface MailSyncNotifier {

    void notifyLog(MailSyncLogDto dto);
}
