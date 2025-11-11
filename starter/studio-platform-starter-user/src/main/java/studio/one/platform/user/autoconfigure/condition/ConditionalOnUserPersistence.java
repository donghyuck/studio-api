package studio.one.platform.user.autoconfigure.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

import studio.one.platform.autoconfigure.PersistenceProperties;

/**
 * Activates configuration elements only when the User feature resolves to the specified
 * persistence type (jpa / jdbc).
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnUserPersistenceCondition.class)
public @interface ConditionalOnUserPersistence {
    PersistenceProperties.Type value();
}
