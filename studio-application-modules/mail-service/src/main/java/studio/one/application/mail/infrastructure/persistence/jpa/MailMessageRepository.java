package studio.one.application.mail.infrastructure.persistence.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import studio.one.application.mail.infrastructure.persistence.jpa.MailMessageEntity;

public interface MailMessageRepository extends JpaRepository<MailMessageEntity, Long>,
        JpaSpecificationExecutor<MailMessageEntity> {
    
    Optional<MailMessageEntity> findByFolderAndUid(String folder, long uid);

    Optional<MailMessageEntity> findByMessageId(String messageId);

}
