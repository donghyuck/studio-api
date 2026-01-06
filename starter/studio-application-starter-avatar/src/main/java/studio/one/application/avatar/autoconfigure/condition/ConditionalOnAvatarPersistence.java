package studio.one.application.avatar.autoconfigure.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

import studio.one.platform.autoconfigure.PersistenceProperties;
import studio.one.platform.autoconfigure.features.condition.ConditionalOnFeaturePersistence;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnFeaturePersistence(feature = "avatar-image")
public @interface ConditionalOnAvatarPersistence {

    @AliasFor(annotation = ConditionalOnFeaturePersistence.class, attribute = "value")
    PersistenceProperties.Type value();
}
