package studio.one.base.security.acl.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
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

/**
 * Service that exposes ACL metadata management operations.
 */
@RequiredArgsConstructor
public class AclAdministrationService {

    private final AclClassRepository classRepository;
    private final AclSidRepository sidRepository;
    private final AclObjectIdentityRepository objectIdentityRepository;
    private final AclEntryRepository entryRepository;

    public List<AclClassDto> listClasses() {
        return classRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public AclClassDto createClass(AclClassRequest request) {
        AclClassEntity entity = new AclClassEntity();
        entity.setClassName(request.getClassName().trim());
        return toDto(classRepository.save(entity));
    }

    public List<AclSidDto> listSids() {
        return sidRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public AclSidDto createSid(AclSidRequest request) {
        AclSidEntity entity = new AclSidEntity();
        entity.setSid(request.getSid().trim());
        entity.setPrincipal(request.isPrincipal());
        return toDto(sidRepository.save(entity));
    }

    public List<AclObjectIdentityDto> listObjectIdentities() {
        return objectIdentityRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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

    public List<AclEntryDto> listEntries() {
        return entryRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public AclEntryDto createEntry(AclEntryRequest request) {
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
        return toDto(entryRepository.save(entry));
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
