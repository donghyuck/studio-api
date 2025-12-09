package studio.one.application.mail.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.application.mail.domain.entity.MailAttachmentEntity;

public interface MailAttachmentRepository extends JpaRepository<MailAttachmentEntity, Long> {
    List<MailAttachmentEntity> findByMailId(long mailId);
    void deleteByMailId(long mailId);
}
