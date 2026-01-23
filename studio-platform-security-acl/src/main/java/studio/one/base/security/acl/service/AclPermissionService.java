package studio.one.base.security.acl.service;

import org.springframework.security.acls.model.MutableAclService;
import org.springframework.security.acls.model.Sid;

/**
 * @deprecated Use {@link studio.one.platform.security.acl.AclPermissionService} instead.
 */
@Deprecated(forRemoval = false, since = "1.0")
public class AclPermissionService extends DefaultAclPermissionService {

    public AclPermissionService(MutableAclService aclService) {
        super(aclService);
    }

    public static Sid roleSid(String role) {
        return studio.one.platform.security.acl.AclPermissionService.roleSid(role);
    }

    public static Sid userSid(String username) {
        return studio.one.platform.security.acl.AclPermissionService.userSid(username);
    }
}
