package studio.one.platform.ai.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import studio.one.platform.ai.autoconfigure.config.AiConfigurationMigration;
import studio.one.platform.constant.PropertyKeys;

class AiWebEndpointCondition implements Condition {

    private static final Logger log = LoggerFactory.getLogger(AiWebEndpointCondition.class);

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return AiConfigurationMigration.isAiFeatureEnabled(context.getEnvironment(), log)
                && context.getEnvironment()
                        .getProperty(PropertyKeys.AI.Endpoints.PREFIX + ".enabled", Boolean.class, true);
    }
}
