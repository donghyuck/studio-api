package studio.one.application.attachment.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import studio.one.application.attachment.domain.entity.ApplicationAttachment;
import studio.one.application.attachment.persistence.AttachmentRepository;

public interface AttachmentJpaRepository extends JpaRepository<ApplicationAttachment, Long>, AttachmentRepository {

    Page<ApplicationAttachment> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    Page<ApplicationAttachment> findByObjectTypeAndObjectIdAndNameContainingIgnoreCase(
            int objectType, Long objectId, String keyword, Pageable pageable);
}
