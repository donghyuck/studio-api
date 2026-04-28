package studio.one.platform.textract.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import studio.one.platform.autoconfigure.ConfigurationPropertyMigration;

class TextractFeatureCondition implements Condition {

    private static final Logger log = LoggerFactory.getLogger(TextractFeatureCondition.class);
    private static final String MIGRATION_REASON = "Textract feature wiring now belongs to "
            + "studio.features.textract.*.";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return ConfigurationPropertyMigration.getBooleanWithLegacyFallback(
                context.getEnvironment(),
                TextractProperties.FEATURE_PREFIX + ".enabled",
                TextractProperties.LEGACY_FEATURE_PREFIX + ".enabled",
                false,
                log,
                MIGRATION_REASON);
    }
}
