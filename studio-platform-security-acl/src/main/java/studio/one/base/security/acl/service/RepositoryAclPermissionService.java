package studio.one.base.security.acl.service;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.Sid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.domain.entity.AclClassEntity;
import studio.one.base.security.acl.domain.entity.AclEntryEntity;
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
 * Repository-backed implementation that does not require MutableAclService.
 */
@Slf4j
@Transactional
public class RepositoryAclPermissionService implements AclPermissionService {

    private final AclClassRepository classRepository;
    private final AclSidRepository sidRepository;
    private final AclObjectIdentityRepository objectIdentityRepository;
    private final AclEntryRepository entryRepository;
    private final AclPolicyRefreshPublisher refreshPublisher;
    private final AclMetricsRecorder metricsRecorder;
    private final boolean auditEnabled;

    public RepositoryAclPermissionService(
            AclClassRepository classRepository,
            AclSidRepository sidRepository,
            AclObjectIdentityRepository objectIdentityRepository,
        AclEntryRepository entryRepository,
        AclPolicyRefreshPublisher refreshPublisher,
        AclMetricsRecorder metricsRecorder,
        boolean auditEnabled) {
        Assert.notNull(classRepository, "classRepository must not be null");
        Assert.notNull(sidRepository, "sidRepository must not be null");
        Assert.notNull(objectIdentityRepository, "objectIdentityRepository must not be null");
        Assert.notNull(entryRepository, "entryRepository must not be null");
        this.classRepository = classRepository;
        this.sidRepository = sidRepository;
        this.objectIdentityRepository = objectIdentityRepository;
        this.entryRepository = entryRepository;
        this.refreshPublisher = refreshPublisher;
        this.metricsRecorder = (metricsRecorder != null) ? metricsRecorder : AclMetricsRecorder.noop();
        this.auditEnabled = auditEnabled;
    }

    @Override
    public MutableAcl grantPermission(Class<?> domainType, Serializable identifier, Sid sid, Permission permission) {
        Assert.notNull(domainType, "domainType must not be null");
        Assert.notNull(identifier, "identifier must not be null");
        ObjectIdentity identity = new org.springframework.security.acls.domain.ObjectIdentityImpl(domainType, identifier);
        return grantPermission(identity, sid, permission);
    }

    @Override
    public MutableAcl grantPermission(ObjectIdentity identity, Sid sid, Permission permission) {
        validateIdentity(identity);
        validateSid(sid);
        validatePermission(permission);

        long started = System.nanoTime();
        AclObjectIdentityEntity objectIdentity = ensureObjectIdentity(identity);
        AclSidEntity sidEntity = ensureSid(sid);
        List<Integer> existing = entryRepository.findMasksByObjectIdentityAndSid(
                objectIdentity, sidEntity, List.of(permission.getMask()));
        if (!existing.isEmpty()) {
            metricsRecorder.record("grant", elapsed(started), 0);
            return buildAcl(objectIdentity);
        }

        int aceOrder = nextAceOrder(objectIdentity);
        AclEntryEntity entry = toEntry(objectIdentity, sidEntity, aceOrder, permission.getMask());
        entryRepository.save(entry);
        publishRefresh();
        metricsRecorder.record("grant", elapsed(started), 1);
        audit("grant", identity, sid, List.of(permission.getMask()), 1);
        return buildAcl(objectIdentity);
    }

    @Override
    public MutableAcl revokePermission(Class<?> domainType, Serializable identifier, Sid sid, Permission permission) {
        Assert.notNull(domainType, "domainType must not be null");
        Assert.notNull(identifier, "identifier must not be null");
        ObjectIdentity identity = new org.springframework.security.acls.domain.ObjectIdentityImpl(domainType, identifier);
        return revokePermission(identity, sid, permission);
    }

    @Override
    public MutableAcl revokePermission(ObjectIdentity identity, Sid sid, Permission permission) {
        validateIdentity(identity);
        validateSid(sid);
        validatePermission(permission);

        long started = System.nanoTime();
        Optional<AclObjectIdentityEntity> objectIdentity = resolveObjectIdentity(identity);
        Optional<AclSidEntity> sidEntity = resolveSid(sid);
        if (objectIdentity.isEmpty() || sidEntity.isEmpty()) {
            metricsRecorder.record("revoke", elapsed(started), 0);
            return null;
        }
        int deleted = entryRepository.deleteByObjectIdentityAndSidAndMaskIn(
                objectIdentity.get(), sidEntity.get(), List.of(permission.getMask()));
        if (deleted > 0) {
            publishRefresh();
        }
        metricsRecorder.record("revoke", elapsed(started), deleted);
        audit("revoke", identity, sid, List.of(permission.getMask()), deleted);
        return buildAcl(objectIdentity.get());
    }

