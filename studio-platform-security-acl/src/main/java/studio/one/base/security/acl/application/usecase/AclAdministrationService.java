package studio.one.base.security.acl.application.usecase;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.domain.model.AclClassEntity;
import studio.one.base.security.acl.domain.model.AclEntryEntity;
import studio.one.base.security.acl.domain.model.AclObjectIdentityEntity;
import studio.one.base.security.acl.domain.model.AclSidEntity;
import studio.one.base.security.acl.domain.port.AclClassRepository;
import studio.one.base.security.acl.domain.port.AclEntryRepository;
import studio.one.base.security.acl.domain.port.AclObjectIdentityRepository;
import studio.one.base.security.acl.domain.port.AclSidRepository;
import studio.one.base.security.acl.application.command.AclClassCommand;
import studio.one.base.security.acl.application.command.AclEntryCommand;
import studio.one.base.security.acl.application.command.AclObjectIdentityCommand;
import studio.one.base.security.acl.application.command.AclSidCommand;
import studio.one.base.security.acl.application.policy.AclPolicyRefreshPublisher;
import studio.one.base.security.acl.application.result.AclClassResult;
import studio.one.base.security.acl.application.result.AclEntryResult;
import studio.one.base.security.acl.application.result.AclObjectIdentityResult;
import studio.one.base.security.acl.application.result.AclSidResult;
import studio.one.platform.security.acl.AclMetricsRecorder;

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

    public List<AclClassResult> listClasses() {
        return classRepository.findAll().stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    public AclClassResult createClass(AclClassCommand request) {
        AclClassEntity entity = new AclClassEntity();
        entity.setClassName(request.getClassName().trim());
        return toDto(classRepository.save(entity));
    }

    public void deleteClass(Long id) {
        classRepository.deleteById(id);
    }

    public List<AclSidResult> listSids() {
        return sidRepository.findAll().stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    public AclSidResult createSid(AclSidCommand request) {
        AclSidEntity entity = new AclSidEntity();
        entity.setSid(request.getSid().trim());
        entity.setPrincipal(request.isPrincipal());
        return toDto(sidRepository.save(entity));
    }

    public void deleteSid(Long id) {
        sidRepository.deleteById(id);
    }

    public List<AclObjectIdentityResult> listObjectIdentities() {
        return objectIdentityRepository.findAll().stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    public AclObjectIdentityResult createObjectIdentity(AclObjectIdentityCommand request) {
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

    public List<AclEntryResult> listEntries() {
        return entryRepository.findAll().stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    public AclEntryResult createEntry(AclEntryCommand request) {
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
        AclEntryResult dto = toDto(entryRepository.save(entry));
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

    private AclClassResult toDto(AclClassEntity entity) {
        return AclClassResult.builder().id(entity.getId()).className(entity.getClassName()).build();
    }

    private AclSidResult toDto(AclSidEntity entity) {
        return AclSidResult.builder().id(entity.getId()).principal(entity.isPrincipal()).sid(entity.getSid()).build();
    }

    private AclObjectIdentityResult toDto(AclObjectIdentityEntity entity) {
        return AclObjectIdentityResult.builder()
                .id(entity.getId())
                .classId(entity.getAclClass().getId())
                .className(entity.getAclClass().getClassName())
                .objectIdentity(entity.getObjectIdIdentity())
                .parentId((entity.getParent() != null) ? entity.getParent().getId() : null)
                .ownerSidId((entity.getOwnerSid() != null) ? entity.getOwnerSid().getId() : null)
                .entriesInheriting(entity.isEntriesInheriting())
                .build();
    }

    private AclEntryResult toDto(AclEntryEntity entity) {
        return AclEntryResult.builder()
                .id(entity.getId())
                .objectIdentityId(entity.getAclObjectIdentity().getId())
                .objectIdentity(entity.getAclObjectIdentity().getObjectIdIdentity())
                .sidId(entity.getSid().getId())
                .sid(entity.getSid().getSid())
                .aceOrder(entity.getAceOrder())
                .mask(entity.getMask())
                .granting(entity.isGranting())
                .auditSuccess(entity.isAuditSuccess())
                .auditFailure(entity.isAuditFailure())
                .build();
    }

    private int nextAceOrder(AclObjectIdentityEntity objectIdentity) {
        Integer max = entryRepository.findMaxAceOrderByAclObjectIdentity_Id(objectIdentity.getId());
        return (max == null) ? 0 : max + 1;
    }
}
