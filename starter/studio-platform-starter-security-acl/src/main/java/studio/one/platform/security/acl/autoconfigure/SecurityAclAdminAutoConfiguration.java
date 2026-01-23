package studio.one.platform.security.acl.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.domain.event.listener.RoleAclSidSyncListener;
import studio.one.base.security.acl.persistence.AclClassRepository;
import studio.one.base.security.acl.persistence.AclEntryRepository;
import studio.one.base.security.acl.persistence.AclObjectIdentityRepository;
import studio.one.base.security.acl.persistence.AclSidRepository;
import studio.one.base.security.acl.policy.AclPolicyRefreshPublisher;
import studio.one.base.security.acl.service.AclAdministrationService;
import studio.one.base.security.acl.web.controller.AclActionController;
import studio.one.base.security.acl.web.controller.AclAdminController;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.autoconfigure.condition.ConditionalOnProperties;
import studio.one.platform.component.State;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.security.acl.AclMetricsRecorder;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityAclAdminProperties.class)
@ConditionalOnProperties( prefix = PropertyKeys.Security.Acl.PREFIX,
    value = {
    @ConditionalOnProperties.Property( name = "enabled", havingValue = "true"),
    @ConditionalOnProperties.Property( name = "web.enabled", havingValue = "true", matchIfMissing = false)    
})
@Slf4j
public class SecurityAclAdminAutoConfiguration {

        
    @Bean
    public AclAdministrationService aclAdministrationService(
            AclClassRepository classRepository,
            AclSidRepository sidRepository,
            AclObjectIdentityRepository identityRepository,
            AclEntryRepository entryRepository,
            AclPolicyRefreshPublisher refreshPublisher,
            ObjectProvider<AclMetricsRecorder> metricsRecorderProvider,
            ObjectProvider<SecurityAclProperties> propertiesProvider,
            ObjectProvider<I18n> i18nProvider) {
                I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, DefaultAclPolicyAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(AclAdministrationService.class, true), LogUtils.red(State.CREATED.toString())));
        return new AclAdministrationService(classRepository, sidRepository, identityRepository, entryRepository,
                refreshPublisher,
                metricsRecorderProvider.getIfAvailable(AclMetricsRecorder::noop),
                propertiesProvider.getIfAvailable(SecurityAclProperties::new).isAuditEnabled());
    }

    @Bean
    @ConditionalOnBean(AclAdministrationService.class)
    public AclAdminController aclAdminController(
            SecurityAclAdminProperties properties,
            AclAdministrationService administrationService,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, "Security - Acl",
                LogUtils.blue(AclAdministrationService.class, true),
                LogUtils.blue(AclAdminController.class, true),
                properties.getBasePath()));
        return new AclAdminController(administrationService);
    }

    @Bean
    public AclActionController aclActionController(
            SecurityAclAdminProperties properties,
            ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.EndPoint.REGISTERED, "Security - Acl",
                LogUtils.blue(AclAdministrationService.class, true),
                LogUtils.blue(AclActionController.class, true),
                properties.getBasePath()));
        return new AclActionController();
    }

    @Bean
    public RoleAclSidSyncListener roleAclSidSyncListener(AclSidRepository sidRepository, AclEntryRepository entryRepository, ObjectProvider<I18n> i18nProvider){
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, DefaultAclPolicyAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(RoleAclSidSyncListener.class, true), LogUtils.red(State.CREATED.toString())));
        return new RoleAclSidSyncListener(sidRepository, entryRepository);
    }
    

}
