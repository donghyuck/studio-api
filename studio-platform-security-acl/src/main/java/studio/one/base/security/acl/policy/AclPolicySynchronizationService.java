package studio.one.base.security.acl.policy;

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

}
