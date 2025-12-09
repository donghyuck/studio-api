package studio.one.application.mail.autoconfigure.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

import studio.one.platform.autoconfigure.PersistenceProperties;

/**
 * 활성화된 mail persistence(jpa|jdbc)를 조건으로 빈 등록을 제어한다.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnMailPersistenceCondition.class)
public @interface ConditionalOnMailPersistence {

    PersistenceProperties.Type value();
}
