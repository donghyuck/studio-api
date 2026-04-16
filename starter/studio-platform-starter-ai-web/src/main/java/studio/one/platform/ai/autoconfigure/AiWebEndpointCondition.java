package studio.one.platform.ai.autoconfigure;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import studio.one.platform.constant.PropertyKeys;

class AiWebEndpointCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment().getProperty(PropertyKeys.AI.PREFIX + ".enabled", Boolean.class, false)
                && context.getEnvironment()
                        .getProperty(PropertyKeys.AI.Endpoints.PREFIX + ".enabled", Boolean.class, true);
    }
}
