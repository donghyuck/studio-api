package studio.one.application.mail.application.usecase;

import org.springframework.scheduling.annotation.Async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MailSyncJobLauncher {

    private final MailSyncService mailSyncService;
    private final MailSyncLogService mailSyncLogService;
    private final MailSyncNotifier mailSyncNotifier;

    @Async
    public void launch(long logId) {
        try {
            mailSyncService.sync(mailSyncLogService.get(logId));
        } catch (Exception ex) {
            log.warn("Mail sync job {} failed: {}", logId, ex.getMessage());
        } finally {
            try {
                mailSyncNotifier.notifyLog(mailSyncLogService.get(logId));
            } catch (Exception notifyEx) {
                log.debug("Failed to notify sync completion for log {}: {}", logId, notifyEx.getMessage());
            }
        }
    }
}
