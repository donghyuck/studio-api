package studio.one.application.mail.service;

import java.util.List;

import studio.one.application.mail.domain.model.MailSyncLog;

public interface MailSyncLogService {

    MailSyncLog start(String triggeredBy);

    void complete(long logId, int processed, int succeeded, int failed, String status, String message);

    List<MailSyncLog> recent(int limit);
}
