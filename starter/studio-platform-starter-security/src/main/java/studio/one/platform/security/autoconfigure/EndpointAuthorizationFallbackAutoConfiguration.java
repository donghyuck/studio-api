package studio.one.platform.security.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.I18nKeys;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.security.authz.DenyAllEndpointAuthorization;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

/**
 * Registers the fail-closed endpoint authorization fallback after optional ACL
 * auto-configuration has had an opportunity to provide the real implementation.
 */
@AutoConfiguration(afterName =
        "studio.one.platform.security.acl.autoconfigure.SecurityAclDatabaseAutoConfiguration")
@ConditionalOnProperty(
        prefix = PropertyKeys.Security.PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@Slf4j
public class EndpointAuthorizationFallbackAutoConfiguration {

    @Bean(name = ServiceNames.DOMAIN_ENDPOINT_AUTHZ)
    @ConditionalOnMissingBean(name = ServiceNames.DOMAIN_ENDPOINT_AUTHZ)
    DenyAllEndpointAuthorization endpointAuthorizationFallback(ObjectProvider<I18n> i18nProvider) {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.warn(LogUtils.format(
                i18n,
                I18nKeys.AutoConfig.Feature.Service.DETAILS,
                SecurityAutoConfiguration.FEATURE_NAME,
                LogUtils.blue(DenyAllEndpointAuthorization.class, true),
                LogUtils.red("FALLBACK_DENY_ALL")));
        return new DenyAllEndpointAuthorization();
    }
}
