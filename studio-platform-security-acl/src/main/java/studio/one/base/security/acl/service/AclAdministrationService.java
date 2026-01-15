package studio.one.base.security.acl.service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.domain.entity.AclClassEntity;
import studio.one.base.security.acl.domain.entity.AclEntryEntity;
import studio.one.base.security.acl.domain.entity.AclObjectIdentityEntity;
import studio.one.base.security.acl.domain.entity.AclSidEntity;
import studio.one.base.security.acl.persistence.AclClassRepository;
import studio.one.base.security.acl.persistence.AclEntryRepository;
import studio.one.base.security.acl.persistence.AclObjectIdentityRepository;
import studio.one.base.security.acl.persistence.AclSidRepository;
import studio.one.base.security.acl.web.dto.AclClassDto;
import studio.one.base.security.acl.web.dto.AclClassRequest;
import studio.one.base.security.acl.web.dto.AclEntryDto;
import studio.one.base.security.acl.web.dto.AclEntryRequest;
import studio.one.base.security.acl.web.dto.AclObjectIdentityDto;
import studio.one.base.security.acl.web.dto.AclObjectIdentityRequest;
import studio.one.base.security.acl.web.dto.AclSidDto;
import studio.one.base.security.acl.web.dto.AclSidRequest;
import studio.one.base.security.acl.policy.AclPolicyRefreshPublisher;
import studio.one.platform.security.authz.acl.AclMetricsRecorder;

/**
 * Service that exposes ACL metadata management operations.
 */
@RequiredArgsConstructor
@Slf4j
public class AclAdministrationService {

    private final AclClassRepository classRepository;
    private final AclSidRepository sidRepository;
    private final AclObjectIdentityRepository objectIdentityRepository;
    private final AclEntryRepository entryRepository;
    private final AclPolicyRefreshPublisher refreshPublisher;
    private final AclMetricsRecorder metricsRecorder;
    private final boolean auditEnabled;

    public List<AclClassDto> listClasses() {
        return classRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public AclClassDto createClass(AclClassRequest request) {
        AclClassEntity entity = new AclClassEntity();
        entity.setClassName(request.getClassName().trim());
        return toDto(classRepository.save(entity));
    }

    public void deleteClass(Long id) {
        classRepository.deleteById(id);
    }

    public List<AclSidDto> listSids() {
        return sidRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public AclSidDto createSid(AclSidRequest request) {
        AclSidEntity entity = new AclSidEntity();
        entity.setSid(request.getSid().trim());
        entity.setPrincipal(request.isPrincipal());
        return toDto(sidRepository.save(entity));
    }

    public void deleteSid(Long id) {
        sidRepository.deleteById(id);
    }

    public List<AclObjectIdentityDto> listObjectIdentities() {
        return objectIdentityRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public AclObjectIdentityDto createObjectIdentity(AclObjectIdentityRequest request) {
        AclObjectIdentityEntity entity = new AclObjectIdentityEntity();
        AclClassEntity clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new IllegalArgumentException("classId"));
        entity.setAclClass(clazz);
        entity.setObjectIdIdentity(request.getObjectIdentity().trim());
        entity.setEntriesInheriting(request.isEntriesInheriting());
        if (request.getParentId() != null) {
            objectIdentityRepository.findById(request.getParentId())
                    .ifPresent(entity::setParent);
        }
        if (request.getOwnerSidId() != null) {
            sidRepository.findById(request.getOwnerSidId())
                    .ifPresent(entity::setOwnerSid);
        }
        return toDto(objectIdentityRepository.save(entity));
    }

    public void deleteObjectIdentity(Long id) {
        objectIdentityRepository.deleteById(id);
    }

    public List<AclEntryDto> listEntries() {
        return entryRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public AclEntryDto createEntry(AclEntryRequest request) {
        long started = System.nanoTime();
        AclEntryEntity entry = new AclEntryEntity();
        AclObjectIdentityEntity objectIdentity = objectIdentityRepository.findById(request.getObjectIdentityId())
                .orElseThrow(() -> new IllegalArgumentException("objectIdentityId"));
        AclSidEntity sid = sidRepository.findById(request.getSidId())
                .orElseThrow(() -> new IllegalArgumentException("sidId"));
        entry.setAclObjectIdentity(objectIdentity);
        entry.setSid(sid);
        entry.setAceOrder(Objects.requireNonNullElse(request.getAceOrder(), nextAceOrder(objectIdentity)));
        entry.setMask(request.getMask());
        entry.setGranting(request.isGranting());
        entry.setAuditSuccess(request.isAuditSuccess());
        entry.setAuditFailure(request.isAuditFailure());
        AclEntryDto dto = toDto(entryRepository.save(entry));
        refreshPublisher.publishAfterCommit();
        metricsRecorder.record("admin_entry_create", Duration.ofNanos(System.nanoTime() - started), 1);
        if (auditEnabled && log.isInfoEnabled()) {
        log.info("ACL_AUDIT action=admin_entry_create entryId={} objectIdentityId={} sidId={}",
                dto.getId(), dto.getObjectIdentityId(), dto.getSidId());
        }
        return dto;
    }

    public void deleteEntry(Long id) {
        long started = System.nanoTime();
        entryRepository.deleteById(id);
        refreshPublisher.publishAfterCommit();
        metricsRecorder.record("admin_entry_delete", Duration.ofNanos(System.nanoTime() - started), 1);
        if (auditEnabled && log.isInfoEnabled()) {
            log.info("ACL_AUDIT action=admin_entry_delete entryId={}", id);
        }
    }

    private AclClassDto toDto(AclClassEntity entity) {
        return new AclClassDto(entity.getId(), entity.getClassName());
    }

    private AclSidDto toDto(AclSidEntity entity) {
        return new AclSidDto(entity.getId(), entity.isPrincipal(), entity.getSid());
    }

    private AclObjectIdentityDto toDto(AclObjectIdentityEntity entity) {
        return new AclObjectIdentityDto(
                entity.getId(),
                entity.getAclClass().getId(),
                entity.getAclClass().getClassName(),
                entity.getObjectIdIdentity(),
                (entity.getParent() != null) ? entity.getParent().getId() : null,
                (entity.getOwnerSid() != null) ? entity.getOwnerSid().getId() : null,
                entity.isEntriesInheriting());
    }

    private AclEntryDto toDto(AclEntryEntity entity) {
        return new AclEntryDto(
                entity.getId(),
                entity.getAclObjectIdentity().getId(),
                entity.getAclObjectIdentity().getObjectIdIdentity(),
                entity.getSid().getId(),
                entity.getSid().getSid(),
                entity.getAceOrder(),
                entity.getMask(),
                entity.isGranting(),
                entity.isAuditSuccess(),
                entity.isAuditFailure());
    }

    private int nextAceOrder(AclObjectIdentityEntity objectIdentity) {
        Integer max = entryRepository.findMaxAceOrderByAclObjectIdentity_Id(objectIdentity.getId());
        return (max == null) ? 0 : max + 1;
    }
}
