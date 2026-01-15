package studio.one.base.security.acl.policy;

import java.util.List;

/**
 * A simple service that can synchronize individual ACL policy descriptors into
 * the Spring Security ACL tables.
 */
public interface AclPolicySynchronizationService {

    /**
     * Synchronizes the provided descriptor. Implementations MUST be idempotent.
     *
     * @param descriptor descriptor containing domain/component/role data
     */
    void synchronize(AclPolicyDescriptor descriptor);

    /**
     * Synchronizes the provided descriptors in bulk. Implementations MUST be idempotent.
     *
     * @param descriptors descriptors containing domain/component/role data
     */
    default void synchronizeAll(List<AclPolicyDescriptor> descriptors) {
        if (descriptors == null) {
            return;
        }
        descriptors.forEach(this::synchronize);
    }

}
