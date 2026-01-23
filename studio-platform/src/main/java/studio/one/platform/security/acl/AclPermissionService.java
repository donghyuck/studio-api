package studio.one.platform.security.acl;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.Sid;
import org.springframework.util.Assert;

import studio.one.platform.constant.ServiceNames;

public interface AclPermissionService {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":security:acl:acl-permission-service";

    MutableAcl grantPermission(Class<?> domainType, Serializable identifier, Sid sid, Permission permission);

    MutableAcl grantPermission(ObjectIdentity identity, Sid sid, Permission permission);

    default int grantPermissions(Class<?> domainType, Serializable identifier, Sid sid, Collection<? extends Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Permission permission : permissions) {
            MutableAcl acl = grantPermission(domainType, identifier, sid, permission);
            if (acl != null) {
                count++;
            }
        }
        return count;
    }

    default int grantPermissions(ObjectIdentity identity, Sid sid, Collection<? extends Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Permission permission : permissions) {
            MutableAcl acl = grantPermission(identity, sid, permission);
            if (acl != null) {
                count++;
            }
        }
        return count;
    }

    MutableAcl revokePermission(Class<?> domainType, Serializable identifier, Sid sid, Permission permission);

    MutableAcl revokePermission(ObjectIdentity identity, Sid sid, Permission permission);

    List<AccessControlEntry> listPermissions(ObjectIdentity identity);

    default List<AccessControlEntry> listPermissions(Class<?> domainType, Serializable identifier) {
        if (domainType == null || identifier == null) {
            return List.of();
        }
        ObjectIdentity identity = new org.springframework.security.acls.domain.ObjectIdentityImpl(domainType, identifier);
        return listPermissions(identity);
    }

    default int revokePermissions(Class<?> domainType, Serializable identifier, Sid sid, Collection<? extends Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Permission permission : permissions) {
            MutableAcl acl = revokePermission(domainType, identifier, sid, permission);
            if (acl != null) {
                count++;
            }
        }
        return count;
    }

    default int revokePermissions(ObjectIdentity identity, Sid sid, Collection<? extends Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Permission permission : permissions) {
            MutableAcl acl = revokePermission(identity, sid, permission);
            if (acl != null) {
                count++;
            }
        }
        return count;
    }

    void deleteAcl(Class<?> domainType, Serializable identifier);

    void deleteAcl(ObjectIdentity identity);

    static Sid roleSid(String role) {
        Assert.hasText(role, "role must not be empty");
        return new GrantedAuthoritySid(role);
    }

    static Sid userSid(String username) {
        Assert.hasText(username, "username must not be empty");
        return new PrincipalSid(username);
    }
}
