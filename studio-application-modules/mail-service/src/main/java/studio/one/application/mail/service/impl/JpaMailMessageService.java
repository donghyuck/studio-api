package studio.one.application.mail.service.impl;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import studio.one.application.mail.domain.entity.MailMessageEntity;
import studio.one.application.mail.domain.model.MailMessage;
import studio.one.application.mail.persistence.repository.MailMessageRepository;
import studio.one.application.mail.service.MailMessageService;
import studio.one.platform.exception.NotFoundException;

@Transactional
@Service(MailMessageService.SERVICE_NAME )
public class JpaMailMessageService implements MailMessageService {

    private final MailMessageRepository repository;

    public JpaMailMessageService(MailMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public MailMessage get(long mailId) {
        return repository.findById(mailId).orElseThrow(() -> NotFoundException.of("mail", mailId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MailMessage> findByFolderAndUid(String folder, long uid) {
        return repository.findByFolderAndUid(folder, uid).map(m -> m);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MailMessage> findByMessageId(String messageId) {
        return repository.findByMessageId(messageId).map(m -> m);
    }

    @Override
    public MailMessage saveOrUpdate(MailMessage message) {
        MailMessageEntity entity = toEntity(message);
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MailMessage> page(Pageable pageable) {
        return page(pageable, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MailMessage> page(Pageable pageable, String query, String fields) {
        int pageIndex = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.max(1, pageable.getPageSize());
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Order.desc("mailId"));
        Pageable safe = PageRequest.of(pageIndex, pageSize, sort);
        if (StringUtils.hasText(query)) {
            String needle = query.trim().toLowerCase(Locale.ROOT);
            Specification<MailMessageEntity> spec = buildSearchSpec(needle, resolveFields(fields));
            return repository.findAll(spec, safe).map(m -> (MailMessage) m);
        }
        return repository.findAll(safe).map(m -> (MailMessage) m);
    }

    private Specification<MailMessageEntity> buildSearchSpec(String needle, Set<String> fields) {
        return (root, query, cb) -> {
            String like = "%" + needle + "%";
            var predicates = new ArrayList<>();
            for (String field : fields) {
                var path = root.get(field).as(String.class);
                var lowered = cb.lower(cb.coalesce(path, ""));
                predicates.add(cb.like(lowered, like));
            }
            return cb.or(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
        };
    }

    private Set<String> resolveFields(String fields) {
        Set<String> allowed = new LinkedHashSet<>(Arrays.asList(
                "subject", "fromAddress", "toAddress", "ccAddress", "bccAddress",
                "messageId", "folder", "body"));
        if (!StringUtils.hasText(fields)) {
            return allowed;
        }
        Set<String> selected = new LinkedHashSet<>();
        for (String raw : fields.split(",")) {
            String field = raw.trim();
            if (allowed.contains(field)) {
                selected.add(field);
            }
        }
        return selected.isEmpty() ? allowed : selected;
    }

    private MailMessageEntity toEntity(MailMessage message) {
        if (message instanceof MailMessageEntity entity) {
            return entity;
        }
        MailMessageEntity entity = new MailMessageEntity();
        entity.setMailId(message.getMailId());
        entity.setFolder(message.getFolder());
        entity.setUid(message.getUid());
        entity.setMessageId(message.getMessageId());
        entity.setSubject(message.getSubject());
        entity.setFromAddress(message.getFromAddress());
        entity.setToAddress(message.getToAddress());
        entity.setCcAddress(message.getCcAddress());
        entity.setBccAddress(message.getBccAddress());
        entity.setSentAt(message.getSentAt());
        entity.setReceivedAt(message.getReceivedAt());
        entity.setFlags(message.getFlags());
        entity.setBody(message.getBody());
        entity.setCreatedAt(message.getCreatedAt());
        entity.setUpdatedAt(message.getUpdatedAt());
        entity.setProperties(message.getProperties());
        return entity;
    }
}
