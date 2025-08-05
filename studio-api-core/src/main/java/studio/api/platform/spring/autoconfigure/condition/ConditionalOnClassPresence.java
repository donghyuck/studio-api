package studio.api.platform.spring.autoconfigure.condition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

 /**
  * Conditional that only matches when the specified classes are on the classpath.
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
 
 

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(ClassPresenceCondition.class)
public @interface ConditionalOnClassPresence {
    String value();
}