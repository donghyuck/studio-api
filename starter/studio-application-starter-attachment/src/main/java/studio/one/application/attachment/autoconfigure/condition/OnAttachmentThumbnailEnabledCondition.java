package studio.one.application.attachment.autoconfigure.condition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import studio.one.application.attachment.autoconfigure.AttachmentProperties;
import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;

class OnAttachmentThumbnailEnabledCondition extends SpringBootCondition {

    private static final Logger log = LoggerFactory.getLogger(OnAttachmentThumbnailEnabledCondition.class);

    private static final String TARGET_PREFIX = AttachmentProperties.PREFIX + ".thumbnail";
    private static final String LEGACY_PREFIX = AttachmentProperties.LEGACY_PREFIX + ".thumbnail";
    private static final String TARGET_ENABLED = TARGET_PREFIX + ".enabled";
    private static final String LEGACY_ENABLED = LEGACY_PREFIX + ".enabled";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean enabled = resolveEnabled(context.getEnvironment());
        if (enabled) {
            return ConditionOutcome.match("Attachment thumbnail is enabled");
        }
        return ConditionOutcome.noMatch("Attachment thumbnail is disabled");
    }

    private boolean resolveEnabled(Environment environment) {
        String targetValue = environment.getProperty(TARGET_ENABLED);
        if (StringUtils.hasText(targetValue)) {
            return Boolean.parseBoolean(targetValue);
        }
        if (ConfigurationPropertyMigration.hasProperties(environment, TARGET_PREFIX)) {
            return true;
        }
        String legacyValue = environment.getProperty(LEGACY_ENABLED);
        if (StringUtils.hasText(legacyValue)) {
            ConfigurationPropertyMigration.warnDeprecated(
                    log,
                    LEGACY_PREFIX + ".*",
                    TARGET_PREFIX + ".*",
                    AttachmentProperties.MIGRATION_REASON);
            return Boolean.parseBoolean(legacyValue);
        }
        return true;
    }
}
