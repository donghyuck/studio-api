package studio.one.application.mail.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import studio.one.application.mail.domain.entity.MailSyncLogEntity;
import studio.one.application.mail.domain.model.MailSyncLog;
import studio.one.application.mail.persistence.repository.MailSyncLogRepository;
import studio.one.application.mail.service.MailSyncLogService;

@Transactional
@Service(MailSyncLogService.SERVICE_NAME)
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

    @Override
    @Transactional(readOnly = true)
    public Page<MailSyncLog> page(Pageable pageable) {
        int pageIndex = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.max(1, pageable.getPageSize());
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.desc("logId"));
        Pageable safe = PageRequest.of(pageIndex, pageSize, sort);
        return repository.findAll(safe).map(l -> (MailSyncLog) l);
    }

    @Override
    @Transactional(readOnly = true)
    public MailSyncLog get(long logId) {
        return repository.findById(logId)
                .map(l -> (MailSyncLog) l)
                .orElseThrow(() -> studio.one.platform.exception.NotFoundException.of("mailSyncLog", logId));
    }
}
