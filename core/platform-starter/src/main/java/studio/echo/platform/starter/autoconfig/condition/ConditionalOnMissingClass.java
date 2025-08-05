package studio.echo.platform.starter.autoconfig.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
/**
 *
 * @author  donghyuck, son
 * @since 2025-07-11
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-11  donghyuck, son: 최초 생성.
 * </pre>
 */

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Conditional(ClassAbsenceCondition.class)
public @interface ConditionalOnMissingClass {
    String value();
}
