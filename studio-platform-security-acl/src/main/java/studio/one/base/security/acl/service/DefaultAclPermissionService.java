package studio.one.base.security.acl.service;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.Sid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.domain.entity.AclEntryEntity;
import studio.one.base.security.acl.domain.entity.AclClassEntity;
import studio.one.base.security.acl.domain.entity.AclObjectIdentityEntity;
import studio.one.base.security.acl.domain.entity.AclSidEntity;
import studio.one.base.security.acl.persistence.AclClassRepository;
import studio.one.base.security.acl.persistence.AclEntryRepository;
import studio.one.base.security.acl.persistence.AclObjectIdentityRepository;
import studio.one.base.security.acl.persistence.AclSidRepository;
import studio.one.base.security.acl.policy.AclPolicyRefreshPublisher;
import studio.one.platform.security.acl.AclMetricsRecorder;
import studio.one.platform.security.acl.AclPermissionService;

/**
 * Convenience service for managing object level ACL entries.
 */
@Slf4j
@Transactional
public class DefaultAclPermissionService implements AclPermissionService {

    private final MutableAclService aclService;
    private final AclMetricsRecorder metricsRecorder;
    private final AclPolicyRefreshPublisher refreshPublisher;
    private final AclEntryRepository entryRepository;
    private final AclObjectIdentityRepository objectIdentityRepository;
    private final AclClassRepository classRepository;
    private final AclSidRepository sidRepository;
    private final boolean auditEnabled;

    public DefaultAclPermissionService(MutableAclService aclService) {
        this(aclService, AclMetricsRecorder.noop(), null, null, null, null, null, true);
    }

    public DefaultAclPermissionService(
            MutableAclService aclService,
            AclMetricsRecorder metricsRecorder,
            AclPolicyRefreshPublisher refreshPublisher,
            AclEntryRepository entryRepository,
            AclObjectIdentityRepository objectIdentityRepository,
            AclClassRepository classRepository,
            AclSidRepository sidRepository,
            boolean auditEnabled) {
        Assert.notNull(aclService, "aclService must not be null");
        this.aclService = aclService;
        this.metricsRecorder = (metricsRecorder != null) ? metricsRecorder : AclMetricsRecorder.noop();
        this.refreshPublisher = refreshPublisher;
        this.entryRepository = entryRepository;
        this.objectIdentityRepository = objectIdentityRepository;
        this.classRepository = classRepository;
        this.sidRepository = sidRepository;
        this.auditEnabled = auditEnabled;
    }

    @Override
    public MutableAcl grantPermission(Class<?> domainType, Serializable identifier, Sid sid, Permission permission) {
        Assert.notNull(domainType, "domainType must not be null");
        Assert.notNull(identifier, "identifier must not be null");
        ObjectIdentity identity = new ObjectIdentityImpl(domainType, identifier);
        return grantPermission(identity, sid, permission);
    }

    @Override
    public MutableAcl grantPermission(ObjectIdentity identity, Sid sid, Permission permission) {
        validateIdentity(identity);
        validateSid(sid);
        validatePermission(permission);

        long started = System.nanoTime();
        MutableAcl acl = findOrCreate(identity);
        if (hasAce(acl, sid, permission)) {
            log.debug("Permission {} on {} already granted to {}", permission, identity, sid);
            metricsRecorder.record("grant", elapsed(started), 0);
            return acl;
        }
        acl.insertAce(acl.getEntries().size(), permission, sid, true);
        MutableAcl updated = aclService.updateAcl(acl);
        publishRefresh();
        metricsRecorder.record("grant", elapsed(started), 1);
        if (auditEnabled && log.isInfoEnabled()) {
            log.info("ACL_AUDIT action=grant identity={} sid={} mask={}", identity, sid, permission.getMask());
        }
        return updated;
    }

    @Override
    public int grantPermissions(ObjectIdentity identity, Sid sid, Collection<? extends Permission> permissions) {
        validateIdentity(identity);
        validateSid(sid);
        validatePermissions(permissions);

        if (!hasBulkRepositories()) {
            return AclPermissionService.super.grantPermissions(identity, sid, permissions);
        }

        long started = System.nanoTime();
        Optional<AclObjectIdentityEntity> objectIdentity = resolveObjectIdentity(identity);
        Optional<AclSidEntity> sidEntity = resolveSid(sid);
        if (objectIdentity.isEmpty() || sidEntity.isEmpty()) {
            return AclPermissionService.super.grantPermissions(identity, sid, permissions);
        }

        List<Integer> requestedMasks = permissions.stream()
                .map(Permission::getMask)
                .distinct()
                .toList();
        if (requestedMasks.isEmpty()) {
            metricsRecorder.record("bulk_grant", elapsed(started), 0);
            return 0;
        }

        List<Integer> existingMasks = entryRepository.findMasksByObjectIdentityAndSid(
                objectIdentity.get(), sidEntity.get(), requestedMasks);
        Set<Integer> existing = Set.copyOf(existingMasks);

        int nextAceOrder = nextAceOrder(objectIdentity.get());
        List<AclEntryEntity> entries = new java.util.ArrayList<>();
        for (Integer mask : requestedMasks) {
            if (existing.contains(mask)) {
                continue;
            }
            entries.add(toEntry(objectIdentity.get(), sidEntity.get(), nextAceOrder, mask));
            nextAceOrder++;
        }

        if (entries.isEmpty()) {
            metricsRecorder.record("bulk_grant", elapsed(started), 0);
            return 0;
        }

        entryRepository.saveAll(entries);
        publishRefresh();
        metricsRecorder.record("bulk_grant", elapsed(started), entries.size());
        if (auditEnabled && log.isInfoEnabled()) {
            log.info("ACL_AUDIT action=bulk_grant identity={} sid={} masks={} added={}",
                    identity, sid, entries.stream().map(AclEntryEntity::getMask).toList(), entries.size());
        }
        return entries.size();
    }

