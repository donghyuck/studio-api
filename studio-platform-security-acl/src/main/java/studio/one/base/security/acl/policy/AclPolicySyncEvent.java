package studio.one.base.security.acl.policy;

/**
 * Lightweight event that can be published to trigger ACL policy synchronization
 * for one descriptor.
 */
public class AclPolicySyncEvent {

    private final AclPolicyDescriptor descriptor;

    public AclPolicySyncEvent(AclPolicyDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public AclPolicyDescriptor getDescriptor() {
        return descriptor;
    }
}
