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
 *      @file DefaultAclPolicyAutoConfiguration.java
 *      @date 2025
 *
 */

package studio.one.platform.security.acl.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.policy.AclPolicySeeder;
import studio.one.base.security.acl.policy.AclPolicySynchronizationService;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.condition.ConditionalOnProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

/**
 * Auto-configuration that wires together the seeding helpers and binds the
 * {@link DefaultAclPolicyProperties}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DefaultAclPolicyProperties.class)
@ConditionalOnClass(JdbcTemplate.class) 
@ConditionalOnProperties( prefix = PropertyKeys.Security.Acl.PREFIX,
    value = {
    @ConditionalOnProperties.Property( name = "enabled", havingValue = "true"),
    @ConditionalOnProperties.Property( name = "sync.enabled", havingValue = "true", matchIfMissing = false)    
})
@Slf4j
public class DefaultAclPolicyAutoConfiguration {

    protected static final String FEATURE_NAME = "Security - Acl";

    @Bean
    @ConditionalOnMissingBean
    public AclPolicySeeder aclPolicySeeder(JdbcTemplate jdbcTemplate, ObjectProvider<I18n> i18nProvider) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(AclPolicySeeder.class, true), LogUtils.red(State.CREATED.toString())));

        return new AclPolicySeeder(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public AclPolicySyncEventListener aclPolicySyncEventListener(
            AclPolicySynchronizationService aclPolicySynchronizationService,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(AclPolicySyncEventListener.class, true), LogUtils.red(State.CREATED.toString())));
        return new AclPolicySyncEventListener(aclPolicySynchronizationService);
    }
}
