package studio.one.application.attachment.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import studio.one.application.attachment.domain.entity.ApplicationAttachment;
import studio.one.application.attachment.persistence.AttachmentRepository;

public interface AttachmentJpaRepository extends JpaRepository<ApplicationAttachment, Long>, AttachmentRepository {

}