    @Override
    public int grantPermissions(Class<?> domainType, Serializable identifier, Sid sid,
            Collection<? extends Permission> permissions) {
        Assert.notNull(domainType, "domainType must not be null");
        Assert.notNull(identifier, "identifier must not be null");
        ObjectIdentity identity = new ObjectIdentityImpl(domainType, identifier);
        return grantPermissions(identity, sid, permissions);
    }

    @Override
    public MutableAcl revokePermission(Class<?> domainType, Serializable identifier, Sid sid, Permission permission) {
        Assert.notNull(domainType, "domainType must not be null");
        Assert.notNull(identifier, "identifier must not be null");
        ObjectIdentity identity = new ObjectIdentityImpl(domainType, identifier);
        return revokePermission(identity, sid, permission);
    }

    @Override
    public MutableAcl revokePermission(ObjectIdentity identity, Sid sid, Permission permission) {
        validateIdentity(identity);
        validateSid(sid);
        validatePermission(permission);

        long started = System.nanoTime();
        MutableAcl acl = find(identity);
        if (acl == null) {
            log.warn("ACL revoke requested for missing identity {}", identity);
            metricsRecorder.record("revoke", elapsed(started), 0);
            return null;
        }

        boolean modified = false;
        for (int i = acl.getEntries().size() - 1; i >= 0; i--) {
            if (permission.equals(acl.getEntries().get(i).getPermission())
                    && sid.equals(acl.getEntries().get(i).getSid())) {
                acl.deleteAce(i);
                modified = true;
            }
        }
        if (modified) {
            MutableAcl updated = aclService.updateAcl(acl);
            publishRefresh();
            metricsRecorder.record("revoke", elapsed(started), 1);
            if (auditEnabled && log.isInfoEnabled()) {
                log.info("ACL_AUDIT action=revoke identity={} sid={} mask={}", identity, sid, permission.getMask());
            }
            return updated;
        }
        metricsRecorder.record("revoke", elapsed(started), 0);
        return acl;
    }

    @Override
    public List<AccessControlEntry> listPermissions(ObjectIdentity identity) {
        validateIdentity(identity);
        long started = System.nanoTime();
        try {
            Acl acl = aclService.readAclById(identity);
            List<AccessControlEntry> entries = (acl.getEntries() != null) ? acl.getEntries() : List.of();
            metricsRecorder.record("list", elapsed(started), entries.size());
            return entries;
        } catch (NotFoundException ex) {
            metricsRecorder.record("list", elapsed(started), 0);
            return List.of();
        }
    }

    @Override
    public int revokePermissions(ObjectIdentity identity, Sid sid, Collection<? extends Permission> permissions) {
        validateIdentity(identity);
        validateSid(sid);
        validatePermissions(permissions);

        if (!hasBulkRepositories()) {
            return AclPermissionService.super.revokePermissions(identity, sid, permissions);
        }

        long started = System.nanoTime();
        Optional<AclObjectIdentityEntity> objectIdentity = resolveObjectIdentity(identity);
        Optional<AclSidEntity> sidEntity = resolveSid(sid);
        if (objectIdentity.isEmpty() || sidEntity.isEmpty()) {
            log.warn("ACL bulk revoke requested for missing identity or sid: {} {}", identity, sid);
            metricsRecorder.record("bulk_revoke", elapsed(started), 0);
            return 0;
        }

        List<Integer> masks = permissions.stream()
                .map(Permission::getMask)
                .distinct()
                .toList();
        int deleted = entryRepository.deleteByObjectIdentityAndSidAndMaskIn(objectIdentity.get(), sidEntity.get(), masks);
        if (deleted > 0) {
            publishRefresh();
        }
        metricsRecorder.record("bulk_revoke", elapsed(started), deleted);
        if (auditEnabled && log.isInfoEnabled()) {
            log.info("ACL_AUDIT action=bulk_revoke identity={} sid={} masks={} deleted={}",
                    identity, sid, masks, deleted);
        }
        return deleted;
    }