    @Override
    public int grantPermissions(ObjectIdentity identity, Sid sid, Collection<? extends Permission> permissions) {
        validateIdentity(identity);
        validateSid(sid);
        validatePermissions(permissions);

        long started = System.nanoTime();
        AclObjectIdentityEntity objectIdentity = ensureObjectIdentity(identity);
        AclSidEntity sidEntity = ensureSid(sid);
        List<Integer> requestedMasks = permissions.stream()
                .map(Permission::getMask)
                .distinct()
                .toList();
        if (requestedMasks.isEmpty()) {
            metricsRecorder.record("bulk_grant", elapsed(started), 0);
            return 0;
        }
        Set<Integer> existing = Set.copyOf(entryRepository.findMasksByObjectIdentityAndSid(
                objectIdentity, sidEntity, requestedMasks));

        int nextAceOrder = nextAceOrder(objectIdentity);
        List<AclEntryEntity> entries = new ArrayList<>();
        for (Integer mask : requestedMasks) {
            if (existing.contains(mask)) {
                continue;
            }
            entries.add(toEntry(objectIdentity, sidEntity, nextAceOrder++, mask));
        }
        if (entries.isEmpty()) {
            metricsRecorder.record("bulk_grant", elapsed(started), 0);
            return 0;
        }
        entryRepository.saveAll(entries);
        publishRefresh();
        metricsRecorder.record("bulk_grant", elapsed(started), entries.size());
        audit("bulk_grant", identity, sid, entries.stream().map(AclEntryEntity::getMask).toList(), entries.size());
        return entries.size();
    }

    @Override
    public int revokePermissions(ObjectIdentity identity, Sid sid, Collection<? extends Permission> permissions) {
        validateIdentity(identity);
        validateSid(sid);
        validatePermissions(permissions);

        long started = System.nanoTime();
        Optional<AclObjectIdentityEntity> objectIdentity = resolveObjectIdentity(identity);
        Optional<AclSidEntity> sidEntity = resolveSid(sid);
        if (objectIdentity.isEmpty() || sidEntity.isEmpty()) {
            metricsRecorder.record("bulk_revoke", elapsed(started), 0);
            return 0;
        }

        List<Integer> masks = permissions.stream()
                .map(Permission::getMask)
                .distinct()
                .toList();
        int deleted = entryRepository.deleteByObjectIdentityAndSidAndMaskIn(
                objectIdentity.get(), sidEntity.get(), masks);
        if (deleted > 0) {
            publishRefresh();
        }
        metricsRecorder.record("bulk_revoke", elapsed(started), deleted);
        audit("bulk_revoke", identity, sid, masks, deleted);
        return deleted;
    }

    @Override
    public void deleteAcl(Class<?> domainType, Serializable identifier) {
        Assert.notNull(domainType, "domainType must not be null");
        Assert.notNull(identifier, "identifier must not be null");
        ObjectIdentity identity = new org.springframework.security.acls.domain.ObjectIdentityImpl(domainType, identifier);
        deleteAcl(identity);
    }

    @Override
    public void deleteAcl(ObjectIdentity identity) {
        validateIdentity(identity);
        long started = System.nanoTime();
        Optional<AclObjectIdentityEntity> objectIdentity = resolveObjectIdentity(identity);
        if (objectIdentity.isEmpty()) {
            metricsRecorder.record("delete", elapsed(started), 0);
            return;
        }
        objectIdentityRepository.delete(objectIdentity.get());
        publishRefresh();
        metricsRecorder.record("delete", elapsed(started), 1);
        audit("delete", identity, null, List.of(), 1);
    }

    @Override
    public List<AccessControlEntry> listPermissions(ObjectIdentity identity) {
        validateIdentity(identity);
        long started = System.nanoTime();
        Optional<AclObjectIdentityEntity> objectIdentity = resolveObjectIdentity(identity);
        if (objectIdentity.isEmpty()) {
            metricsRecorder.record("list", elapsed(started), 0);
            return List.of();
        }
        List<AclEntryEntity> entries = entryRepository.findByAclObjectIdentity_IdOrderByAceOrderAsc(
                objectIdentity.get().getId());
        List<AccessControlEntry> result = buildEntries(entries, identity);
        metricsRecorder.record("list", elapsed(started), result.size());
        return result;
    }

