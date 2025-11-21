package studio.one.application.attachment.persistence;

import studio.one.application.attachment.domain.model.Attachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AttachmentRepository {

    public static final String SERVICE_NAME = "components:attachment-repository";

    Attachment save(Attachment attachment);

    Optional<Attachment> findById(Long id);

    void delete(Attachment attachment);

    List<Attachment> findByObjectTypeAndObjectId(int objectType, Long objectId);

    Page<Attachment> findByObjectTypeAndObjectId(int objectType, Long objectId, Pageable pageable);

    Page<Attachment> findAll(Pageable pageable);
}