    @Override
    public int revokePermissions(Class<?> domainType, Serializable identifier, Sid sid,
            Collection<? extends Permission> permissions) {
        Assert.notNull(domainType, "domainType must not be null");
        Assert.notNull(identifier, "identifier must not be null");
        ObjectIdentity identity = new ObjectIdentityImpl(domainType, identifier);
        return revokePermissions(identity, sid, permissions);
    }

    @Override
    public void deleteAcl(Class<?> domainType, Serializable identifier) {
        Assert.notNull(domainType, "domainType must not be null");
        Assert.notNull(identifier, "identifier must not be null");
        ObjectIdentity identity = new ObjectIdentityImpl(domainType, identifier);
        deleteAcl(identity);
    }

    @Override
    public void deleteAcl(ObjectIdentity identity) {
        validateIdentity(identity);
        long started = System.nanoTime();
        MutableAcl acl = find(identity);
        if (acl == null) {
            log.warn("ACL delete requested for missing identity {}", identity);
            metricsRecorder.record("delete", elapsed(started), 0);
            return;
        }
        aclService.deleteAcl(identity, true);
        publishRefresh();
        metricsRecorder.record("delete", elapsed(started), 1);
        if (auditEnabled && log.isInfoEnabled()) {
            log.info("ACL_AUDIT action=delete identity={}", identity);
        }
    }

    private MutableAcl findOrCreate(ObjectIdentity identity) {
        MutableAcl acl = find(identity);
        if (acl != null) {
            return acl;
        }
        return aclService.createAcl(identity);
    }

    private MutableAcl find(ObjectIdentity identity) {
        try {
            return (MutableAcl) aclService.readAclById(identity);
        } catch (NotFoundException ex) {
            return null;
        }
    }

    private boolean hasAce(MutableAcl acl, Sid sid, Permission permission) {
        if (acl == null || acl.getEntries() == null) {
            return false;
        }
        return acl.getEntries().stream()
                .anyMatch(ace -> ace.isGranting()
                        && permission.equals(ace.getPermission())
                        && sid.equals(ace.getSid()));
    }

    private void validateIdentity(ObjectIdentity identity) {
        Assert.notNull(identity, "identity must not be null");
        Assert.notNull(identity.getIdentifier(), "identity identifier must not be null");
        Assert.hasText(identity.getType(), "identity type must not be empty");
    }

    private void validateSid(Sid sid) {
        Assert.notNull(sid, "sid must not be null");
    }

    private void validatePermission(Permission permission) {
        Assert.notNull(permission, "permission must not be null");
        Assert.isTrue(permission.getMask() > 0, "permission mask must be positive");
    }

    private void validatePermissions(Collection<? extends Permission> permissions) {
        Assert.notNull(permissions, "permissions must not be null");
        Assert.isTrue(!permissions.isEmpty(), "permissions must not be empty");
        for (Permission permission : permissions) {
            validatePermission(permission);
        }
    }

    private boolean hasBulkRepositories() {
        return entryRepository != null
                && objectIdentityRepository != null
                && classRepository != null
                && sidRepository != null;
    }

    private Optional<AclObjectIdentityEntity> resolveObjectIdentity(ObjectIdentity identity) {
        if (!hasBulkRepositories()) {
            return Optional.empty();
        }
        String type = identity.getType();
        String objectId = String.valueOf(identity.getIdentifier());
        Optional<AclClassEntity> clazz = classRepository.findByClassName(type);
        if (clazz.isEmpty()) {
            return Optional.empty();
        }
        return objectIdentityRepository.findByAclClass_IdAndObjectIdIdentity(clazz.get().getId(), objectId);
    }

    private Optional<AclSidEntity> resolveSid(Sid sid) {
        if (sidRepository == null) {
            return Optional.empty();
        }
        if (sid instanceof PrincipalSid principalSid) {
            return sidRepository.findBySidAndPrincipal(principalSid.getPrincipal(), true);
        }
        if (sid instanceof GrantedAuthoritySid authoritySid) {
            return sidRepository.findBySidAndPrincipal(authoritySid.getGrantedAuthority(), false);
        }
        return Optional.empty();
    }

    private void publishRefresh() {
        if (refreshPublisher != null) {
            refreshPublisher.publishAfterCommit();
        }
    }

    private int nextAceOrder(AclObjectIdentityEntity objectIdentity) {
        Integer max = entryRepository.findMaxAceOrderByAclObjectIdentity_Id(objectIdentity.getId());
        return (max == null) ? 0 : max + 1;
    }

    private AclEntryEntity toEntry(AclObjectIdentityEntity objectIdentity, AclSidEntity sidEntity, int aceOrder, int mask) {
        AclEntryEntity entry = new AclEntryEntity();
        entry.setAclObjectIdentity(objectIdentity);
        entry.setSid(sidEntity);
        entry.setAceOrder(aceOrder);
        entry.setMask(mask);
        entry.setGranting(true);
        entry.setAuditSuccess(false);
        entry.setAuditFailure(false);
        return entry;
    }

    private Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }
}
