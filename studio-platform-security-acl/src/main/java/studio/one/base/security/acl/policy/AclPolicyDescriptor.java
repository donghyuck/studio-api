package studio.one.base.security.acl.policy;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Simple descriptor for a domain/component ACL policy that maps
 * {@code ROLE_*} names to one or more {@link AclAction}s.
 */
@Data
public class AclPolicyDescriptor {

    private String domain;
    private String component;
    private List<AclRolePolicy> roles = new ArrayList<>();

    @Data
    public static class AclRolePolicy {
        private String role;
        private List<AclAction> actions = List.of(AclAction.READ);
    }
}
