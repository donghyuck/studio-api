package studio.one.application.mail.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import studio.one.application.mail.domain.entity.MailAttachmentEntity;
import studio.one.application.mail.domain.model.MailAttachment;
import studio.one.application.mail.persistence.repository.MailAttachmentRepository;
import studio.one.application.mail.service.MailAttachmentService;

@Transactional
public class JpaMailAttachmentService implements MailAttachmentService {

    private final MailAttachmentRepository repository;

    public JpaMailAttachmentService(MailAttachmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void replaceAttachments(long mailId, List<MailAttachment> attachments) {
        repository.deleteByMailId(mailId);
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<MailAttachmentEntity> entities = attachments.stream()
                .map(att -> toEntity(att, mailId, now))
                .collect(Collectors.toList());
        repository.saveAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MailAttachment> findByMailId(long mailId) {
        return repository.findByMailId(mailId).stream().map(a -> a).collect(Collectors.toList());
    }

    private MailAttachmentEntity toEntity(MailAttachment attachment, long mailId, Instant now) {
        MailAttachmentEntity entity = new MailAttachmentEntity();
        entity.setAttachmentId(attachment.getAttachmentId());
        entity.setMailId(mailId);
        entity.setFilename(attachment.getFilename());
        entity.setContentType(attachment.getContentType());
        entity.setSize(attachment.getSize());
        entity.setContent(attachment.getContent());
        entity.setCreatedAt(attachment.getCreatedAt() == null ? now : attachment.getCreatedAt());
        entity.setUpdatedAt(now);
        return entity;
    }
}
