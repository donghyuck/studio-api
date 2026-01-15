package studio.one.base.security.acl.policy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;
import studio.one.platform.security.authz.DomainPolicyRefreshEvent;
import studio.one.platform.service.DomainEvents;

/**
 * Publishes policy refresh events using available event infrastructure.
 */
@RequiredArgsConstructor
public class AclPolicyRefreshPublisher {

    private final ObjectProvider<DomainEvents> domainEventsProvider;
    private final ObjectProvider<ApplicationEventPublisher> applicationEventPublisher;

    public void publishAfterCommit() {
        DomainEvents domainEvents = domainEventsProvider.getIfAvailable();
        if (domainEvents != null) {
            domainEvents.publishAfterCommit(new DomainPolicyRefreshEvent());
            return;
        }
        ApplicationEventPublisher publisher = applicationEventPublisher.getIfAvailable();
        if (publisher != null) {
            publisher.publishEvent(new DomainPolicyRefreshEvent());
        }
    }
}
