package studio.one.platform.security.autoconfigure.condition;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.PersistenceProperties.Type;
import studio.one.platform.constant.PropertyKeys;

class OnAccountLockPersistenceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes =
                metadata.getAnnotationAttributes(ConditionalOnAccountLockPersistence.class.getName());
        Type expected = (Type) attributes.get("value");
        Type actual = resolve(context.getEnvironment());
        if (actual == expected) {
            return ConditionOutcome.match("AccountLock persistence matched " + expected);
        }
        return ConditionOutcome.noMatch("AccountLock persistence was " + actual + ", expected " + expected);
    }

    private Type resolve(Environment env) {
        Type configured = parse(env.getProperty(PropertyKeys.Security.Auth.LOCK + ".persistence"));
        if (configured != null) {
            return configured;
        }
        Type global = parse(env.getProperty(PropertyKeys.Persistence.PREFIX + ".type"));
        return global != null ? global : Type.jpa;
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
