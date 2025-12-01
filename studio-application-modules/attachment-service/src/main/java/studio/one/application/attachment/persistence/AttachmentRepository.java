package studio.one.application.attachment.persistence;

import studio.one.application.attachment.domain.entity.ApplicationAttachment;
import studio.one.application.attachment.domain.model.Attachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AttachmentRepository {

    public static final String SERVICE_NAME = "components:attachment-repository";

    Attachment save(ApplicationAttachment attachment);

    Optional<ApplicationAttachment> findById(Long id);

    void delete(ApplicationAttachment attachment);

    List<ApplicationAttachment> findByObjectTypeAndObjectId(int objectType, Long objectId);

    Page<ApplicationAttachment> findByObjectTypeAndObjectId(int objectType, Long objectId, Pageable pageable);

    Page<ApplicationAttachment> findAll(Pageable pageable);

    Page<ApplicationAttachment> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    Page<ApplicationAttachment> findByObjectTypeAndObjectIdAndNameContainingIgnoreCase(
            int objectType, Long objectId, String keyword, Pageable pageable);
}
