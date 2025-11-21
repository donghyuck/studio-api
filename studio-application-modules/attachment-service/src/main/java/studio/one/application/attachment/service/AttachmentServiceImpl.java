package studio.one.application.attachment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.exception.AttachmentNotFoundException;
import studio.one.application.attachment.persistence.AttachmentRepository;
import studio.one.application.attachment.storage.FileStorage;
import studio.one.platform.exception.NotFoundException;
import studio.one.application.attachment.domain.entity.ApplicationAttachment;

@RequiredArgsConstructor
@Transactional
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final FileStorage fileStorage;

    @Override
    public Attachment getAttachmentById(long attachmentId) throws NotFoundException {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> AttachmentNotFoundException.byId(attachmentId));
    }

    @Override
    public List<Attachment> getAttachments(int objectType, long objectId) {
        return attachmentRepository.findByObjectTypeAndObjectId(objectType, objectId);
    }

    @Override
    public Page<Attachment> findAttachemnts(Pageable pageable) {
        return attachmentRepository.findAll(pageable);
    }

    public Page<Attachment> findAttachemnts(int objectType, long objectId, Pageable pageable) {
        return attachmentRepository.findByObjectTypeAndObjectId(objectType, objectId, pageable);
    }

    @Override
    public Attachment createAttachment(int objectType, long objectId, String name, String contentType, File file) {
        try {
            int size = (int) file.length();
            InputStream inputStream = new FileInputStream(file);
            return createAttachment(objectType, objectId, name, contentType, inputStream, size);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public Attachment createAttachment(int objectType, long objectId, String name, String contentType,
            InputStream inputStream) {
        try {
            int size = inputStream.available();
            return createAttachment(objectType, objectId, name, contentType, inputStream, size);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public Attachment createAttachment(int objectType, long objectId, String name, String contentType,
            InputStream inputStream,
            int size) {

        ApplicationAttachment attachment = new ApplicationAttachment();
        attachment.setObjectType(objectType);
        attachment.setObjectId(objectId);
        attachment.setName(name);
        attachment.setContentType(contentType);
        attachment.setSize(size);

        Attachment savedAttachment = attachmentRepository.save(attachment);
        fileStorage.save(savedAttachment, inputStream);
        return savedAttachment;
    }

    @Override
    public Attachment saveAttachment(Attachment attachment) {
        return attachmentRepository.save(attachment);
    }

    @Override
    public void removeAttachment(Attachment attachment) {
        fileStorage.delete(attachment);
        attachmentRepository.delete(attachment);
    }

    @Override
    public InputStream getInputStream(Attachment attachment) throws IOException {
        return fileStorage.load(attachment);
    }

}
