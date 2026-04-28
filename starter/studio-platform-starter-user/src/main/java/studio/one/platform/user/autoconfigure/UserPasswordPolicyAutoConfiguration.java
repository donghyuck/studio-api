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
                    bindLegacyFallback(environment, properties);
                }
                return bean;
            }
        };
    }

    private static void bindLegacyFallback(Environment environment, PasswordPolicyProperties properties) {
        String target = PasswordPolicyProperties.PREFIX;
        String legacy = PasswordPolicyProperties.LEGACY_PREFIX;
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, target, legacy, "min-length",
                Integer.class, properties::setMinLength, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, target, legacy, "max-length",
                Integer.class, properties::setMaxLength, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, target, legacy, "require-upper",
                Boolean.class, properties::setRequireUpper, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, target, legacy, "require-lower",
                Boolean.class, properties::setRequireLower, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, target, legacy, "require-digit",
                Boolean.class, properties::setRequireDigit, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, target, legacy, "require-special",
                Boolean.class, properties::setRequireSpecial, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, target, legacy, "allowed-specials",
                String.class, properties::setAllowedSpecials, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, target, legacy, "allow-whitespace",
                Boolean.class, properties::setAllowWhitespace, log, MIGRATION_REASON);
    }
}
