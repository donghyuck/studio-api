package studio.one.application.template.autoconfigure.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.features.condition.ConditionalOnFeaturePersistence;

/**
 * template persistence(jpa|jdbc) 값에 따라 빈 등록을 제어한다.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnFeaturePersistence(feature = "template")
public @interface ConditionalOnTemplatePersistence {

    @AliasFor(annotation = ConditionalOnFeaturePersistence.class, attribute = "value")
    PersistenceProperties.Type value();
}
