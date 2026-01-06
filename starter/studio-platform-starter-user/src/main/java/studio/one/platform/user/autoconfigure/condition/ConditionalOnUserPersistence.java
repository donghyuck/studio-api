package studio.one.platform.user.autoconfigure.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.features.condition.ConditionalOnFeaturePersistence;

/**
 * Activates configuration elements only when the User feature resolves to the specified
 * persistence type (jpa / jdbc).
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnFeaturePersistence(feature = "user")
public @interface ConditionalOnUserPersistence {
    @AliasFor(annotation = ConditionalOnFeaturePersistence.class, attribute = "value")
    PersistenceProperties.Type value();
}
