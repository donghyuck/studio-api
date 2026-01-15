package studio.one.platform.security.authz.acl;

import java.io.Serializable;

import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.acls.model.Sid;

public interface AclPermissionService {

    MutableAcl grantPermission(Class<?> domainType, Serializable identifier, Sid sid, Permission permission);

    MutableAcl grantPermission(ObjectIdentity identity, Sid sid, Permission permission);

    MutableAcl revokePermission(Class<?> domainType, Serializable identifier, Sid sid, Permission permission);

    MutableAcl revokePermission(ObjectIdentity identity, Sid sid, Permission permission);

    void deleteAcl(Class<?> domainType, Serializable identifier);

    void deleteAcl(ObjectIdentity identity);
}
