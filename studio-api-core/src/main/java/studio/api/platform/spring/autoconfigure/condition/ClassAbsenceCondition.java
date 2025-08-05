package studio.api.platform.spring.autoconfigure.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

 /**
  * 지정된 클래스가 존재하지 않는 경우에만 조건을 충족하는 Condition 구현체
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

public class ClassAbsenceCondition implements Condition {

    private final String className;

    public ClassAbsenceCondition(String className) {
        this.className = className;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (className == null) {
            return false;
        }
        if (context == null) {
            return false;
        }
        ClassLoader classLoader = context.getClassLoader();
        if (classLoader == null) {
            return false; // classLoader 없으면 로드할 수 없으므로 false 처리
        }
        try {
            classLoader.loadClass(className);
            return false; // 클래스가 존재함 → 조건 불충족
        } catch (ClassNotFoundException e) {
            return true; // 클래스가 존재하지 않음 → 조건 충족
        }
    }
}