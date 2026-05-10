package studio.one.application.mail.infrastructure.persistence.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.application.mail.infrastructure.persistence.jpa.MailAttachmentEntity;

public interface MailAttachmentRepository extends JpaRepository<MailAttachmentEntity, Long> {
    List<MailAttachmentEntity> findByMailId(long mailId);
    void deleteByMailId(long mailId);
}
