package studio.one.application.mail.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import studio.one.application.mail.domain.entity.MailMessageEntity;

public interface MailMessageRepository extends JpaRepository<MailMessageEntity, Long> {
    
    Optional<MailMessageEntity> findByFolderAndUid(String folder, long uid);

    Optional<MailMessageEntity> findByMessageId(String messageId);
}
