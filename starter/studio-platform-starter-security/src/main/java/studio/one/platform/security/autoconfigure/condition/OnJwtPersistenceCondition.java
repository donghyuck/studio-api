package studio.one.platform.security.autoconfigure.condition;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import studio.one.platform.autoconfigure.PersistenceProperties.Type;
import studio.one.platform.constant.PropertyKeys;

class OnJwtPersistenceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata
                .getAnnotationAttributes(ConditionalOnJwtPersistence.class.getName());
        Type expected = (Type) attributes.get("value");
        Type actual = resolveType(context.getEnvironment());
        if (actual == expected) {
            return ConditionOutcome.match("JWT persistence type matched: " + expected);
        }
        return ConditionOutcome.noMatch("JWT persistence type was " + actual + ", expected " + expected);
    }

    private Type resolveType(Environment env) {
        String featureKey = PropertyKeys.Security.Jwt.PREFIX + ".persistence";
        Type configured = parse(env.getProperty(featureKey), featureKey);
        if (configured != null) {
            return normalize(configured);
        }
        Type global = parse(env.getProperty(PropertyKeys.Persistence.PREFIX + ".type"),
                PropertyKeys.Persistence.PREFIX + ".type");
        return global != null ? normalize(global) : Type.jpa;
    }

    private Type normalize(Type type) {
        return type == Type.mybatis ? Type.jdbc : type;
    }

    private Type parse(String raw, String propertyName) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Type.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported persistence type '" + raw + "' for "
                    + propertyName + ". Supported values are: jpa, mybatis, jdbc.", ex);
        }
    }
}
