package studio.one.application.mail.autoconfigure;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import studio.one.application.mail.config.ImapProperties;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;
import studio.one.platform.constant.PropertyKeys;

@Configuration(proxyBeanMethods = false)
class MailImapPropertiesConfiguration {

    static final String TARGET_PREFIX = "studio.mail.imap";
    static final String LEGACY_PREFIX = PropertyKeys.Features.PREFIX + ".mail.imap";

    private static final Logger log = LoggerFactory.getLogger(MailImapPropertiesConfiguration.class);
    private static final String MIGRATION_REASON =
            "Mail IMAP settings moved out of feature wiring; keep feature toggles under studio.features.mail.";

    @Bean
    @ConditionalOnMissingBean(ImapProperties.class)
    ImapProperties mailImapProperties(Environment environment, ObjectProvider<Validator> validatorProvider) {
        ImapProperties properties = Binder.get(environment)
                .bind(TARGET_PREFIX, Bindable.of(ImapProperties.class))
                .orElseGet(ImapProperties::new);
        bindLegacyFallback(environment, properties);
        validate(properties, validatorProvider);
        return properties;
    }

    private static void bindLegacyFallback(Environment environment, ImapProperties properties) {
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX, "host",
                String.class, properties::setHost, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX, "port",
                Integer.class, properties::setPort, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX,
                "username", String.class, properties::setUsername, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX,
                "password", String.class, properties::setPassword, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX,
                "protocol", String.class, properties::setProtocol, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX, "ssl",
                Boolean.class, properties::setSsl, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX, "folder",
                String.class, properties::setFolder, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX,
                "max-messages", Integer.class, properties::setMaxMessages, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX,
                "concurrency", Integer.class, properties::setConcurrency, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX,
                "max-attachment-bytes", Long.class, properties::setMaxAttachmentBytes, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX,
                "max-body-bytes", Long.class, properties::setMaxBodyBytes, log, MIGRATION_REASON);
        ConfigurationPropertyMigration.bindLegacyLeafIfTargetMissing(environment, TARGET_PREFIX, LEGACY_PREFIX,
                "delete-after-fetch", Boolean.class, properties::setDeleteAfterFetch, log, MIGRATION_REASON);
    }

    private static void validate(ImapProperties properties, ObjectProvider<Validator> validatorProvider) {
        Validator validator = validatorProvider.getIfAvailable(
                () -> Validation.buildDefaultValidatorFactory().getValidator());
        Set<ConstraintViolation<ImapProperties>> violations = validator.validate(properties);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
