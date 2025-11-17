package studio.one.platform.security.acl.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.acl.persistence.AclClassRepository;
import studio.one.base.security.acl.persistence.AclEntryRepository;
import studio.one.base.security.acl.persistence.AclObjectIdentityRepository;
import studio.one.base.security.acl.persistence.AclSidRepository;
import studio.one.base.security.acl.service.AclAdministrationService;
import studio.one.base.security.acl.web.controller.AclActionController;
import studio.one.base.security.acl.web.controller.AclAdminController;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityAclAdminProperties.class)
@ConditionalOnProperty(prefix = PropertyKeys.Security.Acl.PREFIX + ".web", name = "enabled", havingValue = "true")
@Slf4j
public class SecurityAclAdminAutoConfiguration {

    @Bean
    public AclAdministrationService aclAdministrationService(
            AclClassRepository classRepository,
            AclSidRepository sidRepository,
            AclObjectIdentityRepository identityRepository,
            AclEntryRepository entryRepository) {
        return new AclAdministrationService(classRepository, sidRepository, identityRepository, entryRepository);
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
}
