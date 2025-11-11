/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file DefaultAclPolicyInitializer.java
 *      @date 2025
 *
 */

package studio.one.platform.security.acl.autoconfigure;

import javax.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.policy.AclPolicySeeder;

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
