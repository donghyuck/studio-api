package studio.echo.base.security.audit;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LoginFailureLogRetentionJob {

    private final LoginFailureLogRepository repo;
    private final Integer retentionDays;

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 4 * * *")
    @org.springframework.transaction.annotation.Transactional
    public void purgeOld() { 
        if (retentionDays == null || retentionDays <= 0)
            return;
        java.time.Instant cutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(retentionDays));
        repo.deleteByOccurredAtBefore(cutoff);
    }
}
