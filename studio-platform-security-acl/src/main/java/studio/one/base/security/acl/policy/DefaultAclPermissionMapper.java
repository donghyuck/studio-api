package studio.one.base.security.acl.policy;

import java.util.EnumSet;

import org.springframework.security.acls.domain.BasePermission;

/**
 * Default implementation that maps Spring Security ACL base permissions to
 * read/write/admin actions.
 */
public class DefaultAclPermissionMapper implements AclPermissionMapper {

    @Override
    public EnumSet<AclAction> map(int mask) {
        EnumSet<AclAction> actions = EnumSet.noneOf(AclAction.class);

        if ((mask & BasePermission.ADMINISTRATION.getMask()) != 0) {
            actions.add(AclAction.ADMIN);
            actions.add(AclAction.WRITE);
            actions.add(AclAction.READ);
        }
        if ((mask & BasePermission.WRITE.getMask()) != 0
                || (mask & BasePermission.CREATE.getMask()) != 0
                || (mask & BasePermission.DELETE.getMask()) != 0) {
            actions.add(AclAction.WRITE);
        }
        if ((mask & BasePermission.READ.getMask()) != 0) {
            actions.add(AclAction.READ);
        }
        return actions;
    }
}
