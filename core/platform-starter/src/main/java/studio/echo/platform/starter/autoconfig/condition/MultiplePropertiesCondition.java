package studio.echo.platform.starter.autoconfig.condition;

import java.util.Map;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author  donghyuck, son
 * @since 2025-07-23
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-23  donghyuck, son: 최초 생성.
 * </pre>
 */


@Slf4j
public class MultiplePropertiesCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!metadata.isAnnotated(ConditionalOnProperties.class.getName())) {
            return true;
        }

        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnProperties.class.getName());
        if (attributes == null) {
            log.warn("@ConditionalOnProperties annotation not found or misconfigured.");
            return true;  
        }

        String prefix = (String) attributes.get("prefix");
        Object[] props = (Object[]) attributes.get("value");

        Environment env = context.getEnvironment();

        for (Object p : props) {
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) p;

            String name = (String) prop.get("name");
            String expected = (String) prop.get("havingValue");
            boolean matchIfMissing = (Boolean) prop.get("matchIfMissing");

            String fullKey = prefix.isEmpty() ? name : prefix + "." + name;
            String actual = env.getProperty(fullKey);

            if (actual == null && !matchIfMissing)
                return false;
            if (actual != null && !actual.equalsIgnoreCase(expected))
                return false;
        }

        return true;
    }

}
