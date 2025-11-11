package studio.one.platform.security.acl.autoconfigure;

import javax.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies the configured default ACL policies by invoking {@link AclPolicySeeder}
 * during startup.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "studio.security.acl.defaults", name = "enabled", havingValue = "true")
public class DefaultAclPolicyInitializer {

    private final DefaultAclPolicyProperties properties;
    private final AclPolicySeeder seeder;

    @PostConstruct
    void init() {
        if (properties.getPolicies() == null || properties.getPolicies().isEmpty()) {
            log.debug("no default ACL policies configured");
            return;
        }
        properties.getPolicies().forEach(seeder::apply);
        log.info("seeded {} default ACL policies", properties.getPolicies().size());
    }
}
