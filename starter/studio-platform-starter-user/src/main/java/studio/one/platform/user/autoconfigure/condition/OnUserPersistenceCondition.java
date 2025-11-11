package studio.one.platform.user.autoconfigure.condition;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import studio.one.platform.autoconfigure.PersistenceProperties.Type;
import studio.one.platform.constant.PropertyKeys;

class OnUserPersistenceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnUserPersistence.class.getName());
        Type expected = (Type) attributes.get("value");
        Type actual = resolveUserPersistence(context);
        if (actual == expected) {
            return ConditionOutcome.match("User feature persistence type matched: " + expected);
        }
        return ConditionOutcome.noMatch("User feature persistence type was " + actual + ", expected " + expected);
    }
    private Type resolveUserPersistence(ConditionContext context) {
        Environment env = context.getEnvironment();
        Type global = parseType(env.getProperty(PropertyKeys.Persistence.PREFIX + ".type"), Type.jpa);
        Type feature = parseType(env.getProperty(PropertyKeys.Features.User.PREFIX + ".persistence"), null);
        return feature != null ? feature : global;
    }
    private Type parseType(String raw, Type fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        try {
            return Type.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
