package studio.one.platform.security.acl.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.policy.AclPolicySynchronizationService;
import studio.one.base.security.acl.service.AclPermissionService;
import studio.one.base.security.acl.web.controller.SecurityAclWebController;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.component.State;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

/**
 * Auto-configuration that exposes the ACL web controller when enabled.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityAclWebProperties.class)
@ConditionalOnProperty(prefix = "studio.security.acl.web", name = "enabled", havingValue = "true")
@Slf4j
public class SecurityAclWebAutoConfiguration {

    private static final String FEATURE_NAME = "Security - Acl";

    @Bean
    @ConditionalOnMissingBean
    public AclPolicySynchronizationService aclPolicySynchronizationService(AclPolicySeeder seeder, ObjectProvider<I18n> i18nProvider) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS,
                FEATURE_NAME,
                LogUtils.blue(AclPolicySynchronizationService.class, true),
                LogUtils.red(State.CREATED.toString())));

        return new AclPolicySynchronizationServiceImpl(seeder);
    }

    @Bean
    public SecurityAclWebController securityAclWebController(
            SecurityAclWebProperties properties,
            DefaultAclPolicyProperties defaults,
            ObjectProvider<AclPolicySynchronizationService> synchronizationService,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, FEATURE_NAME,
                LogUtils.blue(AclPolicySynchronizationService.class, true),
                LogUtils.blue(SecurityAclWebController.class, true),
                properties.getBasePath()),
                LogUtils.blue("ACL-managed"));
        return new SecurityAclWebController(defaults.getPolicies(), synchronizationService);
    }
}
