package studio.one.platform.security.acl.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import studio.one.base.security.acl.policy.AclPolicySyncEvent;
import studio.one.base.security.acl.policy.AclPolicySynchronizationService;
import studio.one.platform.constant.PropertyKeys;

/**
 * Listens for {@link AclPolicySyncEvent} and forwards them to the synchronization service.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = PropertyKeys.Security.Acl.PREFIX + ".sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AclPolicySyncEventListener {

    private final AclPolicySynchronizationService service;

    @EventListener
    public void onApplicationEvent(AclPolicySyncEvent event) {
        if (event == null || event.getDescriptor() == null)
            return;
        service.synchronize(event.getDescriptor());
    }
}
