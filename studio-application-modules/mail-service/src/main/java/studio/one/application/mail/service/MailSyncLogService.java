package studio.one.application.mail.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.mail.domain.model.MailSyncLog;

public interface MailSyncLogService {

    String SERVICE_NAME = "mailSyncLogService";

    MailSyncLog start(String triggeredBy);

    void complete(long logId, int processed, int succeeded, int failed, String status, String message);

    List<MailSyncLog> recent(int limit);

    Page<MailSyncLog> page(Pageable pageable);

    MailSyncLog get(long logId);
}
