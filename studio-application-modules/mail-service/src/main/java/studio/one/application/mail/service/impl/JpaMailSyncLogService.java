package studio.one.application.mail.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import studio.one.application.mail.domain.entity.MailSyncLogEntity;
import studio.one.application.mail.domain.model.MailSyncLog;
import studio.one.application.mail.persistence.repository.MailSyncLogRepository;
import studio.one.application.mail.service.MailSyncLogService;

@Transactional
public class JpaMailSyncLogService implements MailSyncLogService {

    private final MailSyncLogRepository repository;

    public JpaMailSyncLogService(MailSyncLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public MailSyncLog start(String triggeredBy) {
        MailSyncLogEntity entity = new MailSyncLogEntity();
        entity.setStartedAt(Instant.now());
        entity.setStatus("running");
        entity.setTriggeredBy(triggeredBy);
        return repository.save(entity);
    }

    @Override
    public void complete(long logId, int processed, int succeeded, int failed, String status, String message) {
        MailSyncLogEntity entity = repository.findById(logId).orElse(null);
        if (entity == null) {
            return;
        }
        entity.setFinishedAt(Instant.now());
        entity.setProcessed(processed);
        entity.setSucceeded(succeeded);
        entity.setFailed(failed);
        entity.setStatus(status);
        entity.setMessage(message);
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MailSyncLog> recent(int limit) {
        List<MailSyncLogEntity> list = repository.findTop50ByOrderByStartedAtDesc();
        if (limit > 0 && list.size() > limit) {
            return list.subList(0, limit).stream().map(l -> l).collect(Collectors.toList());
        }
        return list.stream().map(l -> l).collect(Collectors.toList());
    }
}
