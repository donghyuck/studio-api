package studio.one.platform.security.acl.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.autoconfigure.I18nKeys;
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
@ConditionalOnProperty(prefix = PropertyKeys.Security.Acl.PREFIX + ".defaults", name = "enabled", havingValue = "true") 
@Slf4j
public class DefaultAclPolicyAutoConfiguration {

    private static final String FEATURE_NAME = "Security - Acl";

    @Bean
    @ConditionalOnMissingBean
    public AclPolicySeeder aclPolicySeeder(JdbcTemplate jdbcTemplate, ObjectProvider<I18n> i18nProvider) {

        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, I18nKeys.AutoConfig.Feature.Service.DETAILS, FEATURE_NAME,
                LogUtils.blue(AclPolicySeeder.class, true), LogUtils.red(State.CREATED.toString())));

        return new AclPolicySeeder(jdbcTemplate);
    }
}
