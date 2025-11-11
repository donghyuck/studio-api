package studio.one.base.security.acl.policy;

import java.util.EnumSet;

/**
 * Maps an ACL permission mask to one or more normalized {@link AclAction} values.
 */
public interface AclPermissionMapper {

    EnumSet<AclAction> map(int mask);
}
