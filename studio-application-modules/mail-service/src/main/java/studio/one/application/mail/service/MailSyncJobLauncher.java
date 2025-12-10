package studio.one.application.mail.service;

import org.springframework.scheduling.annotation.Async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.mail.web.dto.MailSyncLogDto;

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
                MailSyncLogDto dto = MailSyncLogDto.from(mailSyncLogService.get(logId));
                mailSyncNotifier.notify(dto);
            } catch (Exception notifyEx) {
                log.debug("Failed to notify sync completion for log {}: {}", logId, notifyEx.getMessage());
            }
        }
    }
}
