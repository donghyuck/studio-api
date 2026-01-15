package studio.one.base.security.acl.policy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.security.acls.model.AclCache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.security.authz.DomainPolicyRefreshEvent;

/**
 * Clears ACL cache whenever domain policies are refreshed.
 */
@RequiredArgsConstructor
@Slf4j
public class AclCacheInvalidationListener {

    private final ObjectProvider<AclCache> aclCacheProvider;

    @EventListener
    public void onDomainPolicyRefresh(DomainPolicyRefreshEvent event) {
        AclCache cache = aclCacheProvider.getIfAvailable();
        if (cache == null) {
            return;
        }
        cache.clearCache();
        log.debug("ACL cache cleared due to DomainPolicyRefreshEvent");
    }
}