    private AclObjectIdentityEntity ensureObjectIdentity(ObjectIdentity identity) {
        return resolveObjectIdentity(identity).orElseGet(() -> {
            AclClassEntity clazz = ensureClass(identity.getType());
            AclObjectIdentityEntity entity = new AclObjectIdentityEntity();
            entity.setAclClass(clazz);
            entity.setObjectIdIdentity(String.valueOf(identity.getIdentifier()));
            entity.setEntriesInheriting(true);
            return objectIdentityRepository.save(entity);
        });
    }

    private Optional<AclObjectIdentityEntity> resolveObjectIdentity(ObjectIdentity identity) {
        Optional<AclClassEntity> clazz = classRepository.findByClassName(identity.getType());
        if (clazz.isEmpty()) {
            return Optional.empty();
        }
        return objectIdentityRepository.findByAclClass_IdAndObjectIdIdentity(
                clazz.get().getId(), String.valueOf(identity.getIdentifier()));
    }

    private AclClassEntity ensureClass(String className) {
        String normalized = className.trim();
        return classRepository.findByClassName(normalized)
                .orElseGet(() -> {
                    AclClassEntity entity = new AclClassEntity();
                    entity.setClassName(normalized);
                    return classRepository.save(entity);
                });
    }

    private AclSidEntity ensureSid(Sid sid) {
        return resolveSid(sid).orElseGet(() -> {
            AclSidEntity entity = new AclSidEntity();
            entity.setPrincipal(isPrincipalSid(sid));
            entity.setSid(sidValue(sid));
            return sidRepository.save(entity);
        });
    }

    private Optional<AclSidEntity> resolveSid(Sid sid) {
        String value = sidValue(sid);
        boolean principal = isPrincipalSid(sid);
        return sidRepository.findBySidAndPrincipal(value, principal);
    }

    private String sidValue(Sid sid) {
        if (sid instanceof PrincipalSid principalSid) {
            return principalSid.getPrincipal();
        }
        if (sid instanceof GrantedAuthoritySid authoritySid) {
            return authoritySid.getGrantedAuthority();
        }
        return sid.toString();
    }

