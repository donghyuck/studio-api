package studio.one.application.attachment.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import studio.one.application.attachment.domain.entity.ApplicationAttachmentData;

@Repository
public interface AttachmentDataJpaRepository extends JpaRepository<ApplicationAttachmentData, Long> {
}
