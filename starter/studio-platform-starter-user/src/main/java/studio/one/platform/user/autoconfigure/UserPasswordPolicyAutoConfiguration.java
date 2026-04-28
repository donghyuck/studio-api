package studio.one.platform.user.autoconfigure;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.config.PasswordPolicyProperties;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;

@AutoConfiguration
@EnableConfigurationProperties(PasswordPolicyProperties.class)
@Slf4j
public class UserPasswordPolicyAutoConfiguration {

    private static final String MIGRATION_REASON =
            "User password policy configuration moved from studio.features.user to studio.user.";

    @Bean
    static BeanPostProcessor passwordPolicyPropertiesMigration(Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof PasswordPolicyProperties properties) {
                    return ConfigurationPropertyMigration.bindLegacyFallbackIfTargetMissing(
                            environment,
                            PasswordPolicyProperties.PREFIX,
                            PasswordPolicyProperties.LEGACY_PREFIX,
                            properties,
                            log,
                            MIGRATION_REASON);
                }
                return bean;
            }
        };
    }
}
