package studio.one.platform.security.autoconfigure.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

import studio.one.platform.autoconfigure.PersistenceProperties;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnJwtPersistenceCondition.class)
public @interface ConditionalOnJwtPersistence {

    PersistenceProperties.Type value();
}