    private boolean isPrincipalSid(Sid sid) {
        return sid instanceof PrincipalSid;
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

    private List<AccessControlEntry> buildEntries(List<AclEntryEntity> entries, ObjectIdentity identity) {
        SimpleMutableAcl acl = new SimpleMutableAcl(identity, entriesInheriting(entries));
        List<AccessControlEntry> list = new ArrayList<>();
        for (AclEntryEntity entry : entries) {
            Permission permission = new MaskPermission(entry.getMask());
            Sid sid = entry.getSid().isPrincipal()
                    ? new PrincipalSid(entry.getSid().getSid())
                    : new GrantedAuthoritySid(entry.getSid().getSid());
            list.add(new SimpleAce(
                    entry.getId(),
                    acl,
                    sid,
                    permission,
                    entry.isGranting(),
                    entry.isAuditSuccess(),
                    entry.isAuditFailure()));
        }
        acl.setEntries(list);
        return list;
    }

    private MutableAcl buildAcl(AclObjectIdentityEntity objectIdentity) {
        ObjectIdentity identity = new org.springframework.security.acls.domain.ObjectIdentityImpl(
                objectIdentity.getAclClass().getClassName(),
                objectIdentity.getObjectIdIdentity());
        List<AclEntryEntity> entries = entryRepository.findByAclObjectIdentity_IdOrderByAceOrderAsc(
                objectIdentity.getId());
        SimpleMutableAcl acl = new SimpleMutableAcl(identity, objectIdentity.isEntriesInheriting());
        acl.setEntries(buildEntries(entries, identity));
        return acl;
    }

    private boolean entriesInheriting(List<AclEntryEntity> entries) {
        for (AclEntryEntity entry : entries) {
            if (entry.getAclObjectIdentity() != null) {
                return entry.getAclObjectIdentity().isEntriesInheriting();
            }
        }
        return true;
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

    private void audit(String action, ObjectIdentity identity, Sid sid, List<Integer> masks, int count) {
        if (!auditEnabled || !log.isInfoEnabled()) {
            return;
        }
        String sidValue = (sid != null) ? sid.toString() : "none";
        String maskValue = masks.isEmpty() ? "none" : masks.toString().toLowerCase(Locale.ROOT);
        log.info("ACL_AUDIT action={} identity={} sid={} masks={} count={}", action, identity, sidValue, maskValue, count);
    }

    private void publishRefresh() {
        if (refreshPublisher != null) {
            refreshPublisher.publishAfterCommit();
        }
    }

    private Duration elapsed(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static class MaskPermission implements Permission {
        private final int mask;

        private MaskPermission(int mask) {
            this.mask = mask;
        }

        @Override
        public int getMask() {
            return mask;
        }

        @Override
        public String getPattern() {
            return Integer.toString(mask);
        }
    }

    private static class SimpleMutableAcl implements MutableAcl {
        private final ObjectIdentity objectIdentity;
        private boolean entriesInheriting;
        private List<AccessControlEntry> entries = new ArrayList<>();

        private SimpleMutableAcl(ObjectIdentity objectIdentity, boolean entriesInheriting) {
            this.objectIdentity = objectIdentity;
            this.entriesInheriting = entriesInheriting;
        }

        private void setEntries(List<AccessControlEntry> entries) {
            this.entries = new ArrayList<>(entries);
        }

        @Override
        public Serializable getId() {
            return null;
        }

        @Override
        public ObjectIdentity getObjectIdentity() {
            return objectIdentity;
        }

        @Override
        public List<AccessControlEntry> getEntries() {
            return entries;
        }

        @Override
        public Sid getOwner() {
            return null;
        }

        @Override
        public boolean isEntriesInheriting() {
            return entriesInheriting;
        }

        @Override
        public org.springframework.security.acls.model.Acl getParentAcl() {
            return null;
        }

        @Override
        public boolean isGranted(List<Permission> permission, List<Sid> sids, boolean administrativeMode) {
            for (AccessControlEntry entry : entries) {
                if (!entry.isGranting()) {
                    continue;
                }
                if (!sids.contains(entry.getSid())) {
                    continue;
                }
                for (Permission p : permission) {
                    if (p.getMask() == entry.getPermission().getMask()) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean isSidLoaded(List<Sid> sids) {
            return true;
        }

        @Override
        public void setParent(org.springframework.security.acls.model.Acl acl) {
            throw new UnsupportedOperationException("Parent ACL not supported");
        }

        @Override
        public void setEntriesInheriting(boolean entriesInheriting) {
            this.entriesInheriting = entriesInheriting;
        }

        @Override
        public void setOwner(Sid owner) {
            throw new UnsupportedOperationException("Owner not supported");
        }

        @Override
        public void insertAce(int atIndexLocation, Permission permission, Sid sid, boolean granting) {
            throw new UnsupportedOperationException("ACE insert not supported");
        }

        @Override
        public void deleteAce(int aceIndex) {
            throw new UnsupportedOperationException("ACE delete not supported");
        }

        @Override
        public void updateAce(int aceIndex, Permission permission) {
            throw new UnsupportedOperationException("ACE update not supported");
        }
    }

    private static class SimpleAce implements AccessControlEntry {
        private final Serializable id;
        private final org.springframework.security.acls.model.Acl acl;
        private final Sid sid;
        private final Permission permission;
        private final boolean granting;
        private final boolean auditSuccess;
        private final boolean auditFailure;

        private SimpleAce(Serializable id, org.springframework.security.acls.model.Acl acl, Sid sid, Permission permission,
                boolean granting, boolean auditSuccess, boolean auditFailure) {
            this.id = id;
            this.acl = acl;
            this.sid = sid;
            this.permission = permission;
            this.granting = granting;
            this.auditSuccess = auditSuccess;
            this.auditFailure = auditFailure;
        }

        @Override
        public Serializable getId() {
            return id;
        }

        @Override
        public org.springframework.security.acls.model.Acl getAcl() {
            return acl;
        }

        @Override
        public Sid getSid() {
            return sid;
        }

        @Override
        public Permission getPermission() {
            return permission;
        }

        @Override
        public boolean isGranting() {
            return granting;
        }

        public boolean isAuditSuccess() {
            return auditSuccess;
        }

        public boolean isAuditFailure() {
            return auditFailure;
        }
    }
}
