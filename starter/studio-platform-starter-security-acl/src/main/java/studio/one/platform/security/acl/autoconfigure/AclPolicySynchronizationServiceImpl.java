package studio.one.platform.security.acl.autoconfigure;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import studio.one.base.security.acl.policy.AclPolicyDescriptor;
import studio.one.base.security.acl.policy.AclPolicySynchronizationService;

/**
 * Default implementation that delegates descriptor synchronization to the
 * {@link AclPolicySeeder}.
 */
@Service
@RequiredArgsConstructor
public class AclPolicySynchronizationServiceImpl implements AclPolicySynchronizationService {

    private final AclPolicySeeder seeder;

    @Override
    public void synchronize(AclPolicyDescriptor descriptor) {
        seeder.apply(descriptor);
    }
}
