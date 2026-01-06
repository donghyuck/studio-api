package studio.one.platform.autoconfigure.features.condition;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import studio.one.platform.autoconfigure.PersistenceProperties.Type;
import studio.one.platform.constant.PropertyKeys;

class OnFeaturePersistenceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(
                ConditionalOnFeaturePersistence.class.getName(), true);
        if (attributes == null) {
            return ConditionOutcome.noMatch("No @ConditionalOnFeaturePersistence attributes found");
        }
        String feature = (String) attributes.get("feature");
        if (!StringUtils.hasText(feature)) {
            return ConditionOutcome.noMatch("Missing feature name for @ConditionalOnFeaturePersistence");
        }
        Type expected = (Type) attributes.get("value");
        Type actual = resolve(context.getEnvironment(), feature.trim());
        if (actual == expected) {
            return ConditionOutcome.match("Feature persistence matched " + feature + ": " + expected);
        }
        return ConditionOutcome.noMatch(
                "Feature persistence for " + feature + " was " + actual + ", expected " + expected);
    }

    private Type resolve(Environment env, String feature) {
        Type configured = parse(env.getProperty(featurePropertyKey(feature)));
        if (configured != null) {
            return configured;
        }
        Type global = parse(env.getProperty(PropertyKeys.Persistence.TYPE));
        return global != null ? global : Type.jpa;
    }

    private String featurePropertyKey(String feature) {
        return PropertyKeys.Features.PREFIX + "." + feature + ".persistence";
    }

    private Type parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Type.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
