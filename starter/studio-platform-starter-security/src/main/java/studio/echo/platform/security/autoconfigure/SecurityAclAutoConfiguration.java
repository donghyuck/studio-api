package studio.echo.platform.security.autoconfigure;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.constant.ServiceNames;
import studio.echo.platform.security.authz.AclProperties;
import studio.echo.platform.security.authz.DomainPolicyContributor;
import studio.echo.platform.security.authz.DomainPolicyRegistry;
import studio.echo.platform.security.authz.DomainPolicyRegistryImpl;
import studio.echo.platform.security.authz.EndpointAuthorizationImpl;
import studio.echo.platform.security.authz.EndpointModeGuard;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(org.springframework.security.core.context.SecurityContextHolder.class)
@ConditionalOnProperty(prefix = PropertyKeys.Security.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WebProperties.class)
public class SecurityAclAutoConfiguration {

    @Bean(name = ServiceNames.DOMAIN_POLICY_REGISTRY)
    @ConditionalOnMissingBean
    public DomainPolicyRegistry domainPolicyRegistry(
            ObjectProvider<List<DomainPolicyContributor>> contributors) {
        return new DomainPolicyRegistryImpl(new AclProperties(), contributors);
    }

    /** SpEL: @endpointAuthz.can('domain','component','action') */
    @Bean(name = ServiceNames.DOMAIN_ENDPOINT_AUTHZ)
    @ConditionalOnMissingBean(name = ServiceNames.DOMAIN_ENDPOINT_AUTHZ)
    public EndpointAuthorizationImpl endpointAuthorization(DomainPolicyRegistry registry,
            EndpointModeGuard endpointModeGuard) {
        return new EndpointAuthorizationImpl(registry, endpointModeGuard);
    }
}
