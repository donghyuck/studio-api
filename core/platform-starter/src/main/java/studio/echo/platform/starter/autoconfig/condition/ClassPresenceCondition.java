package studio.echo.platform.starter.autoconfig.condition;

import java.util.Map;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
 
  /**
   * {@link Condition} that checks for the presence of a class on the classpath.
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
  
  
public class ClassPresenceCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnClassPresence.class.getName());
        
        if (attributes == null || !attributes.containsKey("value")) {
            return false; // 잘못된 사용
        }

        String className = (String) attributes.get("value");
        ClassLoader classLoader = context.getClassLoader();
        if (classLoader == null) {
            return false; // classLoader 없으면 로드할 수 없으므로 false 처리
        }
        try {
            classLoader.loadClass(className);
            return true; // 클래스 존재
        } catch (ClassNotFoundException e) {
            return false; // 클래스 없음
        }
    }
    
}
