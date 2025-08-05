package studio.api.platform.spring.autoconfigure.condition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(MultiplePropertiesCondition.class)
public @interface ConditionalOnProperties {

    String prefix() default "";

    Property[] value();

    @interface Property {
        
        String name();

        String havingValue() default "true";

        boolean matchIfMissing() default false;
    }
}
