package studio.one.platform.thumbnail.autoconfigure;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class ThumbnailFeatureCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment()
                .getProperty(ThumbnailProperties.FEATURE_PREFIX + ".enabled", Boolean.class, true);
    }
}
