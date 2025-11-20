package studio.one.base.security.acl.policy;

import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;

/**
 * Default implementation that delegates descriptor synchronization to the seeder.
 */
@RequiredArgsConstructor
public class AclPolicySynchronizationServiceImpl implements AclPolicySynchronizationService {

    private final AclPolicySeeder seeder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void synchronize(AclPolicyDescriptor descriptor) {
        seeder.apply(descriptor);
        eventPublisher.publishEvent(new studio.one.platform.security.authz.DomainPolicyRefreshEvent());
    }
}
