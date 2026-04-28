package studio.one.application.mail.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    ImapProperties mailImapProperties(Environment environment) {
        ImapProperties properties = Binder.get(environment)
                .bind(TARGET_PREFIX, Bindable.of(ImapProperties.class))
                .orElseGet(ImapProperties::new);
        return ConfigurationPropertyMigration.bindLegacyFallbackIfTargetMissing(
                environment,
                TARGET_PREFIX,
                LEGACY_PREFIX,
                properties,
                log,
                MIGRATION_REASON);
    }
}
