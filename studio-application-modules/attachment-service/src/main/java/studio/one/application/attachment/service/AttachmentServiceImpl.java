package studio.one.application.attachment.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.aplication.security.util.SecurityHelper;
import studio.one.application.attachment.domain.entity.ApplicationAttachment;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.exception.AttachmentNotFoundException;
import studio.one.application.attachment.persistence.AttachmentRepository;
import studio.one.application.attachment.storage.FileStorage;
import studio.one.platform.exception.NotFoundException;

@RequiredArgsConstructor
@Transactional
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    private static final String CACHE_BY_ID = "attachments.byId"; 

    private final AttachmentRepository attachmentRepository;
    private final FileStorage fileStorage;

    @Override
    @Cacheable(cacheNames = CACHE_BY_ID, key = "#attachmentId", unless = "#result == null")
    public Attachment getAttachmentById(long attachmentId) throws NotFoundException {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> AttachmentNotFoundException.byId(attachmentId));
    }

    @Override
    public List<Attachment> getAttachments(int objectType, long objectId) {
        return attachmentRepository.findByObjectTypeAndObjectId(objectType, objectId).stream().map(e -> (Attachment) e)
                .toList();
    }

    @Override
    public List<Attachment> getAttachmentsByObjectAndCreator(int objectType, long objectId, long createdBy) {
        return attachmentRepository.findByObjectTypeAndObjectIdAndCreatedBy(objectType, objectId, createdBy).stream()
                .map(e -> (Attachment) e)
                .toList();
    }

    @Override
    public Page<Attachment> findAttachments(Pageable pageable) {
        Page page = attachmentRepository.findAll(pageable);
        return new PageImpl<>(page.stream().map(e -> (Attachment) e).toList(), pageable, page.getTotalElements());
    }

    public Page<Attachment> findAttachments(int objectType, long objectId, Pageable pageable) {
        Page page = attachmentRepository.findByObjectTypeAndObjectId(objectType, objectId, pageable);
        return new PageImpl<>(page.stream().map(e -> (Attachment) e).toList(), pageable, page.getTotalElements());
    }

    @Override
    public Page<Attachment> findAttachmentsByCreator(long createdBy, Pageable pageable) {
        Page page = attachmentRepository.findByCreatedBy(createdBy, pageable);
        return new PageImpl<>(page.stream().map(e -> (Attachment) e).toList(), pageable, page.getTotalElements());
    }

    @Override
    public Page<Attachment> findAttachmentsByCreator(long createdBy, String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return findAttachmentsByCreator(createdBy, pageable);
        }
        Page<ApplicationAttachment> page = attachmentRepository.findByCreatedByAndNameContainingIgnoreCase(
                createdBy, keyword, pageable);
        return new PageImpl<>(page.stream().map(e -> (Attachment) e).toList(), pageable, page.getTotalElements());
    }

    @Override
    public Page<Attachment> findAttachmentsByObjectAndCreator(
            int objectType, long objectId, long createdBy, Pageable pageable) {
        Page page = attachmentRepository.findByObjectTypeAndObjectIdAndCreatedBy(objectType, objectId, createdBy,
                pageable);
        return new PageImpl<>(page.stream().map(e -> (Attachment) e).toList(), pageable, page.getTotalElements());
    }

    @Override
    public Page<Attachment> findAttachmentsByObjectAndCreator(
            int objectType, long objectId, long createdBy, String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return findAttachmentsByObjectAndCreator(objectType, objectId, createdBy, pageable);
        }
        Page<ApplicationAttachment> page = attachmentRepository
                .findByObjectTypeAndObjectIdAndCreatedByAndNameContainingIgnoreCase(
                        objectType, objectId, createdBy, keyword, pageable);
        return new PageImpl<>(page.stream().map(e -> (Attachment) e).toList(), pageable, page.getTotalElements());
    }

    @Override
    public Page<Attachment> findAttachments(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }
        Page<ApplicationAttachment> page = attachmentRepository.findByNameContainingIgnoreCase(keyword, pageable);
        return new PageImpl<>(page.stream().map(e -> (Attachment) e).toList(), pageable, page.getTotalElements());
    }

    @Override
    public Page<Attachment> findAttachments(int objectType, long objectId, String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return findAttachments(objectType, objectId, pageable);
        }
        Page<ApplicationAttachment> page = attachmentRepository
                .findByObjectTypeAndObjectIdAndNameContainingIgnoreCase(objectType, objectId, keyword, pageable);
        return new PageImpl<>(page.stream().map(e -> (Attachment) e).toList(), pageable, page.getTotalElements());
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
        attachment.setCreatedAt(Instant.now());
        SecurityHelper.getUser().ifPresent(u -> {
            attachment.setCreatedBy(u.getUserId());
        }); 
        Attachment savedAttachment = attachmentRepository.save(attachment);
        fileStorage.save(savedAttachment, inputStream);
        return savedAttachment;
    }

    @Override
    @CachePut(cacheNames = CACHE_BY_ID, key = "#attachment.attachmentId")
    public Attachment saveAttachment(Attachment attachment) {
        return attachmentRepository.save(toEntity(attachment));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CACHE_BY_ID, key = "#attachment.attachmentId")
    })
    public void removeAttachment(Attachment attachment) {
        fileStorage.delete(attachment);
        attachmentRepository.delete(toEntity(attachment));
    }

    @Override
    public InputStream getInputStream(Attachment attachment) throws IOException {
        return fileStorage.load(attachment);
    }

    private ApplicationAttachment toEntity(Attachment attachment) {
        if (attachment instanceof ApplicationAttachment attach) {
            return attach;
        }
        ApplicationAttachment entity = new ApplicationAttachment();
        entity.setAttachmentId(attachment.getAttachmentId());
        entity.setObjectType(attachment.getObjectType());
        entity.setObjectId(attachment.getObjectId());
        entity.setName(attachment.getName());
        entity.setContentType(attachment.getContentType());
        entity.setSize(attachment.getSize());
        return entity;
    }
}
