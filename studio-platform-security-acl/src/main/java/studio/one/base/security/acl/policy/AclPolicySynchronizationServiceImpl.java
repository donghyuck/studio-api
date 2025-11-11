package studio.one.base.security.acl.policy;

import lombok.RequiredArgsConstructor;

/**
 * Default implementation that delegates descriptor synchronization to the seeder.
 */
@RequiredArgsConstructor
public class AclPolicySynchronizationServiceImpl implements AclPolicySynchronizationService {

    private final AclPolicySeeder seeder;

    @Override
    public void synchronize(AclPolicyDescriptor descriptor) {
        seeder.apply(descriptor);
    }
}
