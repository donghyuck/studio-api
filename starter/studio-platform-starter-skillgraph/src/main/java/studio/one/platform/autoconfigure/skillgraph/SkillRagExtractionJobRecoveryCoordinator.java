package studio.one.platform.autoconfigure.skillgraph;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;

@Slf4j
@RequiredArgsConstructor
public class SkillRagExtractionJobRecoveryCoordinator {

    private final SkillRagExtractionJobService jobService;
    private final ScheduledExecutorService scheduler;
    private final Duration recoveryInterval;

    @PostConstruct
    void start() {
        long intervalMillis = Math.max(1_000L, recoveryInterval.toMillis());
        scheduler.scheduleWithFixedDelay(this::recover, 0L, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void recover() {
        try {
            int recovered = jobService.recoverStaleJobs();
            if (recovered > 0) {
                log.info("Recovered {} stale SkillGraph RAG extraction job(s)", recovered);
            }
        } catch (RuntimeException ex) {
            log.warn("SkillGraph RAG extraction job recovery failed", ex);
        }
    }
}
