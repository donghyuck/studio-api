package studio.one.base.security.acl.service;

import java.io.Serializable;

import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.Sid;
import org.springframework.util.Assert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.security.authz.acl.AclPermissionService;

/**
 * Convenience service for managing object level ACL entries.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultAclPermissionService implements AclPermissionService {

    private final MutableAclService aclService;

    @Override
    public MutableAcl grantPermission(Class<?> domainType, Serializable identifier, Sid sid, Permission permission) {
        ObjectIdentity identity = new ObjectIdentityImpl(domainType, identifier);
        return grantPermission(identity, sid, permission);
    }

    @Override
    public MutableAcl grantPermission(ObjectIdentity identity, Sid sid, Permission permission) {
        Assert.notNull(identity, "identity must not be null");
        Assert.notNull(sid, "sid must not be null");
        Assert.notNull(permission, "permission must not be null");

        MutableAcl acl = findOrCreate(identity);
        acl.insertAce(acl.getEntries().size(), permission, sid, true);
        MutableAcl updated = aclService.updateAcl(acl);
        log.debug("Granted permission {} on {} to {}", permission, identity, sid);
        return updated;
    }

    @Override
    public MutableAcl revokePermission(Class<?> domainType, Serializable identifier, Sid sid, Permission permission) {
        ObjectIdentity identity = new ObjectIdentityImpl(domainType, identifier);
        return revokePermission(identity, sid, permission);
    }

    @Override
    public MutableAcl revokePermission(ObjectIdentity identity, Sid sid, Permission permission) {
        Assert.notNull(identity, "identity must not be null");
        Assert.notNull(sid, "sid must not be null");
        Assert.notNull(permission, "permission must not be null");

        MutableAcl acl = find(identity);
        if (acl == null) {
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
            log.debug("Revoked permission {} on {} from {}", permission, identity, sid);
            return updated;
        }
        return acl;
    }

    @Override
    public void deleteAcl(Class<?> domainType, Serializable identifier) {
        ObjectIdentity identity = new ObjectIdentityImpl(domainType, identifier);
        deleteAcl(identity);
    }

    @Override
    public void deleteAcl(ObjectIdentity identity) {
        Assert.notNull(identity, "identity must not be null");
        aclService.deleteAcl(identity, true);
        log.debug("Deleted ACL {}", identity);
    }

    public static Sid roleSid(String role) {
        Assert.hasText(role, "role must not be empty");
        return new GrantedAuthoritySid(role);
    }

    public static Sid userSid(String username) {
        Assert.hasText(username, "username must not be empty");
        return new PrincipalSid(username);
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
}
