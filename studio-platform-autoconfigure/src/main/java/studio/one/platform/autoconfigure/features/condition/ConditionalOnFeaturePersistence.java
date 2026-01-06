package studio.one.platform.autoconfigure.features.condition;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

import studio.one.platform.autoconfigure.PersistenceProperties;

/**
 * Activates configuration elements only when a feature resolves to
 * the specified persistence type (jpa / jdbc).
 */
@Target({ TYPE, METHOD })
@Retention(RUNTIME)
@Documented
@Conditional(OnFeaturePersistenceCondition.class)
public @interface ConditionalOnFeaturePersistence {

    /**
     * Feature name under "studio.features.<feature>.persistence".
     */
    String feature();

    /**
     * Expected persistence type.
     */
    PersistenceProperties.Type value();
}
